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

import java.util.Collection;

import org.eclipse.epsilon.emc.magicdraw.modelapi.BooleanCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.DoubleCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.EnumerationValueCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.FloatCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.IntegerCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.LongCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElement;
import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElementCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.StringCollection;
import org.eclipse.epsilon.emc.magicdraw.modelapi.Value;

/**
 * Encodes values into the API {@link Value} type. This is
 * used for sending values from EOL back to MagicDraw.
 *
 * Unlike the {@code .modelapi} ObjectEncoder, this one only uses the value
 * and has no access to the original EReference / EAttribute.
 */
public class ValueEncoder {

	@SuppressWarnings("unchecked")
	public Value encode(Object value) {
		if (value == null) {
			return Value.newBuilder().setNotDefined(true).build();
		} else if (value instanceof String) {
			return Value.newBuilder().setStringValue((String) value).build();
		} else if (value instanceof Boolean) {
			return Value.newBuilder().setBooleanValue((Boolean) value).build();
		} else if (value instanceof Number) {
			return encode((Number)value);
		} else if (value instanceof MDEnumerationLiteral) {
			final MDEnumerationLiteral mdEnumLiteral = (MDEnumerationLiteral) value;
			return Value.newBuilder().setEnumerationValue(mdEnumLiteral.toEnumerationValue()).build();
		} else if (value instanceof MDModelElement) {
			final MDModelElement mdElem = (MDModelElement) value;
			return Value.newBuilder().setReferenceValue(encode(mdElem)).build();
		} else if (value instanceof MDProxyList) {
			final MDProxyList proxyList = (MDProxyList) value;
			return Value.newBuilder().setProxyList(proxyList.proxyList).build();
		} else if (value instanceof Collection) {
			return encode((Collection<Object>) value);
		}

		throw new UnsupportedOperationException(String.format("Cannot encode values of type %s", value.getClass().getName()));
	}

	private Value encode(Collection<Object> coll) {
		if (coll.isEmpty()) {
			return Value.newBuilder().setIntegerValues(IntegerCollection.getDefaultInstance()).build();
		}

		Object first = coll.iterator().next();
		if (first == null) {
			throw new IllegalArgumentException("Cannot encode collections with null values");
		}

		if (first instanceof String) {
			StringCollection.Builder cb = StringCollection.newBuilder();
			for (Object e : coll) {
				cb.addValues((String) e);
			}
			return Value.newBuilder().setStringValues(cb).build();
		} else if (first instanceof Boolean) {
			BooleanCollection.Builder cb = BooleanCollection.newBuilder();
			for (Object e : coll) {
				cb.addValues((boolean) e);
			}
			return Value.newBuilder().setBooleanValues(cb).build();
		} else if (first instanceof Number) {
			return encodeNumberList((Number) first, coll);
		} else if (first instanceof MDEnumerationLiteral) {
			EnumerationValueCollection.Builder cb = EnumerationValueCollection.newBuilder();
			for (Object e : coll) {
				cb.addValues(((MDEnumerationLiteral)e).toEnumerationValue());
			}
			return Value.newBuilder().setEnumerationValues(cb).build();
		} else if (first instanceof MDModelElement) {
			ModelElementCollection.Builder cb = ModelElementCollection.newBuilder();
			for (Object e : coll) {
				cb.addValues(encode((MDModelElement) e));
			}
			return Value.newBuilder().setReferenceValues(cb).build();
		} else {
			throw new UnsupportedOperationException(String.format("Cannot encode collection of type %s", first.getClass().getName()));
		}
	}

	private Value encodeNumberList(Number firstNumber, Collection<Object> coll) {
		if (firstNumber instanceof Float) {
			FloatCollection.Builder cb = FloatCollection.newBuilder();
			for (Object e : coll) {
				cb.addValues((float) e);
			}
			return Value.newBuilder().setFloatValues(cb).build();
		} else if (firstNumber instanceof Double) {
			DoubleCollection.Builder cb = DoubleCollection.newBuilder();
			for (Object e : coll) {
				cb.addValues((double) e);
			}
			return Value.newBuilder().setDoubleValues(cb).build();
		} else if (firstNumber instanceof Long) {
			LongCollection.Builder cb = LongCollection.newBuilder();
			for (Object e : coll) {
				cb.addValues((long) e);
			}
			return Value.newBuilder().setLongValues(cb).build();
		} else {
			IntegerCollection.Builder cb = IntegerCollection.newBuilder();
			for (Object e : coll) {
				cb.addValues((int) e);
			}
			if (firstNumber instanceof Integer) {
				return Value.newBuilder().setIntegerValues(cb).build();
			} else if (firstNumber instanceof Short) {
				return Value.newBuilder().setShortValues(cb).build();
			} else {
				return Value.newBuilder().setByteValues(cb).build();
			}
		}
	}

	private Value encode(final Number number) {
		if (number instanceof Float) {
			return Value.newBuilder().setFloatValue(number.floatValue()).build();
		} else if (number instanceof Double) {
			return Value.newBuilder().setDoubleValue(number.doubleValue()).build();
		} else if (number instanceof Long) {
			return Value.newBuilder().setLongValue(number.longValue()).build();
		} else if (number instanceof Integer) {
			return Value.newBuilder().setIntegerValue(number.intValue()).build();
		} else if (number instanceof Short) {
			return Value.newBuilder().setShortValue(number.shortValue()).build();
		} else {
			return Value.newBuilder().setByteValue(number.byteValue()).build();
		}
	}

	private ModelElement encode(final MDModelElement mdElem) {
		return ModelElement.newBuilder()
			.setElementID(mdElem.getElementID())
			.setMetamodelUri(mdElem.getMetamodelUri())
			.setTypeName(mdElem.getTypeName())
			.build();
	}
	
}
