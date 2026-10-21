#!/bin/sh
set -eu
./scripts/ensure-pdfbox.sh
driver=${TA_HELP_DESK_JDBC_JAR:-.local-lib/postgresql.jar}
if [ ! -f "$driver" ]; then echo "Set TA_HELP_DESK_JDBC_JAR to the PostgreSQL JDBC driver." >&2; exit 1; fi
docker compose up -d postgres
mkdir -p build/classes build/test-classes
rm -rf build/classes build/test-classes
mkdir -p build/classes build/test-classes
PDFBOX_JAR=.local-lib/pdfbox-app-3.0.8.jar
javac --release 25 --add-modules jdk.httpserver -cp "$PDFBOX_JAR" -d build/classes $(find src/main/java -name '*.java')
javac --release 25 --add-modules jdk.httpserver -cp "build/classes:$PDFBOX_JAR" -d build/test-classes $(find src/test/java -name '*.java')
export TA_HELP_DESK_DATABASE_URL=${TA_HELP_DESK_DATABASE_URL:-jdbc:postgresql://localhost:5432/tahelpdesk}
export TA_HELP_DESK_DATABASE_USER=${TA_HELP_DESK_DATABASE_USER:-tahelpdesk}
export TA_HELP_DESK_DATABASE_PASSWORD=${TA_HELP_DESK_DATABASE_PASSWORD:-local-development-only}
java --add-modules jdk.httpserver -cp "build/classes:build/test-classes:$PDFBOX_JAR:$driver" edu.nus.tahelpdesk.PostgresPlatformIntegrationTest
