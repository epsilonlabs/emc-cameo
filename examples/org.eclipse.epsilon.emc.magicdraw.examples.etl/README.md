# ETL example project

This example project contains a small [ETL](https://www.eclipse.org/epsilon/doc/etl/) transformation from a small `Tree` model to your currently opened MagicDraw/Cameo model.
It is meant as a quick check that the communication between this EMC driver and MagicDraw/Cameo works correctly.

## Running from the command line

This section assumes that you have [Apache Ant](https://ant.apache.org/) installed and ready to run from your command line as `ant`.
For more information, please check the [official install page](https://ant.apache.org/manual/install.html).

To run this transformation from outside Eclipse, follow these steps:

1. Visit the [releases for this project](https://github.com/epsilonlabs/emc-cameo/releases) and download the latest `cameo-mdplugin-*.zip` file. Unzip it into your MagicDraw/Cameo's `plugins` directory. You should end up with a new `org.eclipse.epsilon.emc.magicdraw.mdplugin.remote` folder inside the `plugins` directory, containing a `plugin.xml` file and many `.jar` files.
1. Download from the same release the `org.eclipse.epsilon.emc.magicdraw.antdist-*.zip` file, and unzip it into a directory in your system. Note down the full path to its `lib` subdirectory, which contains all the necessary `.jar` files to use the Epsilon core languages, the EMF EMC driver, and this MagicDraw/Cameo driver.
1. Start MagicDraw/Cameo as usual, and create a new UML project.
1. Open a console prompt, go to the folder containing this example, and run this command:

```shell
ant -lib path-to-antdist-lib run-standalone
```

## Running from Eclipse

This section assumes that you have Eclipse and Epsilon installed.
Please check the [official Epsilon documentation](https://www.eclipse.org/epsilon/download/) for details.

The initial setup is as follows:

1. Install the MagicDraw/Cameo plugin, as shown in step 1 of "Running from the command line".
1. Start MagicDraw/Cameo as usual, and create a new UML project.
1. Visit the [releases for this project](https://github.com/epsilonlabs/emc-cameo/releases) and download the latest `org.eclipse.epsilon.emc.magicdraw.updatesite-*.zip` file. Do not unzip it.
1. From Eclipse, use "Help - Install New Software..." to install the EMC driver from the zipped update site.
1. Allow Eclipse to restart.
1. Use "File - Import - Existing Projects into Workspace..." to import this directory as an Eclipse project.

You will now be able to run the ETL transformation in several ways:

* From the "Project Explorer" view on the left, right-click on `Run Tree2Classes from Eclipse.launch` and select "Run As - Run Tree2Classes from Eclipse". This will use an ETL run configuration to launch the transformation.
* From the same "Project Explorer", right-click on `Run Tree2Classes from Ant in Eclipse.launch` and select "Run As - Run Tree2Classes from Ant in Eclipse". This will run the `run-eclipse` target inside the Ant buildfile in `build.xml`.