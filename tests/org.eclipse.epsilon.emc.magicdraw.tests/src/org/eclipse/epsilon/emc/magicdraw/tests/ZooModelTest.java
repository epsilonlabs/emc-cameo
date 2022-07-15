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
package org.eclipse.epsilon.emc.magicdraw.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.epsilon.emc.magicdraw.MagicDrawModel;
import org.eclipse.epsilon.eol.EolModule;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.junit.Test;

/**
 * Tests for MagicDraw model loading and saving, with MagicDraw running on the <code>resources/example-zoo.mdzip</code> project.
 */
public class ZooModelTest {

	@Test
	public void allOf() throws Exception {
		try (MagicDrawModel m = createZooModel()) {
			assertEquals("5 Class objects (including subtypes) should be visible from the sample Zoo model", 5, m.getAllOfKind("Class").size());
			assertEquals("4 Class objects (exact type) should be visible from the sample Zoo model", 4, m.getAllOfType("Class").size());

			assertEquals("1 ProtocolStateMachine object (including subtypes) should be visible from the sample Zoo model", 1, m.getAllOfKind("ProtocolStateMachine").size());
			assertEquals("1 ProtocolStateMachine object (exact type) should be visible from the sample Zoo model", 1, m.getAllOfType("ProtocolStateMachine").size());
		}
	}

	@Test
	public void classNames() throws Exception {
		try (MagicDrawModel m = createZooModel()) {
			EolModule module = new EolModule();
			module.getContext().getModelRepository().addModel(m);
			module.parse("return Class.all.collect(c | c.name + ' is a ' + c.metamodelUri + '::' + c.typeName).sortBy(s|s).first;");
			String firstClass = (String) module.execute();
			assertEquals("Animal is a http://www.nomagic.com/magicdraw/UML/2.5.1.1::Class", firstClass);
		}
	}

	@Test
	public void hasType() throws Exception {
		try (MagicDrawModel m = createZooModel()) {
			assertTrue("Should have the Class type", m.hasType("Class"));
			assertTrue("Using namespace::type should work", m.hasType("http://www.nomagic.com/magicdraw/UML/2.5.1.1::Class"));
			assertFalse("Should not have the missing type", m.hasType("DoesNotExist"));
		}
	}

	/**
	 * This test assumes you have opened the resources/example-zoo.mdzip file in MagicDraw.
	 */
	private MagicDrawModel createZooModel() throws EolModelLoadingException {
		MagicDrawModel m = new MagicDrawModel();
		m.setReadOnLoad(true);
		m.setStoredOnDisposal(false);
		m.setRootElementHyperlink("mdel://eee_1045467100313_135436_1");
		m.load();

		return m;
	}
}
