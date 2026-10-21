#!/bin/sh
set -eu
./scripts/ensure-pdfbox.sh
rm -rf build
mkdir -p build/classes build/test-classes
PDFBOX_JAR=.local-lib/pdfbox-app-3.0.8.jar
javac --release 25 --add-modules jdk.httpserver -cp "$PDFBOX_JAR" -d build/classes $(find src/main/java -name '*.java')
javac --release 25 --add-modules jdk.httpserver -cp "build/classes:$PDFBOX_JAR" -d build/test-classes $(find src/test/java -name '*.java')
java --add-modules jdk.httpserver -cp "build/classes:build/test-classes:$PDFBOX_JAR" edu.nus.tahelpdesk.PlatformIntegrationTest
java --add-modules jdk.httpserver -cp "build/classes:build/test-classes:$PDFBOX_JAR" edu.nus.tahelpdesk.SoCLaaSClientTest
