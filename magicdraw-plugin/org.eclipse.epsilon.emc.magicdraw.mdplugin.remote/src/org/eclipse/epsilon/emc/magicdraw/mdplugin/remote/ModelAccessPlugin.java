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

import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelServiceConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nomagic.magicdraw.plugins.Plugin;

/**
 * Entry point to the MagicDraw plugin to access MagicDraw/Cameo models from Epsilon.
 */
public class ModelAccessPlugin extends Plugin {

	/** Name of the system property that can be used to customise the host that we are listening on. */
	private static final String PROPERTY_HOST = "epsilon.emc.host";

	/** Name of the system property that can be used to customise the port that we are listening on. */
	private static final String PROPERTY_PORT = "epsilon.emc.port";

	private static final Logger LOGGER = LoggerFactory.getLogger(ModelAccessPlugin.class);

	private ModelAccessServer server;

	@Override
	public boolean close() {
		if (server != null) {
			server.stop();
		}
		return true;
	}

	@Override
	public void init() {
		try {
			final String host = getHostFromProperty();
			final int port = getPortFromProperty();

			server = new ModelAccessServer(host, port);
			server.start();
		} catch (IOException e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	private String getHostFromProperty() {
		final String host = System.getProperty(PROPERTY_HOST);
		if (host == null) {
			return ModelServiceConstants.DEFAULT_HOST;
		}
		return host;
	}

	private int getPortFromProperty() {
		final String sPort = System.getProperty(PROPERTY_PORT);
		if (sPort != null) {
			try {
				return Integer.parseInt(sPort);
			} catch (NumberFormatException ex) {
				LOGGER.error("Invalid port format: '%s' is not an integer", sPort);
			}
		}

		return ModelServiceConstants.DEFAULT_PORT;
	}

	@Override
	public boolean isSupported() {
		return true;
	}

}
