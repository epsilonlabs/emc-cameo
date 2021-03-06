#!/bin/bash

set -e

if [ "$#" != 1 ]; then
  echo "Usage: $0 path/to/magicdraw/plugins"
  exit 1
fi

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"
PLUGIN_DIR="$(readlink -f "$1/org.eclipse.epsilon.emc.magicdraw.mdplugin.remote")"

# Always recreate the plugin folder from scratch
if test -d "$PLUGIN_DIR"; then
  rm -r "$PLUGIN_DIR"
fi
mkdir "$PLUGIN_DIR"
ln -s "$(readlink -f ../org.eclipse.epsilon.emc.magicdraw.mdplugin.remote/resources/magicdraw-plugin.xml)" "$PLUGIN_DIR/plugin.xml"

# Add the remote model access API and its dependencies
cd "$SCRIPT_DIR"
BUNDLE_VERSION="$(grep Bundle-Version ../../bundles/org.eclipse.epsilon.emc.magicdraw.remote/META-INF/MANIFEST.MF  | awk '{print $2}' | cut --delim=. -f1-3)"
SHORT_REV="$(git rev-parse --short HEAD)"
mvn clean package
mvn dependency:copy-dependencies "-DoutputDirectory=$PLUGIN_DIR"
ls target-plain/org.eclipse.epsilon.emc.magicdraw*.jar | grep -v uberjar | xargs cp -t "$PLUGIN_DIR"
ls target-plain/org.eclipse.epsilon.emc.magicdraw*.jar | grep uberjar | xargs cp -t ../../bundles/org.eclipse.epsilon.emc.magicdraw.remote/lib/

# Add the remote model access server
cd ../org.eclipse.epsilon.emc.magicdraw.mdplugin.remote
jar cf "$PLUGIN_DIR/epsilon-mdplugin.jar" -C bin/ .

# Create a redistributable ZIP with the MagicDraw plugin
cd "$PLUGIN_DIR/.."
zip -r "$SCRIPT_DIR/cameo-mdplugin-$BUNDLE_VERSION-$SHORT_REV-$(date +'%Y%m%d%H%M%S').zip" "$(basename "$PLUGIN_DIR")"
