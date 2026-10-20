#!/bin/sh
set -eu
rm -rf build/classes
mkdir -p build/classes
javac --release 25 --add-modules jdk.httpserver -d build/classes $(find src/main/java -name '*.java')
exec java --add-modules jdk.httpserver -cp build/classes edu.nus.tahelpdesk.Main
