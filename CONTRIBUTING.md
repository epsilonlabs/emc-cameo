# Contributor documentation

## Overall repository structure

* `bundles`: Eclipse plugin project for the EMC driver.
* `examples`: example Eclipse projects using the EMC driver.
  * `org.eclipse.epsilon.emc.magicdraw.examples.etl`: example ETL transformation from Tree models to MagicDraw class diagrams, launched in three ways: from Ant running in Eclipse's JRE, from a standalone Ant using the self-contained Ant distribution, and from a Maven POM while fetching dependencies from Maven Central and Github Packages.
* `magicdraw-plugins`: source code for the MagicDraw plugin needed to gain access to MagicDraw/Cameo models.
  * `org.eclipse.epsilon.emc.magicdraw.mdplugin.remote`: the actual MagicDraw plugin, as a plain Java project. Implements a remote model access server.
  * `org.eclipse.epsilon.emc.magicdraw.modelapi`: generates the gRPC client/server stubs for accessing models from a [Protobuf descriptor](magicdraw-plugin/org.eclipse.epsilon.emc.magicdraw.modelapi/src/main/proto/ModelService.proto), as a plain Maven project.
* `releng`: Eclipse target platform, Eclipse features, Eclipse update site, and an Ant-oriented self-contained distribution for users behind corporate firewalls.
* `tests`: Eclipse fragment projects to test the EMC driver, along with sample MagicDraw models.

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

This build is for redistributing the EMC driver via Maven as plain JARs (e.g. for use from the [Epsilon Ant tasks](https://www.eclipse.org/epsilon/doc/workflow/)), for generating the Ant-oriented self-contained distribution, and for developers that are not using Eclipse.

Specifically, this plain Maven build does not run the `create-magicdraw-plugin.sh` script, which is needed when developing the EMC driver as an Eclipse plugin.

Note that you will still need a running MagicDraw instance with the appropriate model open to pass the tests.
Please check the `ZooModelTest` test class for details.

## Checklist for new features / bugfixes

First of all, if the change is potentially large or you are unfamiliar with the project, consider using a [feature branch](https://www.atlassian.com/git/tutorials/comparing-workflows/feature-branch-workflow) to do your work, and then file a pull request to be reviewed by the maintainer.

Before you merge the new feature into the driver, consider the following:

* Does it have a test for the new functionality / the bug that was fixed? We cannot be sure the new feature / bugfix will continue to work in future releases unless it has a test.
* Do all tests pass on clean versions of the test MagicDraw models? We cannot run tests on CI due to the dependency on MagicDraw.
* If the change is user-facing, does it need further explanation in [`README.md`](README.md)?
* If it impacts the developer experience, does `CONTRIBUTING.md` need to be updated?
* Do both plain Maven and Tycho builds pass during CI?

## Preparing a new release

If you are a developer wanting to create a new release, follow these steps:

1. Use the `create-magicdraw-plugin.sh` to ensure MagicDraw has the latest version of the plugin needed for the driver.
1. Start MagicDraw and open the sample Zoo model in the test resources.
1. Run `mvn -f pom-plain.xml clean install` to do a plain Maven build and run the tests from a plain Java environment.
1. Run `mvn clean install` to do a Tycho build, running the tests from the Tycho environment.
1. If all tests pass, create a new release on Github, and ensure that you upload:
   * The `cameo-mdplugin-*.zip` file created by the `create-magicdraw-plugin.sh` script.
   * The zipped update site in `releng/org.eclipse.epsilon.emc.magicdraw.updatesite/target`.
   * The zipped standalone Ant-based distribution in `releng/org.eclipse.epsilon.emc.magicdraw.antdist/target-plain`.
1. In the "Actions" tab, double check that the workflow pushing artifacts to Github Packages also worked.
