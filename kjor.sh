#!/usr/bin/env bash
#
# Starter Førstelinja lokalt: spilltjeneren og Datastar-appen.
#
#   ./kjor.sh              vanlige runder på to minutter
#   ./kjor.sh rask         korte runder, til å prøve spillet fort
#
# Krever Java 21 eller nyere. Gradle henter seg selv.

set -euo pipefail
cd "$(dirname "$0")"

if [ "${1:-}" = "rask" ]; then
  export RUNDE_MS=30000 OPPGJOR_MS=8000 SLUTT_MS=15000
  echo "Rask modus: runder på 30 sekunder."
fi

# Ligger det igjen en tjener fra sist, feiler starten med «Address already in
# use» langt nede i en stakksporing. Si det heller her, og si hvordan.
for port in 8080 8081; do
  if lsof -ti "tcp:$port" >/dev/null 2>&1; then
    echo "Port $port er opptatt. Noe kjører fra før."
    echo "Stopp det med:  lsof -ti tcp:8080,tcp:8081 | xargs kill"
    exit 1
  fi
done

java -version 2>&1 | head -1

echo "Bygger …"
(cd apper/spilltjener && ./gradlew --quiet installDist)
(cd apper/datastar && ./gradlew --quiet installDist)

PIDER=()

rydd() {
  trap - EXIT INT TERM
  echo
  echo "Stopper."
  for pid in "${PIDER[@]:-}"; do
    [ -n "$pid" ] && kill "$pid" 2>/dev/null || true
  done
  wait 2>/dev/null || true
}
trap rydd EXIT INT TERM

# `exec` er ikke pynt. Uten den er $! skallet rundt, ikke Java, og Ctrl+C
# felte skallet mens tjeneren ble stående og holdt porten. Med `exec` blir
# oppstartsskriptet til Gradle selve prosessen, og det kjører Java med `exec`
# igjen, så $! er den prosessen som faktisk lytter.
# Vent på det som setter tilstanden, ikke på klokka. Begge tjenestene har
# `/helse`, og en `sleep` som er for kort på en kald maskin ville skrevet
# «Førstelinja kjører» før den gjorde det.
vent_pa() {
  for _ in $(seq 1 60); do
    curl -fsS -m 1 "$1" >/dev/null 2>&1 && return 0
    sleep 1
  done
  echo "Fikk ikke svar fra $1"
  return 1
}

(cd apper/spilltjener && exec env PORT=8080 HOST=127.0.0.1 \
  ./build/install/spilltjener/bin/spilltjener) &
PIDER+=($!)
vent_pa http://127.0.0.1:8080/helse

(cd apper/datastar && exec env PORT=8081 HOST=127.0.0.1 SPILLTJENER=http://127.0.0.1:8080 \
  ./build/install/datastar-app/bin/datastar-app) &
PIDER+=($!)
vent_pa http://127.0.0.1:8081/helse

echo
echo "  Førstelinja kjører:  http://localhost:8081"
echo
echo "  Åpne den i to vinduer, ett vanlig og ett privat, så spiller du mot"
echo "  deg selv og ser tavla oppdatere seg begge steder."
echo
echo "  Ctrl+C for å stoppe."
echo

wait
