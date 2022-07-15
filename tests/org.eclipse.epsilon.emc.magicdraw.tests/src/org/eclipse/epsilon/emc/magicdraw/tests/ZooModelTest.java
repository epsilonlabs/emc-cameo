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
	public void zooClassCount() throws Exception {
		try (MagicDrawModel m = createZooModel()) {
			assertEquals("4 classes should be visible from the sample Zoo model", 4, m.getAllOfKind("Class").size());
		}
	}

	@Test
	public void zooClassNames() throws Exception {
		try (MagicDrawModel m = createZooModel()) {
			EolModule module = new EolModule();
			module.getContext().getModelRepository().addModel(m);
			module.parse("for (c in Class.all) { (c.name + ' is really a ' + c.metamodelUri + '::' + c.typeName).println(); }");
			module.execute();
		}
	}

	@Test
	public void zooHasTypes() throws Exception {
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
