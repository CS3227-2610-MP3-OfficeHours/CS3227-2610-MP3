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
exec java --add-modules jdk.httpserver -cp "build/classes:$PDFBOX_JAR" edu.nus.tahelpdesk.Main
