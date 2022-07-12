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
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.grpc.Server;
import io.grpc.ServerBuilder;

/**
 * gRPC-based server to access models.
 */
public class ModelAccessServer {

	public static final int DEFAULT_PORT = 8123;
	static final Logger LOGGER = LoggerFactory.getLogger(ModelAccessServer.class);

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
}
