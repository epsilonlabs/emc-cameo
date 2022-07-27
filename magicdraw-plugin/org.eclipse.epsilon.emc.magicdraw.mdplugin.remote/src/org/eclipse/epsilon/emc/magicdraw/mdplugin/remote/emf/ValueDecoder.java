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
package org.eclipse.epsilon.emc.magicdraw.mdplugin.remote.emf;

import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElement;
import org.eclipse.epsilon.emc.magicdraw.modelapi.Value;

import com.nomagic.magicdraw.core.Project;
import com.nomagic.magicdraw.foundation.MDObject;

/**
 *  Decodes {@link Value} objects coming from the API into raw values that can be used
 *  for an {@link EObject#eSet(org.eclipse.emf.ecore.EStructuralFeature, Object)}
 *  call.
 */
public class ValueDecoder {

	public Object decode(Project project, EStructuralFeature targetFeature, Value value) {
		// TODO need to add all possible options

		switch (value.getValueCase()) {
		case STRINGVALUE: return value.getStringValue();
		case BOOLEANVALUE: return value.getBooleanValue();
		case FLOATVALUE: return value.getFloatValue();
		case DOUBLEVALUE: return value.getDoubleValue();
		case ENUMERATIONVALUE: {
			final EDataType eDataType = (EDataType) targetFeature.getEType();
			final String literal = value.getEnumerationValue().getLiteral();
			return EcoreUtil.createFromString(eDataType, literal);
		}
		case REFERENCEVALUE: {
			final ModelElement elem = value.getReferenceValue();
			return project.getElementByID(elem.getElementID());
		}
		case LONGVALUE: return value.getLongValue();
		case INTEGERVALUE: return value.getIntegerValue();
		case SHORTVALUE: return (short) value.getShortValue();
		case BYTEVALUE: return (byte) value.getByteValue();

		// TODO lists (both literal and proxies)

		case PROXYLIST: {
			MDObject element = (MDObject) project.getElementByID(value.getProxyList().getElementID());
			EStructuralFeature eFeature = element.eClass().getEStructuralFeature(value.getProxyList().getFeatureName());
			return element.eGet(eFeature);
		}

		case VALUE_NOT_SET:
		case NOTDEFINED: return null;
		}

		throw new IllegalArgumentException(String.format("Unknown value case %s", value.getValueCase().name()));
	}

}
