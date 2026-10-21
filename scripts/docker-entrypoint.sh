#!/bin/sh
# Production entrypoint. It translates the platform's injected PORT into the
# application's own TA_HELP_DESK_PORT contract and starts the server, which
# applies db/migrations before it accepts any request.
set -eu

# Railway and most PaaS edges inject PORT; the app's own variable wins if set
# explicitly, so a developer can still pin a port.
if [ -n "${PORT:-}" ] && [ "${TA_HELP_DESK_PORT:-8080}" = "8080" ]; then
  TA_HELP_DESK_PORT=$PORT
  export TA_HELP_DESK_PORT
fi

# Fail loudly and early rather than starting a half-configured production app.
# AppConfig re-validates these; this check only produces a clearer log line.
if [ "${TA_HELP_DESK_ENV:-}" = "production" ]; then
  [ -n "${TA_HELP_DESK_DATABASE_URL:-}" ] || { echo "TA_HELP_DESK_DATABASE_URL is required in production." >&2; exit 1; }
  [ -n "${TA_HELP_DESK_SESSION_SECRET:-}" ] || { echo "TA_HELP_DESK_SESSION_SECRET is required in production." >&2; exit 1; }
fi

# No secret is echoed: only the non-sensitive shape of the configuration.
echo "Starting TA Help Desk: env=${TA_HELP_DESK_ENV:-development} port=${TA_HELP_DESK_PORT:-8080} client=${TA_HELP_DESK_STATIC_DIR:-none} database=$([ -n "${TA_HELP_DESK_DATABASE_URL:-}" ] && echo configured || echo in-memory)"

exec java ${JAVA_OPTS:-} --add-modules jdk.httpserver \
  -cp "/app/classes:/app/lib/*" \
  edu.nus.tahelpdesk.Main
