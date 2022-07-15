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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;

import org.eclipse.epsilon.eol.EolModule;
import org.eclipse.epsilon.eol.exceptions.models.EolEnumerationValueNotFoundException;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for MagicDraw model loading and saving, with MagicDraw running on the
 * <code>resources/example-zoo.mdzip</code> project.
 */
public class ZooModelTest {
	private static final String CLASS_OBJECT_ID = "_2021x_2_71601c9_1657191783184_130536_1323";
	private static final int EXPECTED_CLASSES = 4;

	private MagicDrawModel m;

	@Test
	public void allOf() throws Exception {
		assertEquals("5 Class objects (including subtypes) should be visible from the sample Zoo model", 5, m.getAllOfKind("Class").size());
		assertEquals("4 Class objects (exact type) should be visible from the sample Zoo model", EXPECTED_CLASSES, m.getAllOfType("Class").size());

		assertEquals("1 ProtocolStateMachine object (including subtypes) should be visible from the sample Zoo model", 1, m.getAllOfKind("ProtocolStateMachine").size());
		assertEquals("1 ProtocolStateMachine object (exact type) should be visible from the sample Zoo model", 1, m.getAllOfType("ProtocolStateMachine").size());
	}

	@Test
	public void allContents() throws Exception {
		Collection<MDModelElement> contents = m.allContents();

		int nClasses = 0;
		for (MDModelElement e : contents) {
			if ("uml::Class".equals(e.getTypeName())) {
				++nClasses;
			}
		}

		assertEquals("There should be 4 exact classes among the contents of the model", EXPECTED_CLASSES, nClasses);
		assertTrue("There should be more than just the classes", contents.size() > EXPECTED_CLASSES);
	}

	@Test
	public void classNames() throws Exception {
		EolModule module = new EolModule();
		module.getContext().getModelRepository().addModel(m);
		module.parse("return Class.all.collect(c | c.name + ' is a ' + c.typeName + ' from ' + c.metamodelUri).sortBy(s|s).first;");
		String firstClass = (String) module.execute();
		assertEquals("Animal is a uml::Class from http://www.nomagic.com/magicdraw/UML/2.5.1.1", firstClass);
	}

	@Test
	public void hasType() throws Exception {
		assertTrue("Should have the Class type", m.hasType("Class"));
		assertTrue("Using package::type should work", m.hasType("uml::Class"));
		assertFalse("Should not have the missing type", m.hasType("DoesNotExist"));
	}

	@Test
	public void enumLiteralFound() throws Exception {
		MDEnumerationLiteral literal = (MDEnumerationLiteral) m.getEnumerationValue("uml::VisibilityKind", "private");

		assertNotNull(literal);
		assertEquals(1, literal.getValue());
		assertEquals("private", literal.getName());
		assertEquals("private", literal.getLiteral());
	}

	@Test
	public void enumLiteralFoundEOL() throws Exception {
		EolModule module = new EolModule();
		module.getContext().getModelRepository().addModel(m);
		module.parse("return uml::VisibilityKind#private;");
		MDEnumerationLiteral literal = (MDEnumerationLiteral) m.getEnumerationValue("uml::VisibilityKind", "private");
		assertNotNull(literal);
	}

	@Test(expected=EolEnumerationValueNotFoundException.class)
	public void enumLiteralNotFound() throws Exception {
		m.getEnumerationValue("something", "else");
	}

	@Test
	public void byID() {
		assertEquals("uml::Class", ((MDModelElement) m.getElementById(CLASS_OBJECT_ID)).getTypeName());
	}

	@Test
	public void isInstantiable() {
		assertTrue("uml::Class should be instantiable", m.isInstantiable("uml::Class"));
		assertFalse("ActivityNode is abstract, so it should not be instantiable", m.isInstantiable("ActivityNode"));
	}

	@Test
	public void cacheKeyForType() throws Exception {
		assertEquals("uml::Class", m.getCacheKeyForType("Class"));
	}

	@Test
	public void allTypeNamesOf() throws Exception {
		final Collection<String> allTypeNamesOf = m.getAllTypeNamesOf(m.getElementById(CLASS_OBJECT_ID));
		assertTrue(allTypeNamesOf.contains("foundation::MDObject"));
	}

	@Before
	public void createZooModel() throws EolModelLoadingException {
		m = new MagicDrawModel();
		m.setReadOnLoad(true);
		m.setStoredOnDisposal(false);
		m.setRootElementHyperlink("mdel://eee_1045467100313_135436_1");
		m.load();
	}

	@After
	public void disposeModel() {
		if (m != null) {
			m.close();
		}
	}
}
