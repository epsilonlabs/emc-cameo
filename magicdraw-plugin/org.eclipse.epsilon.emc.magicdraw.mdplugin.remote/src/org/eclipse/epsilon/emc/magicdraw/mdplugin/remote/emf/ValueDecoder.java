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

import java.util.stream.Collectors;

import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.epsilon.emc.magicdraw.modelapi.EnumerationValue;
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
		case ENUMERATIONVALUE: return decode(targetFeature, value.getEnumerationValue());
		case REFERENCEVALUE: return decode(project, value.getReferenceValue());
		case LONGVALUE: return value.getLongValue();
		case INTEGERVALUE: return value.getIntegerValue();
		case SHORTVALUE: return (short) value.getShortValue();
		case BYTEVALUE: return (byte) value.getByteValue();

		case STRINGVALUES:
			return value.getStringValues().getValuesList().stream().collect(Collectors.toList());
		case BOOLEANVALUES:
			return value.getBooleanValues().getValuesList().stream().collect(Collectors.toList());
		case FLOATVALUES:
			return value.getFloatValues().getValuesList().stream().collect(Collectors.toList());
		case DOUBLEVALUES:
			return value.getDoubleValues().getValuesList().stream().collect(Collectors.toList());
		case ENUMERATIONVALUES:
			return value.getEnumerationValues().getValuesList().stream()
				.map(e -> decode(targetFeature, e))
				.collect(Collectors.toList());
		case REFERENCEVALUES:
			return value.getReferenceValues().getValuesList().stream()
				.map(e -> decode(project, e))
				.collect(Collectors.toList());
		case LONGVALUES:
			return value.getLongValues().getValuesList().stream().collect(Collectors.toList());
		case INTEGERVALUES:
			return value.getIntegerValues().getValuesList().stream().collect(Collectors.toList());
		case SHORTVALUES:
			return value.getShortValues().getValuesList().stream().map(e -> e.shortValue()).collect(Collectors.toList());
		case BYTEVALUES:
			return value.getByteValues().getValuesList().stream().map(e -> e.byteValue()).collect(Collectors.toList());

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

	private Object decode(EStructuralFeature targetFeature, final EnumerationValue enumValue) {
		final EDataType eDataType = (EDataType) targetFeature.getEType();
		final String literal = enumValue.getLiteral();
		return EcoreUtil.createFromString(eDataType, literal);
	}

	private Object decode(Project project, final ModelElement elem) {
		return project.getElementByID(elem.getElementID());
	}

}
