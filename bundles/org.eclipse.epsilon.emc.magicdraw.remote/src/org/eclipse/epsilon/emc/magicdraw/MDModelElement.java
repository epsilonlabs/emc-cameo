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

import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElement;

public class MDModelElement {

	private final Object model;
	private final String typeName;
	private final String metamodelUri;
	private final String elementID;

	/**
	 * @param magicDrawModel
	 * @param e
	 */
	public MDModelElement(MagicDrawModel model, ModelElement e) {
		this.model = model;
		this.typeName = e.getTypeName();
		this.metamodelUri = e.getMetamodelUri();
		this.elementID = e.getElementID();
	}

	public Object getModel() {
		return model;
	}

	public String getTypeName() {
		return typeName;
	}

	public String getMetamodelUri() {
		return metamodelUri;
	}

	public String getElementID() {
		return elementID;
	}

}
