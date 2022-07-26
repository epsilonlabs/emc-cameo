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

import org.eclipse.epsilon.emc.magicdraw.modelapi.SetFeatureValueRequest;
import org.eclipse.epsilon.eol.exceptions.EolRuntimeException;
import org.eclipse.epsilon.eol.execute.context.IEolContext;
import org.eclipse.epsilon.eol.execute.introspection.java.JavaPropertySetter;

public class MagicDrawPropertySetter extends JavaPropertySetter {

	private final MagicDrawModel model;
	private final ValueEncoder encoder = new ValueEncoder();

	public MagicDrawPropertySetter(MagicDrawModel magicDrawModel) {
		this.model = magicDrawModel;
	}

	@Override
	public void invoke(Object target, String property, Object value, IEolContext context) throws EolRuntimeException {
		if (target instanceof MDModelElement) {
			model.ensureSessionOpened();
			
			MDModelElement mdElem = (MDModelElement) target;
			SetFeatureValueRequest request = SetFeatureValueRequest.newBuilder()
				.setElementID(mdElem.getElementID())
				.setFeatureName(property)
				.setNewValue(encoder.encode(value))
				.build();

			model.getClient().setFeatureValue(request);
		} else {
			super.invoke(target, property, value, context);
		}
	}

}
