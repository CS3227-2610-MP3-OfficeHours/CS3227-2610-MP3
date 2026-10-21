#!/bin/sh
set -eu
rm -rf build
mkdir -p build/classes build/test-classes
javac --release 25 --add-modules jdk.httpserver -d build/classes $(find src/main/java -name '*.java')
javac --release 25 --add-modules jdk.httpserver -cp build/classes -d build/test-classes $(find src/test/java -name '*.java')
java --add-modules jdk.httpserver -cp build/classes:build/test-classes edu.nus.tahelpdesk.PlatformIntegrationTest
java --add-modules jdk.httpserver -cp build/classes:build/test-classes edu.nus.tahelpdesk.SoCLaaSClientTest
