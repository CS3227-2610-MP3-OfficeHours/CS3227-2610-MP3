#!/bin/sh
set -eu
if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi

# The JDBC driver is intentionally local-only and never committed. Download it
# once into .local-lib/, or set TA_HELP_DESK_JDBC_JAR to its absolute path.
driver=${TA_HELP_DESK_JDBC_JAR:-.local-lib/postgresql.jar}
if [ ! -f "$driver" ]; then
  echo "PostgreSQL JDBC driver not found at $driver." >&2
  echo "Set TA_HELP_DESK_JDBC_JAR, then rerun. See README.md." >&2
  exit 1
fi
docker compose up -d postgres
mkdir -p build/classes
rm -rf build/classes/*
javac --release 25 --add-modules jdk.httpserver -d build/classes $(find src/main/java -name '*.java')
export TA_HELP_DESK_DATABASE_URL=${TA_HELP_DESK_DATABASE_URL:-jdbc:postgresql://localhost:5432/tahelpdesk}
export TA_HELP_DESK_DATABASE_USER=${TA_HELP_DESK_DATABASE_USER:-tahelpdesk}
export TA_HELP_DESK_DATABASE_PASSWORD=${TA_HELP_DESK_DATABASE_PASSWORD:-local-development-only}
exec java --add-modules jdk.httpserver -cp "build/classes:$driver" edu.nus.tahelpdesk.Main
