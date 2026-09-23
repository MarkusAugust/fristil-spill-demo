# Datastar-appen, i Kotlin. Bygges fra rota: den serverer `felles/brett.css`
# og leser kommunelista derfra.
FROM eclipse-temurin:21-jdk AS bygger
WORKDIR /kilde

COPY apper/datastar/gradle gradle
COPY apper/datastar/gradlew apper/datastar/settings.gradle.kts apper/datastar/build.gradle.kts ./
RUN ./gradlew --no-daemon --quiet dependencies > /dev/null 2>&1 || true

COPY apper/datastar/src src
RUN ./gradlew --no-daemon --quiet installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=bygger /kilde/build/install/datastar-app datastar-app
COPY felles felles

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=45 -XX:+UseSerialGC -Xss512k" \
    BRETT_CSS_FIL=/app/felles/brett.css \
    PANEL_JS_FIL=/app/felles/panel.js \
    PANEL_AVLYTT_JS_FIL=/app/felles/panel-avlytt.js \
    KOMMUNER_FIL=/app/felles/kommuner.json \
    HOST=:: \
    PORT=8081

CMD ["/app/datastar-app/bin/datastar-app"]
