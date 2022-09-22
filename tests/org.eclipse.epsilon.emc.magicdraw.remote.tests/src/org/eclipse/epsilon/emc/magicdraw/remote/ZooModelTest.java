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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

import java.io.File;
import java.util.Collection;

import org.eclipse.epsilon.emc.magicdraw.modelapi.ModelElement;
import org.eclipse.epsilon.eol.EolModule;
import org.eclipse.epsilon.eol.exceptions.EolRuntimeException;
import org.eclipse.epsilon.eol.exceptions.models.EolEnumerationValueNotFoundException;
import org.eclipse.epsilon.eol.exceptions.models.EolModelElementTypeNotFoundException;
import org.eclipse.epsilon.eol.exceptions.models.EolModelLoadingException;
import org.eclipse.epsilon.eol.exceptions.models.EolNotInstantiableModelElementTypeException;
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
		assertEquals("5 Class objects (including subtypes) should be visible from the sample Zoo model", 5, classCount());
		assertEquals("4 Class objects (exact type) should be visible from the sample Zoo model", EXPECTED_CLASSES, m.getAllOfType("Class").size());

		// We can omit the root element hyperlink (it'll be the primary model in that case)
		m.setRootElementHyperlink(null);
		assertEquals("1 ProtocolStateMachine object (including subtypes) should be visible from the sample Zoo model", 1, count("ProtocolStateMachine"));
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
		EolModule module = createEOLModule();
		module.parse("return Class.all.collect(c | c.name + ' is a ' + c.typeName + ' from ' + c.metamodelUri).sortBy(s|s).first;");
		String firstClass	 = (String) module.execute();

		assertThat("Message mentions metamodel", firstClass,
			startsWith("Animal is a uml::Class from http://www.nomagic.com/magicdraw/UML/"));
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
		EolModule module = createEOLModule();
		module.parse("return uml::VisibilityKind#private;");
		MDEnumerationLiteral literal = (MDEnumerationLiteral) m.getEnumerationValue("uml::VisibilityKind", "private");
		assertNotNull(literal);
	}

	@Test(expected=EolEnumerationValueNotFoundException.class)
	public void enumLiteralNotFound() throws Exception {
		m.getEnumerationValue("something", "else");
	}

	@Test
	public void enumLiteralGet() throws Exception {
		EolModule module = createEOLModule();
		module.parse("return Class.all.selectOne(c|c.name='Animal').visibility;");

		final Object rawResponse = module.execute();
		assertNotNull("Fetching the enum-based visibility of the Animal class should return a non-null value", rawResponse);
		assertEquals("The Animal class should be reported as public", "public", ((MDEnumerationLiteral) rawResponse).getName());
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

	@Test
	public void createClass() throws Exception {
		final int originalClassCount = classCount();
		assertNotNull("Creating an instance in the model should return a proxy object", m.createInstance("Class"));
		assertEquals("The number of classes should have increased by 1 after a createInstance call",
			originalClassCount + 1, classCount());
	}

	@Test
	public void createClassWithinChildPackage() throws Exception {
		// Originally, this package should be empty
		EolModule module = createEOLModule();
		module.parse("return Package.all.selectOne(p|p.name='TestPackage').ownedElement.size();");
		assertEquals(0, module.execute());

		// If we treat it as the root of the model, then created instances should go directly here
		m.setRootElementHyperlink("mdel://_2021x_2_71601c9_1662468071986_336225_1290");
		m.createInstance("Class");

		// We go back to seeing the whole model, so we can find the TestPackage and check if the class got created there
		m.setRootElementHyperlink(null);
		module.parse("return Package.all.selectOne(p|p.name='TestPackage').ownedElement.size();");
		assertEquals(1, module.execute());
	}

	@Test(expected=EolModelElementTypeNotFoundException.class)
	public void createMissingType() throws Exception {
		m.createInstance("IDoNotExist");
	}

	@Test(expected=EolNotInstantiableModelElementTypeException.class)
	public void createAbstractType() throws Exception {
		m.createInstance("ActivityNode");
	}
	
	@Test
	public void deleteInstance() throws Exception {
		final int originalClassCount = classCount();
		Object element = m.getElementById(CLASS_OBJECT_ID);
		assertTrue("Deleting an existing object from the model should succeed", m.deleteElementInModel(element));
		assertEquals("Deleting an existing class from the model should reduce the total number of classes",
			originalClassCount - 1, classCount());
	}

	@Test
	public void deleteNonExistingInstance() throws Exception {
		final int originalClassCount = classCount();
		MDModelElement bogusElement = new MDModelElement(m, ModelElement.newBuilder()
			.setElementID("bogus")
			.build());

		assertFalse("Deleting a non-existing class from the model should fail", m.deleteElementInModel(bogusElement));
		assertEquals("Deleting a non-existing class from the model should not change the number of classes",
			originalClassCount, classCount());
	}

	@Test
	public void deleteNonElement() throws Exception {
		final int originalClassCount = classCount();
		assertFalse("Deleting a non-element from the model should fail", m.deleteElementInModel(1));
		assertEquals("Deleting a non-element from the model should not change the number of classes",
				originalClassCount, classCount());
	}

	@Test
	public void setStringAttribute() throws Exception {
		EolModule module = createEOLModule();
		module.parse("Class.all.selectOne(c|c.name = 'Animal').name = 'AnimalChanged';");
		module.execute();

		module.parse("return Class.all.selectOne(c|c.name = 'AnimalChanged');");
		MDModelElement e = (MDModelElement) module.execute();
		assertNotNull("The change to the name of the Animal class should have been performed", e);
	}

	@Test
	public void unsetStringAttribute() throws Exception {
		EolModule module = createEOLModule();
		module.parse("Class.all.selectOne(c|c.name = 'Animal').name = null;");
		module.execute();

		module.parse("return Class.all.select(c|not c.name.isDefined()).size();");
		assertEquals("There should be one class with an unset name", 1, module.execute());
	}

	@Test
	public void setBooleanAttribute() throws Exception {
		EolModule module = createEOLModule();

		// Given the Animal class is intially passive...
		module.parse("return Class.all.selectOne(c|c.name = 'Animal').isActive;");
		assertFalse("Initially, the Animal class should be passive", (Boolean) module.execute());

		// When we set it to be active...
		module.parse("Class.all.selectOne(c|c.name = 'Animal').isActive = true;");
		module.execute();

		// Then a get on the active property should return true
		module.parse("return Class.all.selectOne(c|c.name = 'Animal').isActive;");
		assertTrue("Making Animal into an active class should be noticed by the following get", (Boolean) module.execute());
	}

	@Test
	public void setEnumerationAttribute() throws Exception {
		EolModule module = createEOLModule();
		module.parse("return Class.all.select(c|c.visibility = uml::VisibilityKind#private).size();");
		assertEquals("At first, there should be no private classes", 0, module.execute());

		module.parse("Class.all.selectOne(c|c.name = 'Animal').visibility = uml::VisibilityKind#private;");
		module.execute();

		module.parse("return Class.all.select(c|c.visibility = uml::VisibilityKind#private).size();");
		assertEquals("After the assignment, there should be one private class", 1, module.execute());
	}

	@Test
	public void setSingleReference() throws Exception {
		EolModule module = createEOLModule();

		// Given the original owning package of Lion was the main Model one
		module.parse("return Class.all.selectOne(c|c.name = 'Lion').owningPackage.name;");
		assertEquals("At first, the name of the owning package for Lion should be Model", "Model", module.execute());

		// When we change the owning package to a new one
		module.parse("var p = new uml::Package; p.name = 'NewPackage'; Class.all.selectOne(c|c.name='Lion').owningPackage = p;");
		module.execute();

		// Then the change should be reflected back on the model
		module.parse("return Class.all.selectOne(c|c.name = 'Lion').owningPackage.name;");
		assertEquals("After the assignment, the name of the owning package for Lion should be NewPackage", "NewPackage", module.execute());
	}

	@Test
	public void setSingleInteger() throws Exception {
		EolModule module = createEOLModule();

		module.parse("return LiteralInteger.all.first.value;");
		assertEquals("At first, the default value of Animal::age should be 0", 0, module.execute());

		final int newValue = 23;
		module.parse(String.format("LiteralInteger.all.first.value = %d;", newValue));
		module.execute();

		module.parse("return LiteralInteger.all.first.value;");
		assertEquals("After the assignment, the default value of Animal::age should be " + newValue, newValue, module.execute());
	}

	@Test
	public void setSingleDouble() throws Exception {
		EolModule module = createEOLModule();

		// Given the original owning package of Lion was the main Model one
		module.parse("return LiteralReal.all.first.value;");
		assertEquals("At first, the default value of Animal::weight should be 0", 0.0d, module.execute());

		final double newValue = 47;
		module.parse(String.format("LiteralReal.all.first.value = %.1fd;", newValue));
		module.execute();

		module.parse("return LiteralReal.all.first.value;");
		assertEquals("After the assignment, the default value of Animal::weight should be " + newValue, newValue, module.execute());
	}

	@Test(expected=EolRuntimeException.class)
	public void setMissingFields() throws Exception {
		EolModule module = createEOLModule();
		module.parse("Class.all.selectOne(c|c.name = 'Animal').iDoNotExist = true;");
		module.execute();
	}

	@Test
	public void getListSize() throws Exception {
		EolModule module = createEOLModule();
		module.parse("return Class.all.selectOne(c|c.name = 'Lion').superClass.size();");
		assertEquals("Lion should have one superclass", 1, module.execute());
	}

	@Test
	public void getListFirst() throws Exception {
		EolModule module = createEOLModule();
		module.parse("return Class.all.selectOne(c|c.name = 'Lion').superClass.first.name;");
		assertEquals("Lion should have one superclass named Animal", "Animal", module.execute());
	}

	@Test(expected=EolRuntimeException.class)
	public void setListElementNotModifiable() throws Exception {
		EolModule module = createEOLModule();
		module.parse("Class.all.selectOne(c|c.name = 'Lion').superClass.set(0, Class.all.selectOne(c|c.name = 'Elephant'));");
		module.execute();
	}

	@Test
	public void setListElement() throws Exception {
		EolModule module = createEOLModule();
		module.parse("var lion = Class.all.selectOne(c|c.name='Lion'); "
				+ "var g = new Generalization; "
				+ "lion.generalization.set(0, g); "
				+ "g.source.add(lion); "
				+ "g.target.add(Class.all.selectOne(c|c.name = 'Elephant')); "
		);
		module.execute();

		module.parse("return Class.all.selectOne(c | c.name = 'Lion').superClass.first.name;");
		assertEquals("Elephant should be the new superclass of Lion", "Elephant", module.execute());

		module.parse("return Class.all.selectOne(c | c.name = 'Lion').superClass.size();");
		assertEquals("Elephant should only have one superclass", 1, module.execute());
	}

	@Test
	public void addListElement() throws Exception {
		EolModule module = createEOLModule();
		module.parse("var lion = Class.all.selectOne(c|c.name='Lion'); "
				+ "var p = new Property; "
				+ "p.name = 'maneColour'; "
				+ "lion.ownedAttribute.add(0, p); "
		);
		module.execute();

		module.parse("return Class.all.selectOne(c | c.name = 'Lion').ownedAttribute.first.name;");
		assertEquals("'maneColour' should be the new first property of Lion", "maneColour", module.execute());
	}

	@Test
	public void removeListElement() throws Exception {
		EolModule module = createEOLModule();
		module.parse("return Class.all.selectOne(c|c.name='Animal').ownedAttribute.size();");
		final int originalAttributeCount = (int) module.execute();
		module.parse("return Class.all.selectOne(c|c.name='Animal').ownedAttribute.first.name;");
		final String firstAttributeName = (String) module.execute();
		module.parse("return Class.all.selectOne(c|c.name='Animal').ownedAttribute.second.name;");
		final String secondAttributeName = (String) module.execute();

		module.parse("var removed = Class.all.selectOne(c|c.name='Animal').ownedAttribute.removeAt(0); return removed.name;");
		assertEquals("It should be possible to access the name of the removed attribute: it has been removed from the list but not deleted from the model",
			firstAttributeName, module.execute());

		module.parse("return Class.all.selectOne(c|c.name='Animal').ownedAttribute.size();");
		assertEquals("The number of attributes should have been reduced",
			originalAttributeCount - 1, module.execute());
		module.parse("return Class.all.selectOne(c|c.name='Animal').ownedAttribute.first.name;");
		assertEquals("The second attribute should now be the first one",
			secondAttributeName, module.execute());
	}

	@Test
	public void moveListObject() throws Exception {
		EolModule module = createEOLModule();
		module.parse("return Class.all.selectOne(c|c.name='Animal').ownedAttribute.first.name;");
		final String firstAttributeName = (String) module.execute();
		module.parse("return Class.all.selectOne(c|c.name='Animal').ownedAttribute.second.name;");
		final String secondAttributeName = (String) module.execute();

		module.parse("var animal = Class.all.selectOne(c|c.name='Animal'); animal.ownedAttribute.move(1, animal.ownedAttribute.first);");
		module.execute();

		module.parse("return Class.all.selectOne(c|c.name='Animal').ownedAttribute.first.name;");
		assertEquals("The previously second attribute should now be the first one",
			secondAttributeName, module.execute());
		module.parse("return Class.all.selectOne(c|c.name='Animal').ownedAttribute.second.name;");
		assertEquals("The previously first attribute should now be the second one",
			firstAttributeName, module.execute());
	}

	@Test
	public void clearList() throws Exception {
		EolModule module = createEOLModule();
		module.parse("var animal = Class.all.selectOne(c|c.name='Animal'); animal.ownedAttribute.clear();");
		module.execute();

		module.parse("return Class.all.selectOne(c|c.name='Animal').ownedAttribute.isEmpty();");
		assertTrue("Clearing the list of attributes should result in an empty list", (boolean) module.execute());
	}

	@Test
	public void assignProxyListToProxyList() throws Exception {
		EolModule module = createEOLModule();
		module.parse("var animal = Class.all.selectOne(c|c.name='Animal'); return animal.ownedAttribute.size();");
		final int originalAttributeCount = (int) module.execute();

		module.parse("var animal = Class.all.selectOne(c|c.name='Animal'); var lion = Class.all.selectOne(c|c.name='Lion'); lion.ownedAttribute = animal.ownedAttribute;");
		module.execute();

		module.parse("return Class.all.selectOne(c|c.name='Animal').ownedAttribute.isEmpty();");
		assertTrue("Setting the attributes of Lion to those of Animal should leave Animal with no attributes", (boolean) module.execute());
		module.parse("return Class.all.selectOne(c|c.name='Lion').ownedAttribute.size();");
		assertEquals("Setting the attributes of Animal to those of Lion should leave Lion with all the original attributes of Animal", originalAttributeCount, (int) module.execute());
	}

	@Test
	public void assignEmptyListToProxyList() throws Exception {
		EolModule module = createEOLModule();
		module.parse("var animal = Class.all.selectOne(c|c.name='Animal'); return animal.ownedAttribute.isEmpty();");
		assertFalse("Originally, the Animal class should have attributes", (boolean) module.execute());

		module.parse("var animal = Class.all.selectOne(c|c.name='Animal'); animal.ownedAttribute = Sequence {};");
		module.execute();

		module.parse("var animal = Class.all.selectOne(c|c.name='Animal'); return animal.ownedAttribute.isEmpty();");
		assertTrue("After the assignment, the Animal class should have no attributes", (boolean) module.execute());
	}

	@Test
	public void assignOneModelElementListToProxyList() throws Exception {
		EolModule module = createEOLModule();
		module.parse("var animal = Class.all.selectOne(c|c.name='Animal'); return animal.ownedAttribute.size();");
		final int originalAttributeCount = (int) module.execute();

		module.parse("var animal = Class.all.selectOne(c|c.name='Animal'); var lion = Class.all.selectOne(c|c.name='Lion'); lion.ownedAttribute = Sequence { animal.ownedAttribute.first };");
		module.execute();

		module.parse("return Class.all.selectOne(c|c.name='Animal').ownedAttribute.size();");
		assertEquals("Moving the first attributes of Animal to Lion should leave Animal with one attribute less", originalAttributeCount - 1, (int) module.execute());
		module.parse("return Class.all.selectOne(c|c.name='Lion').ownedAttribute.size();");
		assertEquals("Moving the first attributes of Animal to Lion should leave Lion with one attribute", 1, (int) module.execute());
	}

	@Test
	public void assignStringListToProxyList() throws Exception {
		EolModule module = createEOLModule();
		module.parse("var interaction = new Interaction; var oa = new OpaqueAction; interaction.action.add(oa); oa.body = Sequence { 'a', 'b' };");
		module.execute();

		module.parse("return OpaqueAction.all.first.body.size();");
		assertEquals("After the assignment, the new OpaqueAction should have two elements in .body", 2, module.execute());
	}

	@Test
	public void assignBooleanListToProxyList() throws Exception {
		// Note: Cameo Systems Modeler 19.0 does not have BooleanTaggedValue - available from 2021x
		assumeTypeExists("BooleanTaggedValue");

		EolModule module = createEOLModule();
		module.parse("var btv = new BooleanTaggedValue; btv.value = Sequence { true, false }; return btv.value.size();");
		assertEquals("After the assignment, the new BooleanTaggedValue should have two values", 2, module.execute());
	}

	@Test
	public void assignDoubleListToProxyList() throws Exception {
		// Note: Cameo Systems Modeler 19.0 does not have RealTaggedValue - available from 2021x
		assumeTypeExists("RealTaggedValue");
		
		EolModule module = createEOLModule();
		module.parse("var tv = new RealTaggedValue; tv.value = Sequence { 3.14d, 1.3d, 2.9d }; return tv.value.size();");
		assertEquals("After the assignment, the new RealTaggedValue should have three values", 3, module.execute());
	}

	@Test
	public void assignIntegerListToProxyList() throws Exception {
		// Note: Cameo Systems Modeler 19.0 does not have RealTaggedValue - available from 2021x
		assumeTypeExists("IntegerTaggedValue");

		EolModule module = createEOLModule();
		module.parse("var tv = new IntegerTaggedValue; tv.value = Sequence { 1, 2, 3, 27, 5 }; return tv.value.size();");
		assertEquals("After the assignment, the new IntegerTaggedValue should have five values", 5, module.execute());
	}

	@Test
	public void tryClosing() throws Exception {
		/*
		 * We don't want to open/close for every test (as it is slow and generates many popups in the demo),
		 * but we should at least try it once.
		 */
		m.setClosedOnDisposal(true);
	}

	private void assumeTypeExists(String typeName) {
		try {
			m.getAllOfKind(typeName);
		} catch (EolModelElementTypeNotFoundException ex) {
			assumeNoException(ex);
		}
	}
	
	private EolModule createEOLModule() {
		EolModule module = new EolModule();
		module.getContext().getModelRepository().addModel(m);
		return module;
	}

	private int classCount() throws EolModelElementTypeNotFoundException {
		return count("uml::Class");
	}

	private int count(String klass) throws EolModelElementTypeNotFoundException {
		return m.getAllOfKind(klass).size();
	}
	
	@Before
	public void createZooModel() throws EolModelLoadingException {
		m = new MagicDrawModel();

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
