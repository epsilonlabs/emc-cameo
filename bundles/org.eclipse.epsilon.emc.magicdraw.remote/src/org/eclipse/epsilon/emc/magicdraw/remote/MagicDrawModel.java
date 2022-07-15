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
import java.util.stream.Collectors;

import org.eclipse.epsilon.emc.magicdraw.modelapi.AllOfRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetElementByIDRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetEnumerationValueRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetEnumerationValueResponse;
import org.eclipse.epsilon.emc.magicdraw.modelapi.GetTypeRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElement;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElementCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElementType;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelServiceGrpc;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelServiceGrpc.ModelServiceBlockingStub;
import org.eclipse.epsilon.eol.exceptions.EolRuntimeException;
import org.eclipse.epsilon.eol.exceptions.models.EolEnumerationValueNotFoundException;
import org.eclipse.epsilon.eol.exceptions.models.EolModelElementTypeNotFoundException;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.eclipse.epsilon.eol.exceptions.models.EolNotInstantiableModelElementTypeException;
import org.eclipse.epsilon.eol.models.CachedModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.NettyChannelBuilder;

public class MagicDrawModel extends CachedModel<MDModelElement> {
	private static final Logger LOGGER = LoggerFactory.getLogger(MagicDrawModel.class);
	
	private static final int HAS_TYPE_CACHE_SIZE = 100;
	private final LoadingCache<String, Optional<ModelElementType>> getTypeCache = CacheBuilder.newBuilder()
			.maximumSize(HAS_TYPE_CACHE_SIZE)
			.build(new HasTypeCacheLoader());

	private ManagedChannel channel;
	private ModelServiceBlockingStub client;

	private String rootElementHyperlink;

	public MagicDrawModel() {
		propertyGetter = new MagicDrawPropertyGetter(this);
	}

	protected ModelServiceBlockingStub getClient() {
		return client;
	}

	public String getRootElementHyperlink() {
		return rootElementHyperlink;
	}

	public void setRootElementHyperlink(String rootElementHyperlink) {
		this.rootElementHyperlink = rootElementHyperlink;
	}

	@Override
	public boolean store() {
		throw new UnsupportedOperationException("Storing not implemented yet");
	}

	@Override
	protected void loadModel() throws EolModelLoadingException {
		// Connect to MagicDraw
		channel = NettyChannelBuilder.forAddress(new InetSocketAddress("localhost", 8123)).usePlaintext().build();
		client = ModelServiceGrpc.newBlockingStub(channel);

		// Invalidate all caches
		getTypeCache.invalidateAll();
	}

	@Override
	public Object getEnumerationValue(String enumeration, String label) throws EolEnumerationValueNotFoundException {
		final GetEnumerationValueRequest request = GetEnumerationValueRequest.newBuilder()
			.setEnumeration(enumeration)
			.setLabel(label)
			.build();

		try {
			final GetEnumerationValueResponse response = client.getEnumerationValue(request);
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
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	protected Collection<MDModelElement> allContentsFromModel() {
		final AllOfRequest request = AllOfRequest.newBuilder()
			.setRootElementHyperlink(rootElementHyperlink)
			.build();

		return getAllOfFromModel(request);
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

	private Collection<MDModelElement> getAllOfFromModel(String type, boolean onlyExactType) {
		AllOfRequest request = AllOfRequest.newBuilder()
			.setTypeName(type)
			.setRootElementHyperlink(rootElementHyperlink == null ? "" : rootElementHyperlink)
			.setOnlyExactType(onlyExactType)
			.build();

		return getAllOfFromModel(request);
	}

	private Collection<MDModelElement> getAllOfFromModel(AllOfRequest request) {
		ModelElementCollection response = client.allOf(request);
		List<MDModelElement> elements = new ArrayList<>(response.getValuesCount());
		for (ModelElement e : response.getValuesList()) {
			elements.add(new MDModelElement(this, e));
		}

		return elements;
	}

	@Override
	protected MDModelElement createInstanceInModel(String type)
			throws EolModelElementTypeNotFoundException, EolNotInstantiableModelElementTypeException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected void disposeModel() {
		// TODO Would need to complete editing session if store==true
		if (channel != null) {
			channel.shutdown();
			channel = null;
			client = null;
		}
	}

	@Override
	protected boolean deleteElementInModel(Object instance) throws EolRuntimeException {
		// TODO Auto-generated method stub
		return false;
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

	private class HasTypeCacheLoader extends CacheLoader<String, Optional<ModelElementType>> {
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
