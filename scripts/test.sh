#!/bin/sh
set -eu
./scripts/ensure-pdfbox.sh
rm -rf build
mkdir -p build/classes build/test-classes
PDFBOX_JAR=.local-lib/pdfbox-app-3.0.8.jar
# The JVM classpath separator is ';' on Windows and ':' elsewhere, so the same
# script runs under Git Bash and under a POSIX shell.
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
  *) SEP=':' ;;
esac
javac --release 25 --add-modules jdk.httpserver -cp "$PDFBOX_JAR" -d build/classes $(find src/main/java -name '*.java')
javac --release 25 --add-modules jdk.httpserver -cp "build/classes$SEP$PDFBOX_JAR" -d build/test-classes $(find src/test/java -name '*.java')
TEST_CP="build/classes$SEP""build/test-classes$SEP$PDFBOX_JAR"
java --add-modules jdk.httpserver -cp "$TEST_CP" edu.nus.tahelpdesk.PlatformIntegrationTest
java --add-modules jdk.httpserver -cp "$TEST_CP" edu.nus.tahelpdesk.StudentAiHelpTest
java --add-modules jdk.httpserver -cp "$TEST_CP" edu.nus.tahelpdesk.SoCLaaSClientTest
# StaticSiteTest binds a local HTTP listener and is therefore the last suite:
# where a sandbox forbids loopback binding it aborts the run under `set -e`,
# and the suites above still report their results first.
java --add-modules jdk.httpserver -cp "$TEST_CP" edu.nus.tahelpdesk.StaticSiteTest
