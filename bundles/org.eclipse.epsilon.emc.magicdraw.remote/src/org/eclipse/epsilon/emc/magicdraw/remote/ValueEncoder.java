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

import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElement;
import org.eclipse.epsilon.emc.magicdraw.modelapi.Value;

/**
 * Encodes values into the API {@link Value} type.
 *
 * Unlike the {@code .modelapi} ObjectEncoder, this one only uses the value
 * and has no access to the original EReference / EAttribute.
 */
public class ValueEncoder {

	public Value encode(Object value) {
		if (value == null) {
			return Value.newBuilder().setNotDefined(true).build();
		} else if (value instanceof String) {
			return Value.newBuilder().setStringValue((String) value).build();
		} else if (value instanceof Boolean) {
			return Value.newBuilder().setBooleanValue((Boolean) value).build();
		} else if (value instanceof MDEnumerationLiteral) {
			final MDEnumerationLiteral mdEnumLiteral = (MDEnumerationLiteral) value;
			return Value.newBuilder().setEnumerationValue(mdEnumLiteral.toEnumerationValue()).build();
		} else if (value instanceof MDModelElement) {
			final MDModelElement mdElem = (MDModelElement) value;
			return Value.newBuilder().setReferenceValue(ModelElement.newBuilder()
				.setElementID(mdElem.getElementID())
				.setMetamodelUri(mdElem.getMetamodelUri())
				.setTypeName(mdElem.getTypeName())
				.build()).build();
		}

		throw new UnsupportedOperationException(String.format("Cannot encode values of type %s", value.getClass().getName()));
	}
	
}
