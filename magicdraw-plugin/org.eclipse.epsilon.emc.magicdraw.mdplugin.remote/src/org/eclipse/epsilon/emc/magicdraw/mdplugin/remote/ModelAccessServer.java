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
package org.eclipse.epsilon.emc.magicdraw.mdplugin.remote;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.epsilon.emc.magicdraw.modelapi.HelloRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.HelloResponse;
import org.eclipse.epsilon.emc.magicdraw.modelapi.HelloServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nomagic.magicdraw.core.Application;
import com.nomagic.magicdraw.core.Project;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

/**
 * gRPC-based server to access models.
 */
public class ModelAccessServer {

	public static final int DEFAULT_PORT = 8123;
	private static final Logger LOGGER = LoggerFactory.getLogger(ModelAccessServer.class);

	private final int port;
	private final Server server;

	public ModelAccessServer() {
		this(DEFAULT_PORT);
	}

	public ModelAccessServer(int port) {
		this.port = port;
		this.server = ServerBuilder.forPort(port).addService(new ModelAccessService()).build();
	}

	public void start() throws IOException {
		server.start();

		LOGGER.info("Model access server started, listening on port " + port);
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				System.err.println("*** shutting down Epsilon model access server since JVM is shutting down");
				try {
					ModelAccessServer.this.stop();
				} catch (InterruptedException e) {
					e.printStackTrace(System.err);
				}
				System.err.println("*** Epsilon model access server shut down");
			}
		});
	}

	/** Stop serving requests and shutdown resources. */
	public void stop() throws InterruptedException {
		if (server != null) {
			server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
		}
	}

	/**
	 * Await termination on the main thread since the grpc library uses daemon
	 * threads.
	 */
	private void blockUntilShutdown() throws InterruptedException {
		if (server != null) {
			server.awaitTermination();
		}
	}

	/**
	 * Main entrypoint for testing the server.
	 */
	public static void main(String[] args) throws Exception {
		ModelAccessServer server = new ModelAccessServer();
		server.start();
		server.blockUntilShutdown();
	}

	private static class ModelAccessService extends HelloServiceGrpc.HelloServiceImplBase {
		@Override
		public void hello(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
			Application app = Application.getInstance();
			Project project = app.getProject();
			if (project == null) {
				LOGGER.error("Project is not open yet");
				responseObserver.onError(new StatusRuntimeException(Status.fromCode(Code.FAILED_PRECONDITION)));
			}

			int count = 0;
			Package pkg = project.getPrimaryModel();
			for (TreeIterator<Object> it = EcoreUtil.getAllContents(Collections.singletonList(pkg)); it.hasNext(); it.next()) {
				count++;
			}

			String greeting = String.format("Hello %s %s!!! Your primary model has %d objects.", request.getFirstName(), request.getLastName(), count);
			HelloResponse response = HelloResponse.newBuilder().setGreeting(greeting).build();
			responseObserver.onNext(response);
			responseObserver.onCompleted();
		}
	}
}
