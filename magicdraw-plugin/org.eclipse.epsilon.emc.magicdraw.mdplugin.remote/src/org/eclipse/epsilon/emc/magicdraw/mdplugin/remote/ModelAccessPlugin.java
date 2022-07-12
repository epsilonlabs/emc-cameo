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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nomagic.magicdraw.plugins.Plugin;

/**
 * Entry point to the MagicDraw plugin to access MagicDraw/Cameo models from Epsilon.
 */
public class ModelAccessPlugin extends Plugin {

	private static final Logger LOGGER = LoggerFactory.getLogger(ModelAccessPlugin.class);

	private ModelAccessServer server;

	@Override
	public boolean close() {
		if (server != null) {
			try {
				server.stop();
			} catch (InterruptedException e) {
				LOGGER.error(e.getMessage(), e);
			}
		}
		return true;
	}

	@Override
	public void init() {
		try {
			server = new ModelAccessServer();
			server.start();
		} catch (IOException e) {
			LOGGER.error(e.getMessage(), e);
		}
	}

	@Override
	public boolean isSupported() {
		return true;
	}

}
