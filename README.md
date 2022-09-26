# emc-cameo

![Maven CI workflow status](https://github.com/epsilonlabs/emc-cameo/actions/workflows/maven.yml/badge.svg)
![Tycho CI workflow status](https://github.com/epsilonlabs/emc-cameo/actions/workflows/tycho.yml/badge.svg)

This project contains a prototype [Eclipse Epsilon Model Connectivity](https://www.eclipse.org/epsilon/doc/emc/) (EMC) driver for [MagicDraw](https://magicdraw.com) / [Cameo Systems Modeler](https://www.3ds.com/products-services/catia/products/no-magic/cameo-enterprise-architecture/).

It uses [gRPC](https://grpc.io) to communicate with a running MagicDraw/Cameo instance.

For developer instructions, please check [`CONTRIBUTING.md`](CONTRIBUTING.md).

## Installation and usage instructions

Download `cameo-mdplugin-*.zip` from the [latest release](https://github.com/epsilonlabs/emc-cameo/releases) of the plugin.
Unzip it into your MagicDraw/Cameo `plugins` directory.

Launch MagicDraw/Cameo as usual: upon startup, the plugin will start a gRPC server which will make your models accessible to the driver.
The gRPC server will shut down automatically when you close MagicDraw/Cameo.

You can tell the driver to ensure a project is opened for you (using the `setProjectURL` method in `MagicDrawModel`), or you can open a project manually and let the driver access it.

The driver can be used in several ways, as listed below.
You may want to check the sample scripts in the [`examples`](examples) folder.

### Configuring the server port

The gRPC server will be listening on `localhost:8123` by default.
If you want to change this, edit your `magicdraw.properties` file, adding something like this to the end of your `JAVA_ARGS`:

```text
JAVA_ARGS=... -Depsilon.emc.host=yourhost -Depsilon.emc.port=9001
```

You will need to configure your `MagicDrawModel`s accordingly (via the Eclipse GUI, through the `PROPERTY_HOST` / `PROPERTY_PORT` properties, or through the `setHost` and `setPort` methods).

### Using the driver from Eclipse

You should first [install Epsilon](https://www.eclipse.org/epsilon/download/) following the official instructions.

You can then download the `org.eclipse.epsilon*.updatesite*.zip` file from the latest release, and install its plugins into your Eclipse IDE.

Once you do that, you will be able to add a new type of model to your Epsilon launch configurations, called "MagicDraw Instance".

### Using the driver from Ant inside Eclipse

The `run-eclipse` target in the [`examples/org.eclipse.epsilon.emc.magicdraw.examples.etl/build.xml`](examples/org.eclipse.epsilon.emc.magicdraw.examples.etl/build.xml) shows how to use the driver from an Ant workflow if you are running Ant from the same JRE as the workspace.

You should only need to use the [`epsilon.loadModel` Ant task](https://www.eclipse.org/epsilon/doc/workflow/#model-loading-tasks) with `type="MagicDrawRemote"`, while specifying any `<parameters>` as desired.
Please check the previous link in this paragraph for the supported properties.

### Using the driver from Ant outside Eclipse

The `run-standalone` target in the [`examples/org.eclipse.epsilon.emc.magicdraw.examples.etl/build.xml`](examples/org.eclipse.epsilon.emc.magicdraw.examples.etl/build.xml) also shows how to use the driver when the Ant workflow is not running from Eclipse's JRE.

It will require the driver, the Epsilon Ant tasks, and their dependencies to be in the classpath, and you will need to specifically mention the Java class of the driver in the `impl` attribute of the `epsilon.loadModel` task.
One easy way to populate the classpath (especially when running from inside corporate firewalls) is to download the `org.eclipse.epsilon.emc.*.antidst*-ant.zip` file in the latest release, unzip it, and then ask Ant to load all its `.jar` files into its classpath during execution:

```shell
ant -lib path/to/antdist/lib run-standalone
```

Another option is to use Maven / Gradle / Ivy to automatically download all the dependencies, as done in the [Epsilon standalone Ant example](https://git.eclipse.org/c/epsilon/org.eclipse.epsilon.git/tree/examples/org.eclipse.epsilon.examples.workflow.standalone).
An adaptation of this approach with Maven is available in the [`examples/org.eclipse.epsilon.emc.magicdraw.examples.etl/pom.xml`](examples/org.eclipse.epsilon.emc.magicdraw.examples.etl/pom.xml) file.

### Maven artifacts

The Maven artifacts for this repository are hosted in Github Packages, which requires [some configuration to be accessed](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry).

Once you have set up your authentication to Github Packages, you can access them by adding this `<repository>` element to Maven:

```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/epsilonlabs/emc-cameo</url>
  <snapshots>
    <enabled>true</enabled>
  </snapshots>
</repository>
```

Note that if you are using the repository from the Maven `antrun` plugin, it will need to be a `<pluginRepository>` element instead.

## Other resources

* [Blog post on using Xtend from a Cameo/MagicDraw plugin](https://blogs.itemis.com/en/model-transformations-for-mbse-with-cameo-and-xtend)
* [MagicDraw 2021x Developer Guide](https://docs.nomagic.com/display/MD2021x/Developer+Guide)
* [MagicDraw OpenAPI User Guide](https://www.magicdraw.com/files/manuals/MagicDraw%20OpenAPI%20UserGuide.pdf)
* [MagicDraw UML Metamodel](https://docs.nomagic.com/display/MD2021x/UML+2.5.1+Meta+Model)
