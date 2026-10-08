#!/bin/bash
# Morph Watch builder for Mac.
# Double-click this file. It builds the mod .jar and puts it in your Minecraft (TLauncher) mods folder.

cd "$(dirname "$0")" || exit 1
set -o pipefail

pause_and_exit() {
  echo
  read -n 1 -s -r -p "Press any key to close this window..."
  echo
  exit "$1"
}

echo "=============================="
echo "   Morph Watch mod builder"
echo "=============================="
echo

# 1. Java 17 is needed to build Minecraft 1.20.1 mods
JAVA17=$(/usr/libexec/java_home -v 17 2>/dev/null)
if [ -z "$JAVA17" ]; then
  echo "Java 17 is not installed yet."
  echo
  echo "Opening the download page now:"
  echo "  1. Download the .pkg file for macOS"
  echo "     (aarch64 = Apple M1/M2/M3/M4 chip, x64 = Intel)"
  echo "  2. Open it and click through the installer"
  echo "  3. Double-click BUILD-MOD.command again"
  open "https://adoptium.net/temurin/releases/?version=17&os=mac&package=jdk"
  pause_and_exit 1
fi
export JAVA_HOME="$JAVA17"
echo "Found Java 17."
echo

# 2. Build the .jar
chmod +x ./gradlew
echo "Building the mod..."
echo "(The first time downloads Minecraft and Forge, so it can take 10 minutes or more.)"
echo
if ! ./gradlew build --no-daemon 2>&1 | tee build-log.txt; then
  echo
  echo "------------------------------------------------------"
  echo " The build FAILED."
  echo " Send the file build-log.txt (in the MorphWatch folder)"
  echo " to Claude and it will fix the problem."
  echo "------------------------------------------------------"
  open -R build-log.txt
  pause_and_exit 1
fi

JAR=$(ls build/libs/morphwatch-*.jar 2>/dev/null | grep -v -e sources -e slim | head -n 1)
if [ -z "$JAR" ]; then
  echo "Build finished but no .jar was found in build/libs. Send build-log.txt to Claude."
  pause_and_exit 1
fi

# 3. Put it in the mods folder (TLauncher on Mac uses the normal Minecraft folder)
MODS="$HOME/Library/Application Support/minecraft/mods"
mkdir -p "$MODS"
rm -f "$MODS"/morphwatch-*.jar
cp "$JAR" "$MODS/"

echo
echo "======================================================"
echo " DONE! $(basename "$JAR") is in your mods folder:"
echo " $MODS"
echo
echo " Now open TLauncher, pick Forge 1.20.1 and press Play."
echo "======================================================"
open "$MODS"
pause_and_exit 0
