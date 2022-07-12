#!/bin/bash

if [ "$#" != 1 ]; then
  echo "Usage: $0 path/to/magicdraw/plugins"
  exit 1
fi

PLUGIN_DIR="$(readlink -f "$1/org.eclipse.epsilon.emc.magicdraw.mdplugin.remote")"

if ! test -d "$PLUGIN_DIR"; then
  mkdir "$PLUGIN_DIR"
  ln -s "$(readlink -f ../org.eclipse.epsilon.emc.magicdraw.mdplugin.remote/resources/magicdraw-plugin.xml)" "$PLUGIN_DIR/plugin.xml"
fi

cd "$(dirname "$0")"
mvn clean package
mvn dependency:copy-dependencies "-DoutputDirectory=$PLUGIN_DIR"
ls target/org.eclipse.epsilon.emc.magicdraw*.jar | grep -v uberjar | xargs cp -t "$PLUGIN_DIR"
ls target/org.eclipse.epsilon.emc.magicdraw*.jar | grep uberjar | xargs cp -t ../../bundles/org.eclipse.epsilon.emc.magicdraw.remote/lib/
