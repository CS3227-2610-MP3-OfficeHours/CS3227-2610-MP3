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

# Most managed Postgres add-ons (Render, Fly, Koyeb, Heroku) publish a single
# DATABASE_URL of the form postgres://user:password@host:port/database?params.
# Translate it into the application's own three-variable contract when that has
# not been set explicitly, so the image is not tied to one platform. Anything
# after the '@' is preserved verbatim, including a required ?sslmode=.
if [ -z "${TA_HELP_DESK_DATABASE_URL:-}" ] && [ -n "${DATABASE_URL:-}" ]; then
  _creds=${DATABASE_URL#*://}
  _userpass=${_creds%%@*}
  _location=${_creds#*@}
  TA_HELP_DESK_DATABASE_USER=${_userpass%%:*}
  TA_HELP_DESK_DATABASE_PASSWORD=${_userpass#*:}
  TA_HELP_DESK_DATABASE_URL="jdbc:postgresql://${_location}"
  export TA_HELP_DESK_DATABASE_USER TA_HELP_DESK_DATABASE_PASSWORD TA_HELP_DESK_DATABASE_URL
  unset _creds _userpass _location
fi

# Fail loudly and early rather than starting a half-configured production app.
# AppConfig re-validates these; this check only produces a clearer log line.
if [ "${TA_HELP_DESK_ENV:-}" = "production" ]; then
  if [ -z "${TA_HELP_DESK_DATABASE_URL:-}" ]; then
    echo "No database is configured, so this production container cannot start." >&2
    echo "Set either DATABASE_URL (the platform's own postgres:// connection string," >&2
    echo "which this entrypoint translates) or TA_HELP_DESK_DATABASE_URL together with" >&2
    echo "TA_HELP_DESK_DATABASE_USER and TA_HELP_DESK_DATABASE_PASSWORD." >&2
    echo "Neither DATABASE_URL nor TA_HELP_DESK_DATABASE_URL was present in this environment." >&2
    exit 1
  fi
  if [ -z "${TA_HELP_DESK_SESSION_SECRET:-}" ]; then
    echo "TA_HELP_DESK_SESSION_SECRET is required in production and must be at least 32 characters." >&2
    exit 1
  fi
fi

# No secret is echoed: only the non-sensitive shape of the configuration.
echo "Starting TA Help Desk: env=${TA_HELP_DESK_ENV:-development} port=${TA_HELP_DESK_PORT:-8080} client=${TA_HELP_DESK_STATIC_DIR:-none} database=$([ -n "${TA_HELP_DESK_DATABASE_URL:-}" ] && echo configured || echo in-memory)"

exec java ${JAVA_OPTS:-} --add-modules jdk.httpserver \
  -cp "/app/classes:/app/lib/*" \
  edu.nus.tahelpdesk.Main
