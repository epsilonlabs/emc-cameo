/*******************************************************************************
 * Copyright (c) 2022 University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Antonio Garcia-Dominguez - initial API and implementation
 *******************************************************************************/
package org.eclipse.epsilon.emc.magicdraw.remote;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.epsilon.common.util.StringProperties;
import org.eclipse.epsilon.emc.magicdraw.modelapi.AllOfRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.AllOfRequest.Builder;
import org.eclipse.epsilon.emc.magicdraw.modelapi.CreateInstanceRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.DeleteInstanceRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.Empty;
import org.eclipse.epsilon.emc.magicdraw.modelapi.EnumerationValue;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetElementByIDRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetEnumerationValueRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetTypeRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElement;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElementCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElementType;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelServiceConstants;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelServiceGrpc;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelServiceGrpc.ModelServiceBlockingStub;
import org.eclipse.epsilon.emc.magicdraw.modelapi.OpenSessionRequest;
import org.eclipse.epsilon.eol.exceptions.EolRuntimeException;
import org.eclipse.epsilon.eol.exceptions.models.EolEnumerationValueNotFoundException;
import org.eclipse.epsilon.eol.exceptions.models.EolModelElementTypeNotFoundException;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.eclipse.epsilon.eol.exceptions.models.EolNotInstantiableModelElementTypeException;
import org.eclipse.epsilon.eol.execute.introspection.IPropertySetter;
import org.eclipse.epsilon.eol.models.CachedModel;
import org.eclipse.epsilon.eol.models.IRelativePathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.rpc.ErrorInfo;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.protobuf.ProtoUtils;

public class MagicDrawModel extends CachedModel<MDModelElement> {

	private static final Logger LOGGER = LoggerFactory.getLogger(MagicDrawModel.class);
	
	private static final int HAS_TYPE_CACHE_SIZE = 100;
	private final LoadingCache<String, Optional<ModelElementType>> getTypeCache = CacheBuilder.newBuilder()
			.maximumSize(HAS_TYPE_CACHE_SIZE)
			.build(new GetTypeCacheLoader());

	private ManagedChannel channel;
	protected ModelServiceBlockingStub client;

	public static final String PROPERTY_HOST = "server.host";
	public static final String PROPERTY_PORT = "server.port";
	public static final String PROPERTY_ROOT_HYPERLINK = "root.hyperlink";

	private String host = ModelServiceConstants.DEFAULT_HOST;
	private int port = ModelServiceConstants.DEFAULT_PORT;
	private String rootElementHyperlink;

	protected final ValueEncoder encoder = new ValueEncoder();

	/**
	 * <p>Thread-safe way of ensuring we have a session opened when needed.</p>
	 *
	 * <p>See <a href=
	 * "https://docs.nomagic.com/display/MD2021x/Creating+new+model+elements">the
	 * MagicDraw documentation</a> on the need to manage editing sessions, to ensure
	 * transactional semantics and undo support.</p>
	 *
	 * <p>Note that these transactional semantics do not extend to diagrams,
	 * apparently: if you delete an element in an editing session and then roll it
	 * back, the model element will be restored but not its appearances on diagrams.
	 * This appears to be a limitation in MagicDraw/Cameo.</p>
	 */
	private class SessionState {
		private volatile boolean active;

		/**
		 * If a session has not been opened yet by this model, it will ask MagicDraw to open one.
		 * This may fail if MagicDraw already had an editing session opened from before.
		 */
		public synchronized void ensureOpened() {
			if (!active) {
				client.openSession(OpenSessionRequest.newBuilder().setDescription("Epsilon EMC driver").build());
				active = true;
			}
		}

		/**
		 * If there is a session open in MagicDraw, closes it, confirming any changes made.
		 */
		public synchronized void close() {
			if (active) {
				client.closeSession(Empty.newBuilder().build());
				active = false;
			}
		}

		/**
		 * If there is a session open in MagicDraw, cancels it, rolling back any changes made.
		 */
		public synchronized void cancel() {
			if (active) {
				client.cancelSession(Empty.newBuilder().build());
				active = false;
			}
		}
	}
	private final SessionState sessionState = new SessionState();

	public MagicDrawModel() {
		propertyGetter = new MagicDrawPropertyGetter(this);
		propertySetter = new MagicDrawPropertySetter(this);
	}

	@Override
	public MagicDrawPropertyGetter getPropertyGetter() {
		return (MagicDrawPropertyGetter) propertyGetter;
	}

	@Override
	public IPropertySetter getPropertySetter() {
		return (MagicDrawPropertySetter) propertySetter;
	}

	public String getRootElementHyperlink() {
		return rootElementHyperlink;
	}

	public void setRootElementHyperlink(String rootElementHyperlink) {
		if (rootElementHyperlink != null && !rootElementHyperlink.isBlank()) {
			this.rootElementHyperlink = rootElementHyperlink;
		} else {
			this.rootElementHyperlink = null;
		}
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	@Override
	public boolean store() {
		sessionState.close();
		return true;
	}

	@Override
	protected void loadModel() throws EolModelLoadingException {
		// Connect to MagicDraw
		channel = NettyChannelBuilder.forAddress(new InetSocketAddress(host, port)).usePlaintext().build();
		client = ModelServiceGrpc.newBlockingStub(channel);
		try {
			client.ping(Empty.newBuilder().build());
		} catch (StatusRuntimeException ex) {
			throw new EolModelLoadingException(ex, this);
		}

		// Invalidate all caches
		getTypeCache.invalidateAll();
	}

	@Override
	public void load(StringProperties properties, IRelativePathResolver resolver) throws EolModelLoadingException {
		super.load(properties, resolver);
 
		setHost(properties.getProperty(PROPERTY_HOST, ModelServiceConstants.DEFAULT_HOST));
		setPort(properties.getIntegerProperty(PROPERTY_PORT, ModelServiceConstants.DEFAULT_PORT));
		setRootElementHyperlink(properties.getProperty(PROPERTY_ROOT_HYPERLINK));

		load();
	}

	@Override
	public Object getEnumerationValue(String enumeration, String label) throws EolEnumerationValueNotFoundException {
		final GetEnumerationValueRequest request = GetEnumerationValueRequest.newBuilder()
			.setEnumeration(enumeration)
			.setLabel(label)
			.build();

		try {
			final EnumerationValue response = client.getEnumerationValue(request);
			return new MDEnumerationLiteral(response);
		} catch (StatusRuntimeException ex) {
			throw new EolEnumerationValueNotFoundException(enumeration, label, name);
		}
	}

	@Override
	public String getTypeNameOf(Object instance) {
		return ((MDModelElement) instance).getTypeName();
	}

	@Override
	public Object getElementById(String id) {
		final GetElementByIDRequest request = GetElementByIDRequest.newBuilder().setElementID(id).build();
		final ModelElement response = client.getElementByID(request);
		return new MDModelElement(this, response);
	}

	@Override
	public String getElementId(Object instance) {
		return ((MDModelElement) instance).getElementID();
	}

	@Override
	public void setElementId(Object instance, String newId) {
		throw new UnsupportedOperationException("Cannot change IDs for MagicDraw objects");
	}

	@Override
	public boolean owns(Object instance) {
		return instance instanceof MDModelElement && ((MDModelElement) instance).getModel() == this;
	}

	@Override
	public boolean isLoaded() {
		return client != null;
	}

	@Override
	public boolean isInstantiable(String type) {
		return getTypeCache.getUnchecked(type)
			.map(e -> !e.getIsAbstract())
			.orElse(false);
	}

	@Override
	public boolean hasType(String type) {
		return getTypeCache.getUnchecked(type).isPresent();
	}

	@Override
	public boolean store(String location) {
		throw new UnsupportedOperationException("Driver does not support storing in a different location");
	}

	@Override
	protected Collection<MDModelElement> allContentsFromModel() {
		Builder builder = AllOfRequest.newBuilder();
		if (rootElementHyperlink != null) {
			builder.setRootElementHyperlink(rootElementHyperlink);
		}
		final AllOfRequest request = builder.build();

		try {
			return getAllOfFromModel(request);
		} catch (EolModelElementTypeNotFoundException e) {
			LOGGER.error(e.getMessage(), e);
			return Collections.emptyList();
		}
	}

	@Override
	protected Collection<MDModelElement> getAllOfTypeFromModel(String type)
			throws EolModelElementTypeNotFoundException {
		return getAllOfFromModel(type, true);
	}

	@Override
	protected Collection<MDModelElement> getAllOfKindFromModel(String kind)
			throws EolModelElementTypeNotFoundException {
		return getAllOfFromModel(kind, false);
	}

	private Collection<MDModelElement> getAllOfFromModel(String type, boolean onlyExactType) throws EolModelElementTypeNotFoundException {
		AllOfRequest request = AllOfRequest.newBuilder()
			.setTypeName(type)
			.setRootElementHyperlink(rootElementHyperlink == null ? "" : rootElementHyperlink)
			.setOnlyExactType(onlyExactType)
			.build();

		return getAllOfFromModel(request);
	}

	private Collection<MDModelElement> getAllOfFromModel(AllOfRequest request) throws EolModelElementTypeNotFoundException {
		try {
			ModelElementCollection response = client.allOf(request);
			List<MDModelElement> elements = new ArrayList<>(response.getValuesCount());
			for (ModelElement e : response.getValuesList()) {
				elements.add(new MDModelElement(this, e));
			}

			return elements;
		} catch (StatusRuntimeException ex) {
			if (ex.getStatus().getCode() == Code.INVALID_ARGUMENT) {
				Metadata metadata = Status.trailersFromThrowable(ex);
				ErrorInfo errInfo = metadata.get(ProtoUtils.keyForProto(ErrorInfo.getDefaultInstance()));
				if (errInfo != null) {
					switch (errInfo.getReason()) {
					case ModelServiceConstants.REASON_CANNOT_FIND_TYPE:
						throw new EolModelElementTypeNotFoundException(getName(), request.getTypeName());
					}
				}
			}
			throw ex;
		}
	}

	@Override
	protected MDModelElement createInstanceInModel(String type)
			throws EolModelElementTypeNotFoundException, EolNotInstantiableModelElementTypeException {
		ensureSessionOpened();

		try {
			org.eclipse.epsilon.emc.magicdraw.modelapi.CreateInstanceRequest.Builder builder = CreateInstanceRequest.newBuilder().setTypeName(type);
			if (rootElementHyperlink != null) {
				builder.setRootElementHyperlink(rootElementHyperlink);
			}
			ModelElement response = client.createInstance(builder.build());

			return new MDModelElement(this, response);
		} catch (StatusRuntimeException ex) {
			if (ex.getStatus().getCode() == Code.INVALID_ARGUMENT) {
				Metadata metadata = Status.trailersFromThrowable(ex);
				ErrorInfo errInfo = metadata.get(ProtoUtils.keyForProto(ErrorInfo.getDefaultInstance()));
				if (errInfo != null) {
					switch (errInfo.getReason()) {
					case ModelServiceConstants.REASON_CANNOT_FIND_TYPE:
						throw new EolModelElementTypeNotFoundException(getName(), type);
					case ModelServiceConstants.REASON_CANNOT_INSTANTIATE_TYPE:
						throw new EolNotInstantiableModelElementTypeException(getName(), type);
					}
				}
			}
			throw ex;
		}
	}

	@Override
	protected void disposeModel() {
		sessionState.cancel();

		if (channel != null) {
			try {
				channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				LOGGER.warn(e.getMessage(), e);
				channel.shutdownNow();
			}

			channel = null;
			client = null;
		}
	}

	@Override
	protected boolean deleteElementInModel(Object instance) throws EolRuntimeException {
		if (!(instance instanceof MDModelElement)) {
			return false;
		}
		MDModelElement mdElem = (MDModelElement) instance;

		ensureSessionOpened();
		try {
			client.deleteInstance(DeleteInstanceRequest.newBuilder()
				.setElementID(mdElem.getElementID())
				.build());
			return true;
		} catch (Exception ex) {
			LOGGER.error(ex.getMessage(), ex);
			return false;
		}
	}

	@Override
	protected Object getCacheKeyForType(String type) throws EolModelElementTypeNotFoundException {
		// Tries to map the type reference to the fully qualified version of the name
		return getTypeCache.getUnchecked(type)
			.map(e -> e.getTypeName())
			.orElse(type);
	}

	@Override
	protected Collection<String> getAllTypeNamesOf(Object instance) {
		if (instance instanceof MDModelElement) {
			MDModelElement mdElem = (MDModelElement) instance;
			return getTypeCache.getUnchecked(mdElem.getTypeName())
				.map(e -> e.getAllSupertypesList().stream()
					.map(t -> t.getTypeName())
					.collect(Collectors.toList()))
				.orElse(Collections.emptyList());
		} else {
			return Collections.emptyList();
		}
	}

	protected void ensureSessionOpened() {
		sessionState.ensureOpened();
	}

	private class GetTypeCacheLoader extends CacheLoader<String, Optional<ModelElementType>> {
		@Override
		public Optional<ModelElementType> load(String type) {
			GetTypeRequest request = GetTypeRequest.newBuilder().setTypeName(type).build();
			try {
				ModelElementType result = client.getType(request);
				return Optional.of(result);
			} catch (StatusRuntimeException ex) {
				LOGGER.warn("Error while fetching type", ex);
				return Optional.empty();
			}
		}
	}
}
