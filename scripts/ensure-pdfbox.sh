#!/bin/sh
# Apache PDFBox 3.0.8, Apache-2.0. The app artifact bundles its runtime dependencies.
set -eu
version=3.0.8
jar=.local-lib/pdfbox-app-$version.jar
sha512=768847238f683568507bf73570a2b6fedcbe58b25c7b4f97fba536ba110b290fe96ba065aed58629d41fb94857d76bc1978c2f31d294b553c69f287f71ee9600
if [ ! -f "$jar" ]; then
  mkdir -p .local-lib
  curl --fail --location --retry 2 --connect-timeout 15 -o "$jar.tmp" "https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox-app/$version/pdfbox-app-$version.jar"
  actual=$(shasum -a 512 "$jar.tmp" | awk '{print $1}')
  [ "$actual" = "$sha512" ] || { rm -f "$jar.tmp"; echo "PDFBox checksum verification failed." >&2; exit 1; }
  mv "$jar.tmp" "$jar"
fi
actual=$(shasum -a 512 "$jar" | awk '{print $1}')
[ "$actual" = "$sha512" ] || { echo "PDFBox checksum verification failed; remove $jar and retry." >&2; exit 1; }
