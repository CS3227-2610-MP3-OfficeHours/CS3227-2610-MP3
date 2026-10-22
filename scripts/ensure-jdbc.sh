#!/bin/sh
# PostgreSQL JDBC driver 42.7.8, BSD-2-Clause. Fetched into the ignored
# .local-lib/ cache and pinned by SHA-512; no third-party JAR is committed.
set -eu
version=42.7.8
jar=${TA_HELP_DESK_JDBC_JAR:-.local-lib/postgresql.jar}
sha512=3ec6f2186ad9f2cbd7916cc1749b61433137c78c13e13b7583507051733318423505fa61908242f4d02e0d90f7db6eecdb6d573a966773d274bf2c78d976573b

# sha512sum on Linux, shasum on macOS; the image and the runner differ.
digest() {
  if command -v sha512sum >/dev/null 2>&1; then sha512sum "$1" | awk '{print $1}'
  else shasum -a 512 "$1" | awk '{print $1}'; fi
}

if [ ! -f "$jar" ]; then
  mkdir -p "$(dirname "$jar")"
  curl --fail --location --retry 2 --connect-timeout 15 -o "$jar.tmp" \
    "https://repo1.maven.org/maven2/org/postgresql/postgresql/$version/postgresql-$version.jar"
  actual=$(digest "$jar.tmp")
  [ "$actual" = "$sha512" ] || { rm -f "$jar.tmp"; echo "PostgreSQL JDBC checksum verification failed." >&2; exit 1; }
  mv "$jar.tmp" "$jar"
fi
actual=$(digest "$jar")
[ "$actual" = "$sha512" ] || { echo "PostgreSQL JDBC checksum verification failed; remove $jar and retry." >&2; exit 1; }
