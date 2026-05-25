#!/bin/bash
set -e

# Eger JAVA_HOME tanimliysa oradaki javac'i kullan
if [ -n "$JAVA_HOME" ]; then
    JAVAC="$JAVA_HOME/bin/javac"
    JAR="$JAVA_HOME/bin/jar"
else
    JAVAC="javac"
    JAR="jar"
fi

echo "Compiler Version:"
"$JAVAC" -version

rm -rf build
mkdir -p build dist
"$JAVAC" -source 11 -target 11 -d build -cp "lib/*" src/SystemPrompts.java src/Main.java src/TerminalApp.java
"$JAR" cfm dist/TerminalAsistani.jar src/Manifest.txt -C build .
echo "Build Complete!"
