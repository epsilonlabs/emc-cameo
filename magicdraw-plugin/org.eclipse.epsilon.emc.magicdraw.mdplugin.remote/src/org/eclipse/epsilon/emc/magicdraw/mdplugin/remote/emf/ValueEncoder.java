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

import java.util.Collection;
import java.util.Iterator;
import java.util.function.Predicate;

import org.eclipse.emf.common.util.Enumerator;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.epsilon.emc.magicdraw.modelapi.BooleanCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.DoubleCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.EnumerationValue;
import org.eclipse.epsilon.emc.magicdraw.modelapi.EnumerationValueCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.IntegerCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElement;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElementCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.StringCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.Value;

import com.nomagic.magicdraw.foundation.MDObject;

/**
 * Encodes objects and values into the API types.
 */
public class ValueEncoder {

	public void encode(final EStructuralFeature eFeature, Value.Builder vBuilder, final Object rawValue) {
		if (eFeature instanceof EAttribute) {
			encodeAttribute((EAttribute) eFeature, vBuilder, rawValue);
		} else {
			encodeReference((EReference) eFeature, vBuilder, rawValue);
		}
	}

	public EnumerationValue encode(Enumerator literal) {
		return EnumerationValue.newBuilder()
			.setValue(literal.getValue())
			.setLiteral(literal.getLiteral())
			.setName(literal.getName())
			.build();
	}

	private void encodeAttribute(final EAttribute eFeature, Value.Builder vBuilder, final Object rawValue) {
		if (eFeature.isMany()) {
			encodeManyScalarsAttribute(vBuilder, rawValue);
		} else {
			encodeScalarAttribute(vBuilder, rawValue);
		}
	}

	private void encodeScalarAttribute(Value.Builder vBuilder, final Object rawValue) {
		if (rawValue instanceof Byte || rawValue instanceof Short || rawValue instanceof Integer || rawValue instanceof Long) {
			vBuilder.setLongValue((byte) rawValue);
		} else if (rawValue instanceof Float || rawValue instanceof Double) {
			vBuilder.setDoubleValue((float) rawValue);
		} else if (rawValue instanceof String) {
			vBuilder.setStringValue((String) rawValue);
		} else if (rawValue instanceof Boolean) {
			vBuilder.setBooleanValue((boolean) rawValue);
		} else if (rawValue instanceof Enumerator) {
			vBuilder.setEnumerationValue(encode((Enumerator) rawValue));
		}
	}

	private void encodeManyScalarsAttribute(Value.Builder vBuilder, final Object rawValue) {
		final Iterator<?> it = ((Collection<?>) rawValue).iterator();
		final Object firstValue = it.next();
		if (rawValue instanceof Byte || rawValue instanceof Short || rawValue instanceof Integer
				|| rawValue instanceof Long) {
			IntegerCollection.Builder iBuilder = IntegerCollection.newBuilder();
			iBuilder.addValues((long) firstValue);
			while (it.hasNext())
				iBuilder.addValues((long) it.next());
			vBuilder.setLongValues(iBuilder.build());
		} else if (rawValue instanceof Float || rawValue instanceof Double) {
			DoubleCollection.Builder iBuilder = DoubleCollection.newBuilder();
			iBuilder.addValues((double) firstValue);
			while (it.hasNext())
				iBuilder.addValues((double) it.next());
			vBuilder.setDoubleValues(iBuilder.build());
		} else if (rawValue instanceof String) {
			StringCollection.Builder iBuilder = StringCollection.newBuilder();
			iBuilder.addValues((String) firstValue);
			while (it.hasNext())
				iBuilder.addValues((String) it.next());
			vBuilder.setStringValues(iBuilder.build());
		} else if (rawValue instanceof Boolean) {
			BooleanCollection.Builder iBuilder = BooleanCollection.newBuilder();
			iBuilder.addValues((boolean) firstValue);
			while (it.hasNext())
				iBuilder.addValues((boolean) it.next());
			vBuilder.setBooleanValues(iBuilder.build());
		} else if (rawValue instanceof Enumerator) {
			EnumerationValueCollection.Builder evBuilder = EnumerationValueCollection.newBuilder();
			evBuilder.addValues(encode((Enumerator) firstValue));
			while (it.hasNext())
				evBuilder.addValues(encode((Enumerator) it.next()));
			vBuilder.setEnumerationValues(evBuilder.build());
		}
	}

	@SuppressWarnings("unchecked")
	private void encodeReference(EReference eReference, Value.Builder vBuilder, final Object rawValue) {
		if (eReference.isMany()) {
			ModelElementCollection.Builder cBuilder = ModelElementCollection.newBuilder();
			for (Iterator<MDObject> it = ((Iterable<MDObject>) rawValue).iterator(); it.hasNext();) {
				cBuilder.addValues(encode(it.next()));
			}
			vBuilder.setReferenceValues(cBuilder.build());
		} else {
			vBuilder.setReferenceValue(encode((MDObject) rawValue));
		}
	}

	public ModelElement encode(MDObject eob) {
		return ModelElement.newBuilder().setElementID(eob.getID())
			.setMetamodelUri(eob.eClass().getEPackage().getNsURI())
			.setTypeName(getFullyQualifiedName(eob.eClass()))
			.build();
	}

	public ModelElementCollection encodeAllOf(EClassifier eClassifier, EObject root, final boolean onlyExactType) {
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
