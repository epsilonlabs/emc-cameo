# emc-cameo

![Maven CI workflow status](https://github.com/epsilonlabs/emc-cameo/actions/workflows/maven.yml/badge.svg)

This project contains a prototype [Eclipse Epsilon Model Connectivity](https://www.eclipse.org/epsilon/doc/emc/) (EMC) driver for [MagicDraw](https://magicdraw.com) / [Cameo](https://www.3ds.com/products-services/catia/products/no-magic/cameo-enterprise-architecture/).

It uses [gRPC](https://grpc.io) to communicate with a running MagicDraw/Cameo instance.

## Installation instructions

To install the latest [release](https://github.com/epsilonlabs/emc-cameo/releases) of the plugin, follow these steps:

1. Ensure you have MagicDraw/Cameo, Eclipse and Epsilon installed.
1. Go to the latest release, and download both files:
   * The `cameo-mdplugin-*.zip` file will need to be downloaded and unzipped into your MagicDraw `plugins` directory.
   * The `org.eclipse.epsilon*.zip` file is a zipped Eclipse update site: install all its features through the "Help - Install New Software..." menu entry.
1. You will now be able to add a new type of model to your Epsilon launch configurations, called a "MagicDraw Instance".

## Overall repository structure

* `magicdraw-plugins`: source code for the MagicDraw plugin needed to gain access to MagicDraw/Cameo models.
  * `org.eclipse.epsilon.emc.magicdraw.mdplugin.remote`: the actual MagicDraw plugin, as a plain Java project. Implements a remote model access server.
  * `org.eclipse.epsilon.emc.magicdraw.modelapi`: the gRPC client/server stubs for accessing models, as a Maven project.
* `bundles`: Eclipse plugin project for the EMC driver.
* `releng`: Eclipse target platform, features, and update site.
* `tests`: Eclipse fragment projects to test the EMC driver, along with sample MagicDraw models.

The bundles/releng/tests part is meant to take advantage of [Tycho POM-less builds](https://wiki.eclipse.org/Tycho/pomless) in the future, which take that structure as a convention.

## Preparing a development environment

### Dependencies

You will need:

* A recent version of the [Eclipse Modeling Tools](https://www.eclipse.org/downloads/).
* A Java 11 or newer JDK.
* [M2Eclipse](https://www.eclipse.org/m2e/).
* A MagicDraw 2021x installation (MagicDraw has a [free demo](https://www.magicdraw.com/download/magicdraw), only limited in model size).

Currently, the build environment uses some Bash scripts.
If using Windows, you will need [WSL2](https://docs.microsoft.com/en-us/windows/wsl/install).

### Eclipse configuration

Import all projects into an Eclipse workspace, and then open the `.target` file in the `org.eclipse.epsilon.emc.magicdraw.targetplatform` project.
Let it resolve, and then set it as the target platform.

Next, you need to define the `MAGICDRAW_INSTALL` classpath variable, to point to your MagicDraw main folder.
Go to `Window - Preferences...`, select the `Java - Build Path - Classpath Variables` item on the left, and set up the new variable.

You will also need to rebuild the `magicdraw.modelapi` project using Maven.
To do so, right-click on the project in the `Project Explorer` view and select `Maven - Update Project...`.

### MagicDraw plugin generation

All compilation errors should be gone at this point.
The next step is to set up your MagicDraw plugin folder and update the model access API `.jar` used by the EMC driver, by running a shell script:

```shell
cd magicdraw-plugin/org.eclipse.epsilon.emc.magicdraw.modelapi
./create-magicdraw-plugin.sh path/to/magicdraw/plugins
```

You must rerun this script every time you change the `ModelService.proto` file in the `magicdraw.modelapi` plugin.
You must also rerun the script whenever you change the `mdplugin.remote` project, but you can avoid some of this if you are using hot code swapping via remote debugging (see below).

### Remote debugging and hot code swapping

Edit the `bin/magicdraw.properties` file in the MagicDraw installation, adding this at the end of the line starting with `JAVA_ARGS=` (add a space before the last existing argument):

```text
-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=8000
```

This will allow you to use the `Remote Debug MagicDraw.launch` file in the `mdplugin.remote` project while MagicDraw is running.
While you are in a remote debugging session, any changes you make to method bodies will be reflected in the running instance of MagicDraw.
If you make changes to the structure of the code (i.e. adding/removing/renaming methods and/or classes), however, hot code swapping will not work: you will need to close MagicDraw, rerun the `create-magicdraw-plugin.sh` script, and start MagicDraw again.

While developing, it is best to run MagicDraw in verbose mode from a console, to see the log messages:

```shell
./magicdraw -verbose
```

## Plain Maven build

You can also run a plain Maven build using the parallel hierarchy of `pom-plain.xml` files:

```shell
mvn -f pom-plain.xml clean install
```

This build is for redistributing the EMC driver via Maven / plain JARs (e.g. for use from the [Epsilon Ant tasks](https://www.eclipse.org/epsilon/doc/workflow/)), and for developers that are not using Eclipse.

Specifically, this plain Maven build does not run the `create-magicdraw-plugin.sh` script, which is needed when developing the EMC driver as an Eclipse plugin.

## Other resources

* [Blog post on using Xtend from a Cameo/MagicDraw plugin](https://blogs.itemis.com/en/model-transformations-for-mbse-with-cameo-and-xtend)
* [MagicDraw 2021x Developer Guide](https://docs.nomagic.com/display/MD2021x/Developer+Guide)
* [MagicDraw OpenAPI User Guide](https://www.magicdraw.com/files/manuals/MagicDraw%20OpenAPI%20UserGuide.pdf)
* [MagicDraw UML Metamodel](https://docs.nomagic.com/display/MD2021x/UML+2.5.1+Meta+Model)
