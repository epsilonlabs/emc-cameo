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

import java.util.Objects;

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

	@Override
	public int hashCode() {
		return Objects.hash(elementID, metamodelUri, model, typeName);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MDModelElement other = (MDModelElement) obj;
		return Objects.equals(elementID, other.elementID) && Objects.equals(metamodelUri, other.metamodelUri)
				&& Objects.equals(model, other.model) && Objects.equals(typeName, other.typeName);
	}

	@Override
	public String toString() {
		return "MDModelElement [typeName=" + typeName + ", metamodelUri=" + metamodelUri + ", elementID=" + elementID + "]";
	}

	
}
