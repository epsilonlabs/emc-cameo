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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.epsilon.emc.magicdraw.modelapi.GetFeatureValueRequest;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElement;
import org.eclipse.epsilon.emc.magicdraw.modelapi.Value;
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

			Value response = model.getClient().getFeatureValue(request);
			switch (response.getValueCase()) {
			case BOOLEANVALUE: return response.getBooleanValue();
			case BOOLEANVALUES: return response.getBooleanValues().getValuesList();
			case DOUBLEVALUE: return response.getDoubleValue();
			case DOUBLEVALUES: return response.getDoubleValues().getValuesList();
			case LONGVALUE: return response.getLongValue();
			case LONGVALUES: return response.getLongValues();
			case STRINGVALUE: return response.getStringValue();
			case STRINGVALUES: return response.getStringValues();
			case REFERENCEVALUE: return new MDModelElement(model, response.getReferenceValue());
			case REFERENCEVALUES:
				List<MDModelElement> elems = new ArrayList<>(response.getReferenceValues().getValuesCount());
				for (ModelElement e : response.getReferenceValues().getValuesList()) {
					elems.add(new MDModelElement(model, e));
				}
				return elems;
			case VALUE_NOT_SET: return null;
			}
		}

		return super.invoke(object, property, context);
	}

}
