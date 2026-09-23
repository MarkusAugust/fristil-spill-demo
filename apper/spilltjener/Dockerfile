# Spilltjeneren. Bygges fra rota av repoet, siden den trenger `felles/`.
#
# Railway setter «Dockerfile Path» til denne fila og lar «Root Directory»
# stå tom. Da er byggekonteksten rota, og `felles/saker.json` finnes.
FROM eclipse-temurin:21-jdk AS bygger
WORKDIR /kilde

# Avhengighetene først, i sitt eget lag: de endrer seg sjelden, og da
# slipper hver utrulling å hente Kotlin og Ktor på nytt.
COPY apper/spilltjener/gradle gradle
COPY apper/spilltjener/gradlew apper/spilltjener/settings.gradle.kts apper/spilltjener/build.gradle.kts ./
RUN ./gradlew --no-daemon --quiet dependencies > /dev/null 2>&1 || true

COPY apper/spilltjener/src src
RUN ./gradlew --no-daemon --quiet installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=bygger /kilde/build/install/spilltjener spilltjener
COPY felles felles

# Sakene og kommunene ligger i bildet. Topplista gjør ikke det: den skal
# overleve en utrulling, og hører derfor på et volum montert på /data.
ENV SAKER_FIL=/app/felles/saker.json \
    KOMMUNER_FIL=/app/felles/kommuner.json \
    TOPPLISTE_FIL=/data/toppliste.db \
    HOST=:: \
    PORT=8080

CMD ["/app/spilltjener/bin/spilltjener"]
