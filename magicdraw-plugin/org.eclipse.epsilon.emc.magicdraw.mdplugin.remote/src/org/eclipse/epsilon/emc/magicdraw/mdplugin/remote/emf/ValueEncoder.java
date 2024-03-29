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

import static org.eclipse.epsilon.emc.magicdraw.mdplugin.remote.emf.ModelUtils.getFullyQualifiedName;

import java.util.function.Predicate;

import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.epsilon.emc.magicdraw.modelapi.EnumerationValue;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElement;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElementCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.Value;

import com.nomagic.magicdraw.foundation.MDObject;

/**
 * Encodes objects and values from MagicDraw into the API types.
 */
public class ValueEncoder {

	/**
	 * Encodes a single value for a given feature. For many-valued features,
	 * it assumes that the {@code rawValue} is an element of the list.
	 */
	public void encode(final EObject mdObject, final EStructuralFeature eFeature, Value.Builder vBuilder, final Object rawValue) {
		if (eFeature instanceof EAttribute) {
			encodeScalarAttribute(vBuilder, rawValue);
		} else {
			encodeReference(vBuilder, (EObject) rawValue);
		}
	}

	public EnumerationValue encode(Enumerator literal) {
		return EnumerationValue.newBuilder()
			.setValue(literal.getValue())
			.setLiteral(literal.getLiteral())
			.setName(literal.getName())
			.build();
	}

	private void encodeScalarAttribute(Value.Builder vBuilder, final Object rawValue) {
		if (rawValue instanceof Number) {
			if (rawValue instanceof Float) {
				vBuilder.setFloatValue(((Number) rawValue).floatValue());
			} else if (rawValue instanceof Double) {
				vBuilder.setDoubleValue(((Number) rawValue).doubleValue());
			} else if (rawValue instanceof Long) {
				vBuilder.setLongValue(((Number) rawValue).longValue());
			} else if (rawValue instanceof Integer) {
				vBuilder.setIntegerValue(((Number) rawValue).intValue());
			} else if (rawValue instanceof Short) {
				vBuilder.setShortValue(((Number) rawValue).shortValue());
			} else {
				vBuilder.setByteValue(((Number) rawValue).byteValue());
			}
		} else if (rawValue instanceof String) {
			vBuilder.setStringValue((String) rawValue);
		} else if (rawValue instanceof Boolean) {
			vBuilder.setBooleanValue((boolean) rawValue);
		} else if (rawValue instanceof Enumerator) {
			vBuilder.setEnumerationValue(encode((Enumerator) rawValue));
		}
	}

	public void encodeReference(Value.Builder vBuilder, final EObject rawValue) {
		vBuilder.setReferenceValue(encode(rawValue));
	}

	public ModelElement encode(EObject eob) {
		return ModelElement.newBuilder().setElementID(encodeID(eob))
			.setMetamodelUri(eob.eClass().getEPackage().getNsURI())
			.setTypeName(getFullyQualifiedName(eob.eClass()))
			.build();
	}

	public String encodeID(EObject eob) {
		if (eob instanceof MDObject) {
			return ((MDObject) eob).getID();
		} else {
			/* For non-MDObject instances (e.g. the .eClass / .eContainingFeature), we
			 * take advantage of the fact that the resource URI matches the EPackage URI. */
			final Resource eResource = eob.eResource();
			return String.format("%s#%s", eob.eResource().getURI(), eResource.getURIFragment(eob));
		}
	}

	public ModelElementCollection encodeAllOf(EClassifier eClassifier, EObject root, final boolean onlyExactType) {
		// TODO Should we use a proxy list for Type.all as well?
		final ModelElementCollection.Builder builder = ModelElementCollection.newBuilder();
		final TreeIterator<EObject> it = EcoreUtil.getAllProperContents(root, true);

		Predicate<EObject> pred;
		if (eClassifier == null) {
			pred = (eob) -> eob instanceof MDObject;
		}
		else if (onlyExactType) {
			pred = (eob) -> eob instanceof MDObject && eob.eClass() == eClassifier;
		}
		else {
			pred = (eob) -> eob instanceof MDObject && eClassifier.isInstance(eob);
		}

		while (it.hasNext()) {
			EObject eob = it.next();
			if (pred.test(eob)) {
				builder.addValues(encode((MDObject) eob));
			}
		}

		return builder.build();
	}

}
