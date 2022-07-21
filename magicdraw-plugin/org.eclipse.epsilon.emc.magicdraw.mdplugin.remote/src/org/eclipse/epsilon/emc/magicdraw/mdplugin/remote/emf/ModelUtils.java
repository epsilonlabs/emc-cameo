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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods to interact with MagicDraw models and metamodels.
 */
public class ModelUtils {

	private static final Logger LOGGER = LoggerFactory.getLogger(ModelUtils.class);

	public static String getFullyQualifiedName(EClassifier eClass) {
		List<String> parts = new ArrayList<>();
		parts.add(eClass.getName());
		for (EPackage pkg = eClass.getEPackage(); pkg != null; pkg = pkg.getESuperPackage()) {
			parts.add(pkg.getName());
		}

		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (ListIterator<String> it = parts.listIterator(parts.size()); it.hasPrevious(); ) {
			if (first) {
				first = false;
			} else {
				sb.append("::");
			}
			sb.append(it.previous());
		}

		return sb.toString();
	}


	public static Collection<EClassifier> findEClassifier(String typeName) {
		final List<String> parts = Arrays.asList(typeName.split("::"));
		if (parts.size() > 1) {
			final EClassifier eClassifier = findEClassifierAbsolute(parts, getRootEPackages());
			if (eClassifier == null) {
				LOGGER.warn(String.format("Cannot find type '%s'", typeName));
				return Collections.emptySet();
			}
			return Collections.singleton(eClassifier);
		} else {
			final Set<EClassifier> options = new HashSet<>();
			findEClassifierByName(parts.get(0), getRootEPackages(), options);
			return options;
		}
	}

	private static void findEClassifierByName(String name, Iterable<EPackage> scope, Collection<EClassifier> options) {
		for (EPackage pkg : scope) {
			EClassifier eClassifier = pkg.getEClassifier(name);
			if (eClassifier != null) {
				options.add(eClassifier);
			}
			findEClassifierByName(name, pkg.getESubpackages(), options);
		}
	}

	private static Iterable<EPackage> getRootEPackages() {
		return () -> EPackage.Registry.INSTANCE.values()
			.stream()
			.filter(pkg -> (pkg instanceof EPackage))
			.map(pkg -> (EPackage) pkg).iterator();
	}

	private static EClassifier findEClassifierAbsolute(List<String> parts, Iterable<EPackage> scope) {
		assert parts.size() > 1;

		for (EPackage pkg : scope) {
			if (parts.get(0).equals(pkg.getName())) {
				if (parts.size() == 2) {
					// Second part is just the classifier name
					EClassifier eClassifier = pkg.getEClassifier(parts.get(1));
					if (eClassifier != null) {
						return eClassifier;
					}
				} else {
					EClassifier eClassifier = findEClassifierAbsolute(
						parts.subList(1, parts.size()),
						pkg.getESubpackages()
					);
					if (eClassifier != null) {
						return eClassifier;
					}
				}
			}
		}

		return null;
	}

}
