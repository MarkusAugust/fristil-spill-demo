#!/usr/bin/env bash
#
# Starter Førstelinja lokalt: spilltjeneren og Datastar-appen.
#
#   ./kjor.sh              vanlige runder på 30 sekunder
#   ./kjor.sh rolig        lange runder, til å se seg om i
#
# Krever Java 21 eller nyere. Gradle henter seg selv.

set -euo pipefail
cd "$(dirname "$0")"

if [ "${1:-}" = "rolig" ]; then
  export RUNDE_MS=180000 OPPGJOR_MS=30000 SLUTT_MS=60000
  echo "Rolig modus: tre minutter per runde."
fi

java -version 2>&1 | head -1

echo "Bygger …"
(cd apper/spilltjener && ./gradlew --quiet installDist)
(cd apper/datastar && ./gradlew --quiet installDist)

rydd() {
  echo
  echo "Stopper."
  kill "${PIDER[@]}" 2>/dev/null || true
}
trap rydd EXIT INT TERM
PIDER=()

(cd apper/spilltjener && PORT=8080 HOST=127.0.0.1 ./build/install/spilltjener/bin/spilltjener) &
PIDER+=($!)
sleep 6

(cd apper/datastar && PORT=8081 HOST=127.0.0.1 SPILLTJENER=http://127.0.0.1:8080 \
  ./build/install/datastar-app/bin/datastar-app) &
PIDER+=($!)
sleep 4

echo
echo "  Førstelinja kjører:  http://localhost:8081"
echo
echo "  Åpne den i to vinduer, ett vanlig og ett privat, så spiller du mot"
echo "  deg selv og ser tavla oppdatere seg begge steder."
echo
echo "  Ctrl+C for å stoppe."
echo

wait
