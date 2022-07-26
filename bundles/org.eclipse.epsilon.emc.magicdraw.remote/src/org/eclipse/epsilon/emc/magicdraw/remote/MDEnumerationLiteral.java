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

import org.eclipse.epsilon.emc.magicdraw.modelapi.EnumerationValue;

/**
 * Represents an immutable EMF enumeration literal inside MagicDraw.
 */
public class MDEnumerationLiteral {

	private final int value;
	private final String literal;
	private final String name;

	public MDEnumerationLiteral(EnumerationValue response) {
		this.value = response.getValue();
		this.literal = response.getLiteral();
		this.name = response.getName();
	}

	public int getValue() {
		return value;
	}

	public String getLiteral() {
		return literal;
	}

	public String getName() {
		return name;
	}

	@Override
	public int hashCode() {
		return Objects.hash(literal, name, value);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MDEnumerationLiteral other = (MDEnumerationLiteral) obj;
		return Objects.equals(literal, other.literal) && Objects.equals(name, other.name) && value == other.value;
	}

	@Override
	public String toString() {
		return "MDEnumerationLiteral [value=" + value + ", literal=" + literal + ", name=" + name + "]";
	}

}
