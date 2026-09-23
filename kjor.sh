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
#
# `lsof -nP -iTCP:<port> -sTCP:LISTEN` og ikke `lsof -ti tcp:<port>`: den
# siste treffer også klientsiden av en åpen forbindelse, altså nettleseren
# din, og et `kill` på den lista feller mer enn tjeneren.
for port in 8080 8081 8082 8083 8084; do
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "Port $port er opptatt. Noe kjører fra før."
    echo "Stopp det med:  lsof -nP -iTCP:8080 -iTCP:8081 -iTCP:8082 -iTCP:8083 -iTCP:8084 -sTCP:LISTEN -t | xargs kill"
    exit 1
  fi
done

java -version 2>&1 | head -1

echo "Bygger …"
(cd apper/spilltjener && ./gradlew --quiet installDist)
(cd apper/datastar && ./gradlew --quiet installDist)
(cd apper/tanstack && bun install --silent)
(cd apper/astro && bun install --silent && ./node_modules/.bin/astro build >/dev/null)

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

# TanStack-appen kjører i utviklingsmodus. Det er den ærlige utgaven av
# «slik ser dette ut å jobbe i», og den bygger seg selv mens den står.
(cd apper/tanstack && exec env SPILLTJENER=http://127.0.0.1:8080 \
  ./node_modules/.bin/vite dev --port 8082 --clearScreen false) &
PIDER+=($!)
# `localhost` og ikke `127.0.0.1`: Vites utviklingstjener lytter på det
# navnet, som på macOS slår opp til IPv6 først, og en sjekk mot IPv4-adressen
# ville stått og ventet på noe som aldri kommer.
vent_pa http://localhost:8082/helse

# Astro-appen kjøres bygget, ikke i utviklingsmodus. `astro dev` legger seg i
# bakgrunnen og svarer ikke på Ctrl+C her, og den bygde tjeneren er dessuten
# den samme som kjører i drift.
(cd apper/astro && exec env HOST=127.0.0.1 PORT=8083 SPILLTJENER=http://127.0.0.1:8080 \
  node ./dist/server/entry.mjs) &
PIDER+=($!)
vent_pa http://127.0.0.1:8083/helse

# Skallet viser de tre side om side. Det har ingen avhengigheter og snakker
# ikke med spilltjeneren; hver ramme er en helt vanlig adresse.
(cd apper/skall && exec env PORT=8084 HOST=127.0.0.1 bun run src/skall.ts) &
PIDER+=($!)
vent_pa http://127.0.0.1:8084/helse

echo
echo "  Førstelinja kjører:"
echo "    Datastar og Kotlin:      http://localhost:8081"
echo "    TanStack Start og React: http://localhost:8082"
echo "    Astro, hele sider:       http://localhost:8083"
echo
echo "    Alle tre side om side:   http://localhost:8084"
echo
echo "  Åpne den i to vinduer, ett vanlig og ett privat, så spiller du mot"
echo "  deg selv og ser tavla oppdatere seg begge steder."
echo
echo "  Ctrl+C for å stoppe."
echo

wait
