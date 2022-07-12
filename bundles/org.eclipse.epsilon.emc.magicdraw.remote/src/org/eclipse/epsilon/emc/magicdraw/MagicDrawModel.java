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
package org.eclipse.epsilon.emc.magicdraw;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.epsilon.emc.magicdraw.modelapi.AllOfKindRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElement;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElementCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelServiceGrpc;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelServiceGrpc.ModelServiceBlockingStub;
import org.eclipse.epsilon.eol.exceptions.EolRuntimeException;
import org.eclipse.epsilon.eol.exceptions.models.EolEnumerationValueNotFoundException;
import org.eclipse.epsilon.eol.exceptions.models.EolModelElementTypeNotFoundException;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.eclipse.epsilon.eol.exceptions.models.EolNotInstantiableModelElementTypeException;
import org.eclipse.epsilon.eol.models.CachedModel;

import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;

public class MagicDrawModel extends CachedModel<MDModelElement> {

	private ManagedChannel channel;
	private ModelServiceBlockingStub client;

	public MagicDrawModel() {
		propertyGetter = new MagicDrawPropertyGetter(this);
	}

	protected ModelServiceBlockingStub getClient() {
		return client;
	}

	@Override
	public boolean store() {
		throw new UnsupportedOperationException("Storing not implemented yet");
	}

	@Override
	protected void loadModel() throws EolModelLoadingException {
		// Test out gRPC
		channel = NettyChannelBuilder.forAddress(new InetSocketAddress("localhost", 8123)).usePlaintext().build();
		client = ModelServiceGrpc.newBlockingStub(channel);
		System.out.println("Connected to MagicDraw");
	}

	@Override
	public Object getEnumerationValue(String enumeration, String label) throws EolEnumerationValueNotFoundException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getTypeNameOf(Object instance) {
		return ((MDModelElement) instance).getTypeName();
	}

	@Override
	public Object getElementById(String id) {
		// TODO Auto-generated method stub
		return null;
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
		return instance instanceof MDModelElement && ((MDModelElement)instance).getModel() == this;
	}

	@Override
	public boolean isInstantiable(String type) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean hasType(String type) {
		// TODO need API to ask server for registered models

		return true;
	}

	@Override
	public boolean store(String location) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	protected Collection<MDModelElement> allContentsFromModel() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected Collection<MDModelElement> getAllOfTypeFromModel(String type)
			throws EolModelElementTypeNotFoundException {
		// TODO Need to implement exact type finding in server
		return getAllOfKindFromModel(type);
	}

	@Override
	protected Collection<MDModelElement> getAllOfKindFromModel(String kind) throws EolModelElementTypeNotFoundException {
		AllOfKindRequest request;
		String[] parts = kind.split("::");
		if (parts.length > 1) {
			request = AllOfKindRequest.newBuilder()
				.setMetamodelUri(parts[0])
				.setTypeName(parts[1])
				.build();
		} else {
			request = AllOfKindRequest.newBuilder()
				.setTypeName(parts[0])
				.build();
		}
		
		ModelElementCollection response = client.allOfKind(request);
		List<MDModelElement> elements = new ArrayList<>(response.getValuesCount());
		for (ModelElement e : response.getValuesList()) {
			elements.add(new MDModelElement(this, e));
		}

		return elements;
	}

	@Override
	protected MDModelElement createInstanceInModel(String type) throws EolModelElementTypeNotFoundException, EolNotInstantiableModelElementTypeException {
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	protected Collection<String> getAllTypeNamesOf(Object instance) {
		// TODO Auto-generated method stub
		return null;
	}

}
