# Notes about MagicDraw for EMC driver development

## Prior posts

There was a blog post on using Xtend from a Cameo/MagicDraw plugin:

https://blogs.itemis.com/en/model-transformations-for-mbse-with-cameo-and-xtend

There is also documentation on MagicDraw's available API here:

## Internal modeling framework

https://www.magicdraw.com/files/manuals/MagicDraw%20OpenAPI%20UserGuide.pdf

## Internal modeling framework

It appears to be EMF and UML-based. They have their own implementation of UML, as far as I can tell (it's not the usual Eclipse UML metamodel).
They support an export to the Eclipse UML2 metamodels: every non-UML notation is a profile.
From EMF's point of view, their diagrams are just an extension element with a binary blob and some references to used objects/elements in them.
Here is an example:

```xml
<xmi:Extension extender='MagicDraw UML 2021x'>
  <modelExtension>
    <ownedDiagram xmi:type='uml:Diagram' xmi:id='_2021x_2_71601c9_1657191772600_850662_1272' name='Model' visibility='public' ownerOfDiagram='eee_1045467100313_135436_1'>
	    <xmi:Extension extender='MagicDraw UML 2021x'>
	      <diagramRepresentation>
	        <diagram:DiagramRepresentationObject ID='_2021x_2_71601c9_1657191772690_384006_1288' initialFrameSizeSet='true' requiredFeature=';UML_Standard_Profile.mdzip^UML_FEATURE~~' type='Class Diagram' umlType='Class Diagram' xmi:id='_9xjeMP3bEeyOYORq5j6epA' xmi:version='2.0' xmlns:binary='http://www.nomagic.com/ns/cameo/client/binary/1.0' xmlns:diagram='http://www.nomagic.com/ns/magicdraw/core/diagram/1.0' xmlns:xmi='http://www.omg.org/XMI' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>
            <diagramContents contentHash='23734607d7f9c2b60321c19f44fa65d02796d33c' exporterName='MagicDraw UML' exporterVersion='2021x' xmi:id='_9xjeMf3bEeyOYORq5j6epA'>
							<binaryObject streamContentID='BINARY-fb62fcc5-4dbf-49e9-9c6d-2d19f85e4648' xmi:id='_9xkFQP3bEeyOYORq5j6epA' xsi:type='binary:StreamIdentityBinaryObject'/>
							<usedObjects href='#_2021x_2_71601c9_1657191856569_741391_1411'/>
							<usedObjects href='#_2021x_2_71601c9_1657191825569_380834_1403'/>
							<usedElements>_2021x_2_71601c9_1657191818863_173481_1394</usedElements>
							<usedElements>_2021x_2_71601c9_1657191775958_126901_1301</usedElements>
						</diagramContents>
					</diagram:DiagramRepresentationObject>
				</diagramRepresentation>
			</xmi:Extension>

## Metamodels
</ownedDiagram>
  </modelExtension>
</xmi:Extension>
```

## Metamodels

They have a significant number of extensions on top of standard EMF/UML.
A bit of Bash scripting from the `lib` folder of a MagicDraw 2021x install shows quite a few generated EMF metamodels.
I tried running this command:

```shell
agd516@laptop:~/bin/MagicDraw Demo/lib$ for i in *.jar; do OUTPUT="$(jar tf "$i" | grep PackageImpl.class)"; if test $? == 0; then echo -e "\n# in $i"; echo $OUTPUT; fi ; done
```

I obtained this:

```text
# in cmof-1.4.jar
com/io_software/catools/tas/mof/CorbaidltypesPackageImpl.class com/io_software/catools/tas/mof/model/ModelPackageImpl.class com/io_software/catools/tas/mof/model/PackageImpl.class com/io_software/catools/tas/mof/primitivetypes/PrimitivetypesPackageImpl.class

# in com.nomagic.ci.admin.lock.test.metamodel-2021.2.0-SNAPSHOT.jar
com/nomagic/ci/admin/lock/test/metamodel/test/impl/TestPackageImpl.class

# in com.nomagic.ci.binary-2021.2.0-SNAPSHOT.jar
com/nomagic/ci/metamodel/binary/impl/BinaryPackageImpl.class

# in com.nomagic.ci.metamodel.project-2021.2.0-SNAPSHOT.jar
com/nomagic/ci/metamodel/project/impl/CommonprojectPackageImpl.class com/nomagic/ci/metamodel/local/project/impl/ProjectPackageImpl.class com/nomagic/ci/metamodel/local/snapshot/impl/SnapshotPackageImpl.class

# in com.nomagic.ci.metamodel.querytest-2021.2.0-SNAPSHOT.jar
com/nomagic/ci/metamodel/query2/impl/Query2PackageImpl.class com/nomagic/ci/metamodel/query/impl/QueryPackageImpl.class com/nomagic/ci/metamodel/query3/impl/Query3PackageImpl.class

# in com.nomagic.magicdraw.ce-2021.2.0-SNAPSHOT.jar
com/nomagic/magicdraw/ce/core/rt/options/impl/RTOptionsPackageImpl.class com/nomagic/magicdraw/ce/core/rt/objects/impl/RTObjectsPackageImpl.class com/nomagic/magicdraw/ce/core/rt/languageproperties/impl/LanguagePropertiesPackageImpl.class com/nomagic/magicdraw/ce/core/code/documentation/impl/RTDocumentationPackageImpl.class com/nomagic/magicdraw/ce/cppAnsi/rt/options/impl/CppRTOptionsPackageImpl.class com/nomagic/magicdraw/ce/cppAnsi/rt/objects/impl/CppRTObjectsPackageImpl.class com/nomagic/magicdraw/ce/corbaidl/rt/objects/impl/CorbaIdlRTObjectsPackageImpl.class com/nomagic/magicdraw/ce/corbaidl/rt/options/impl/CorbaIdlRTOptionsPackageImpl.class com/nomagic/magicdraw/ce/javabytecode/rt/objects/impl/JavaBytecodeRTObjectsPackageImpl.class com/nomagic/magicdraw/ce/java/rt/objects/impl/JavaRTObjectsPackageImpl.class com/nomagic/magicdraw/ce/java/code/documentation/impl/JavaRTDocumentationPackageImpl.class com/nomagic/magicdraw/dmn/ce/ddl/rt/options/impl/DdlRTOptionsPackageImpl.class com/nomagic/magicdraw/dmn/ce/ddl/rt/objects/impl/DdlRTObjectsPackageImpl.class com/nomagic/magicdraw/dmn/ce/xmlschema/rt/objects/impl/XmlSchemaRTObjectsPackageImpl.class

# in com.nomagic.magicdraw.core.diagram-2021.2.0-SNAPSHOT.jar
com/nomagic/magicdraw/core/diagram/impl/DiagramPackageImpl.class

# in com.nomagic.magicdraw.esi.binary.metamodel-2021.2.0-SNAPSHOT.jar
com/nomagic/magicdraw/esi/binary/metamodel/impl/BinaryPackageImpl.class

# in com.nomagic.magicdraw.esi.esiproject-2021.2.0-SNAPSHOT.jar
com/nomagic/magicdraw/esi/esiproject/impl/EsiprojectPackageImpl.class

# in com.nomagic.magicdraw.foundation-2021.2.0-SNAPSHOT.jar
com/nomagic/magicdraw/foundation/diagram/impl/DiagramPackageImpl.class com/nomagic/magicdraw/foundation/impl/MDFoundationPackageImpl.class

# in com.nomagic.magicdraw.security-2021.2.0-SNAPSHOT.jar
com/nomagic/magicdraw/security/impl/SecurityPackageImpl.class

# in com.nomagic.magicdraw.uml2-2021.2.0-SNAPSHOT.jar
com/nomagic/uml2/ext/magicdraw/classes/mdkernel/impl/PackageImpl.class com/nomagic/uml2/ext/magicdraw/impl/UMLPackageImpl.class

# in org.eclipse.emf.ecore-2.13.0.v20170609-0707.jar
org/eclipse/emf/ecore/impl/EPackageImpl.class org/eclipse/emf/ecore/impl/EcorePackageImpl.class org/eclipse/emf/ecore/xml/namespace/impl/XMLNamespacePackageImpl.class org/eclipse/emf/ecore/xml/type/impl/XMLTypePackageImpl.class

# in org.eclipse.osgi-3.12.50.jar
org/eclipse/osgi/internal/framework/legacy/PackageAdminImpl$ExportedPackageImpl.class
```

Unfortunately, it doesn't look like they have a nicely packaged way to read their models (i.e. as a nice library with an API).
If we wanted to have reliable read/write access to their models across MagicDraw versions, the best option may be to create a Cameo/MagicDraw plugin and have our EMC driver talk to it (similar to the PTC IM approach).

Plugins seem to be just subfolders inside the MagicDraw `plugins/` folder with a custom `plugin.xml` file and the appropriate set of JARs.
MagicDraw uses a single classloader, so we will want to be minimal about it.

## Exports

The Ecore export only leaves the things that can be directly mapped (e.g. straight up classes).
The Eclipse UML2 XMI exports appear to be very thorough, and they are also self-contained (MagicDraw exports the profiles, too): these do not include diagrams, though.
If we are only reading Cameo files, it may be enough to use the UML2 export: the EMC driver would only make sense for writing straight into Cameo, I think.
