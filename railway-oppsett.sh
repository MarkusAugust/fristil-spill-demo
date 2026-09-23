#!/usr/bin/env bash
#
# Setter opp de fem tjenestene i et Railway-prosjekt du alt har laget.
#
#   railway login
#   railway link          # velg prosjektet, miljøet production
#   ./railway-oppsett.sh
#
# Skriptet gjør det samme som klikkingen i grensesnittet, men uten å gå seg
# vill i den. Det er trygt å kjøre om igjen: finnes en tjeneste fra før,
# sier Railway fra, og resten går videre.
#
# Alt bygges med Dockerfile fra rota av repoet. `RAILWAY_DOCKERFILE_PATH`
# peker på riktig fil per tjeneste, og «Root Directory» skal stå tom: sakene,
# kommunene og stilarket ligger i `felles/`, utenfor appene, og en rotmappe
# per tjeneste ville gjemt dem for byggeren.

set -euo pipefail

REPO="${REPO:-MarkusAugust/fristil-spill-demo}"
GREN="${GREN:-master}"
PROSJEKT="${PROSJEKT:-forstelinja}"
MILJO="${MILJO:-production}"

if ! railway whoami > /dev/null 2>&1; then
  echo "Ikke innlogget. Kjør «railway login» først."
  exit 1
fi

lag() {
  local navn="$1" sti="$2"
  shift 2
  echo
  echo "== $navn"
  local args=(--service "$navn" --repo "$REPO" --branch "$GREN"
              --variables "RAILWAY_DOCKERFILE_PATH=$sti")
  local v
  for v in "$@"; do args+=(--variables "$v"); done
  railway add "${args[@]}" || echo "  (fantes fra før, går videre)"
}

# Spilltjeneren først. Den skal ikke ha noe offentlig domene: appene når den
# på det private nettet, og der er Railway IPv6-bare, så tjenesten lytter på
# `::`. Det står i Dockerfilen.
lag spilltjener apper/spilltjener/Dockerfile \
  "TOPPLISTE_FIL=/data/toppliste.db"

INTERN="http://spilltjener.railway.internal:8080"

lag datastar apper/datastar/Dockerfile "SPILLTJENER=$INTERN"
lag tanstack apper/tanstack/Dockerfile "SPILLTJENER=$INTERN"
lag astro    apper/astro/Dockerfile    "SPILLTJENER=$INTERN"
lag skall    apper/skall/Dockerfile

# Topplista skal overleve en utrulling, og hører derfor på et volum.
# `railway volume add` tar ingen `--service`: den legger volumet på tjenesten
# som er lenket, så lenkingen er selve valget.
echo
echo "== volum på spilltjeneren"
railway link --project "$PROSJEKT" --environment "$MILJO" --service spilltjener > /dev/null
railway volume add --mount-path /data || echo "  (fantes fra før, går videre)"

# De fire som skal nås utenfra får hver sitt domene.
for tjeneste in datastar tanstack astro skall; do
  echo
  echo "== domene for $tjeneste"
  railway domain --service "$tjeneste" || echo "  (hadde domene fra før)"
done

# Og til slutt hvem som lenker til hvem. Referansene løses av Railway, så de
# må settes etter at tjenestene finnes.
echo
echo "== adressene til de tre utgavene"
for tjeneste in datastar tanstack astro skall; do
  railway variables --service "$tjeneste" \
    --set "DATASTAR_URL=https://\${{datastar.RAILWAY_PUBLIC_DOMAIN}}" \
    --set "TANSTACK_URL=https://\${{tanstack.RAILWAY_PUBLIC_DOMAIN}}" \
    --set "ASTRO_URL=https://\${{astro.RAILWAY_PUBLIC_DOMAIN}}"
done

echo
echo "Ferdig. Følg utrullingene med «railway logs --service spilltjener»."
echo "Skallet er inngangen: «railway domain --service skall» viser adressen."
