/*******************************************************************************
 * Copyright (c) 2023 University of York.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Alfa Yohannis - Add a test for enumeration and enumeration literal creation
 *******************************************************************************/
package org.eclipse.epsilon.emc.magicdraw.remote;

import static org.junit.Assert.assertNotNull;

import java.io.File;

import org.eclipse.epsilon.eol.EolModule;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for Enumeration and EnumerationLiteral creation, with MagicDraw running on the
 * <code>resources/example-zoo.mdzip</code> project.
 */
public class EnumerationLiteralTest {

  private MagicDrawModel m;


  @Test
  public void createEnumerationLiteral() throws Exception {
    String script =
        "var enum = new Enumeration;\n"
        + "enum.name = \"MyEnum\";\n"
        + "var literal = new EnumerationLiteral;\n"
        + "literal.name = \"FIRST\";\n"
        + "enum.ownedLiteral.add(literal);\n"
        + "return literal.id;";
    EolModule module = createEOLModule();
    module.parse(script);
    String id = (String) module.execute();
    Object result = m.getElementById(id); 
    assertNotNull("EnumerationLiteral should have been created (not null)", result);
  }

  
  private EolModule createEOLModule() {
    EolModule module = new EolModule();
    module.getContext().getModelRepository().addModel(m);
    return module;
  }

  
  @Before
  public void createZooModel() throws EolModelLoadingException {
    m = new MagicDrawModel();
    m.setName("Model");

    m.setProjectURL(new File("resources/example-zoo.mdzip").getAbsoluteFile().toURI().toString());
    m.setReadOnLoad(true);
    m.setStoredOnDisposal(false);

    m.load();
  }

  @After
  public void disposeModel() {
    if (m != null) {
      m.close();
    }
  }
}
