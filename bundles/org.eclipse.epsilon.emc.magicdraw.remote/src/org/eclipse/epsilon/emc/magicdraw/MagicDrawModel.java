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

import org.eclipse.emf.common.util.URI;
import org.eclipse.epsilon.emc.emf.AbstractEmfModel;
import org.eclipse.epsilon.emc.magicdraw.modelapi.HelloRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.HelloResponse;
import org.eclipse.epsilon.emc.magicdraw.modelapi.HelloServiceGrpc;
import org.eclipse.epsilon.emc.magicdraw.modelapi.HelloServiceGrpc.HelloServiceBlockingStub;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;

import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;

public class MagicDrawModel extends AbstractEmfModel {

	private URI modelUri;

	public URI getModelURI() {
		return modelUri;
	}

	public void setModelURI(URI modelUri) {
		this.modelUri = modelUri;
	}

	@Override
	public boolean store() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	protected void loadModel() throws EolModelLoadingException {
		// Test out gRPC
		ManagedChannel channel = NettyChannelBuilder.forAddress(new InetSocketAddress("localhost", 8123)).usePlaintext().build();
		HelloServiceBlockingStub blockingClient = HelloServiceGrpc.newBlockingStub(channel);

		HelloRequest request = HelloRequest.newBuilder().setFirstName("Antonio").setLastName("Garcia").build();
		HelloResponse response = blockingClient.hello(request);
		System.out.println("gRPC from MagicDraw said " + response.getGreeting());

		// TODO stubbed
	}

}
