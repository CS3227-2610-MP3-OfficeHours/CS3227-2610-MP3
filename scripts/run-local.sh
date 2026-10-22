#!/bin/sh
set -eu
./scripts/ensure-pdfbox.sh
if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi
rm -rf build/classes
mkdir -p build/classes
PDFBOX_JAR=.local-lib/pdfbox-app-3.0.8.jar
javac --release 25 --add-modules jdk.httpserver -cp "$PDFBOX_JAR" -d build/classes $(find src/main/java -name '*.java')
# The JVM classpath separator is ';' on Windows and ':' elsewhere.
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) SEP=';' ;;
  *) SEP=':' ;;
esac
# Serve the built client from the same origin when it has been built.
if [ -z "${TA_HELP_DESK_STATIC_DIR:-}" ] && [ -d src/frontend/dist ]; then
  TA_HELP_DESK_STATIC_DIR=src/frontend/dist
  export TA_HELP_DESK_STATIC_DIR
fi
exec java --add-modules jdk.httpserver -cp "build/classes$SEP$PDFBOX_JAR" edu.nus.tahelpdesk.Main
