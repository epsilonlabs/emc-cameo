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

import org.eclipse.epsilon.emc.magicdraw.MagicDrawModel;
import org.junit.Test;

/**
 * Tests for MagicDraw model loading and saving.
 */
public class MagicDrawModelTest {

	@Test
	public void zoo() throws Exception {
		try (MagicDrawModel m = new MagicDrawModel()) {
			m.setReadOnLoad(true);
			m.setStoredOnDisposal(false);
			m.load();

			assertEquals("4 classes should be visible from the sample Zoo model", 4, m.getAllOfKind("Class").size());
		}
	}

}
