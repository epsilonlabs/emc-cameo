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

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.epsilon.emc.magicdraw.modelapi.Value;

/**
 *  Decodes {@link Value} objects into raw values that can be used
 *  for an {@link EObject#eSet(org.eclipse.emf.ecore.EStructuralFeature, Object)}
 *  call.
 */
public class ValueDecoder {

	public Object decode(EStructuralFeature targetFeature, Value value) {
		// TODO
		switch (value.getValueCase()) {
		case STRINGVALUE: return value.getStringValue();
		case NOTDEFINED: return null;
		}

		throw new IllegalArgumentException(String.format("Unknown value case %s", value.getValueCase().name()));
	}

}
