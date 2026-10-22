# Production image for the University TA Help Desk: one container serving the
# JSON API and the built React client on a single origin. TLS is terminated by
# the platform edge; this process speaks plain HTTP on $PORT.

# ---------- client ----------
FROM node:22.12.0-bookworm-slim AS client
WORKDIR /client
COPY src/frontend/package.json src/frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY src/frontend/ ./
# tsc -b type-checks, vite build emits dist/. No server secret is available at
# build time, so none can be baked into the bundle.
RUN npm run build

# ---------- server ----------
FROM eclipse-temurin:25-jdk AS server
WORKDIR /build
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && rm -rf /var/lib/apt/lists/*
COPY scripts/ensure-pdfbox.sh scripts/ensure-jdbc.sh scripts/
RUN sh scripts/ensure-pdfbox.sh && sh scripts/ensure-jdbc.sh
COPY src/main/java/ src/main/java/
RUN mkdir -p classes \
 && javac --release 25 --add-modules jdk.httpserver \
      -cp .local-lib/pdfbox-app-3.0.8.jar \
      -d classes $(find src/main/java -name '*.java')

# ---------- runtime ----------
FROM eclipse-temurin:25-jre
# An unprivileged account owns nothing it does not need to read.
RUN useradd --system --create-home --uid 10001 app
WORKDIR /app
COPY --from=server /build/classes/ classes/
COPY --from=server /build/.local-lib/ lib/
COPY --from=client /client/dist/ client/
# PostgresPlatform reads db/migrations relative to the working directory and
# applies every unapplied file before Main starts accepting requests.
COPY db/migrations/ db/migrations/
COPY scripts/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh && chown -R app:app /app
USER app
ENV TA_HELP_DESK_ENV=production \
    TA_HELP_DESK_PORT=8080 \
    TA_HELP_DESK_STATIC_DIR=/app/client \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
