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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.epsilon.emc.magicdraw.modelapi.GetFeatureValueRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElement;
import org.eclipse.epsilon.emc.magicdraw.modelapi.Value;
import org.eclipse.epsilon.emc.magicdraw.modelapi.Value.ValueCase;
import org.eclipse.epsilon.eol.exceptions.EolRuntimeException;
import org.eclipse.epsilon.eol.execute.context.IEolContext;
import org.eclipse.epsilon.eol.execute.introspection.java.JavaPropertyGetter;

public class MagicDrawPropertyGetter extends JavaPropertyGetter {

	private MagicDrawModel model;

	public MagicDrawPropertyGetter(MagicDrawModel model) {
		this.model = model;
	}

	@Override
	public Object invoke(Object object, String property, IEolContext context) throws EolRuntimeException {
		if (object instanceof MDModelElement) {
			MDModelElement mdElement = (MDModelElement) object;
			GetFeatureValueRequest request = GetFeatureValueRequest.newBuilder()
				.setElementID(mdElement.getElementID())
				.setFeatureName(property)
				.build();

			Value response = model.client.getFeatureValue(request);
			if (response.getValueCase() != ValueCase.NOTDEFINED) {
				return decodeValue(response);
			}
		}

		return super.invoke(object, property, context);
	}

	protected Object decodeValue(Value response) {
		switch (response.getValueCase()) {
		case BOOLEANVALUE: return response.getBooleanValue();
		case FLOATVALUE: return response.getFloatValue();
		case DOUBLEVALUE: return response.getDoubleValue();
		case LONGVALUE: return response.getLongValue();
		case INTEGERVALUE: return response.getIntegerValue();
		case SHORTVALUE: return (short) response.getShortValue();
		case BYTEVALUE: return (byte) response.getByteValue();
		case STRINGVALUE: return response.getStringValue();
		case REFERENCEVALUE: return new MDModelElement(model, response.getReferenceValue());
		case ENUMERATIONVALUE: return new MDEnumerationLiteral(response.getEnumerationValue());

		case BOOLEANVALUES:
		case DOUBLEVALUES:
		case FLOATVALUES:
		case LONGVALUES:
		case INTEGERVALUES:
		case SHORTVALUES:
		case BYTEVALUES:
		case STRINGVALUES:
		case ENUMERATIONVALUES:
			throw new IllegalArgumentException("Server should only send proxy lists for many-valued features");

		case REFERENCEVALUES: {
			// NOTE: should be used solely for .eContents and read-only lists - modifiable many-valued features should use proxy lists
			List<MDModelElement> elems = new ArrayList<>(response.getReferenceValues().getValuesCount());
			for (ModelElement e : response.getReferenceValues().getValuesList()) {
				elems.add(new MDModelElement(model, e));
			}
			return elems;
		}
			
		case PROXYLIST: return new MDProxyList(model, response.getProxyList());

		case NOTDEFINED:
			throw new IllegalArgumentException("Value is for an undefined feature");

		case VALUE_NOT_SET:
		default: return null;
		}
	}

}
