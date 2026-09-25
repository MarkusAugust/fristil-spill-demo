import { chromium } from "playwright"

/**
 * At én spiller i flere utgaver samtidig ikke setter spilltjeneren i sving.
 *
 * Det er skallet: tre rammer, og lokalt deler de tre portene på localhost
 * den samme kapselen, så den som melder seg på i én ramme er samme spiller i
 * alle tre. Hver app henter tilstanden på nytt for hver hendelse, og sa
 * hver henting fra hvilken utgave spilleren satt i, flyttet spilltjeneren
 * henne fram og tilbake og sendte en hendelse for hver flytting. Én
 * påmelding ga da over sju tusen hendelser i sekundet, og spillet sto
 * stille.
 *
 * Testen gjør det skallet gjør: åpner de tre utgavene som samme spiller,
 * holder en egen strøm mot spilltjeneren, utløser én hendelse, og krever at
 * strømmen blir stille igjen. Den ser på ledningen og ikke på skjermen,
 * fordi det er ledningen som gikk galt: skjermene så helt normale ut.
 *
 * Appene må kjøre først:
 *
 *     ./kjor.sh rask
 *     cd tester && bun install && bun run sloyfe
 */

const SPILLTJENER = process.env.SPILLTJENER_URL ?? "http://127.0.0.1:8080"

/*
 * `localhost` for alle tre, som i skallet. Paritetstesten bruker
 * `127.0.0.1` for to av dem, og det er nettopp det som skiller: kapselen
 * gjelder per vertsnavn, og det er den delte kapselen som gir én spiller i
 * tre utgaver.
 *
 * Astro står sist med vilje. Den henter siden på nytt ved hvert fasebytte
 * og melder utgaven igjen, så sto den først, kunne et fasebytte under de
 * neste lastingene gjort spilltjenerens svar til «astro» for en annen app.
 */
const APPER = [
  { navn: "tanstack", adresse: process.env.TANSTACK_URL ?? "http://localhost:8082" },
  { navn: "datastar", adresse: process.env.DATASTAR_URL ?? "http://localhost:8081" },
  { navn: "astro", adresse: process.env.ASTRO_URL ?? "http://localhost:8083" },
]

/** Hvor mange hendelser én utløser får lov til å gi før det er en sløyfe. */
const TAK = 10

const funn: string[] = []
const si = (melding: string) => funn.push(melding)

async function bliMed(navn: string): Promise<string> {
  const svar = await fetch(`${SPILLTJENER}/api/bli-med`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ navn, stack: "ukjent", erTest: true }),
  })
  return ((await svar.json()) as { spillerId: string }).spillerId
}

/** Utgaven spilltjeneren mener spilleren sitter i akkurat nå. */
async function utgave(spillerId: string): Promise<string | undefined> {
  const svar = await fetch(`${SPILLTJENER}/api/tilstand?spiller=${spillerId}`)
  const t = (await svar.json()) as { tavle: { erMeg: boolean; stack: string }[] }
  return t.tavle.find((rad) => rad.erMeg)?.stack
}

async function gaAv(spillerId: string) {
  await fetch(`${SPILLTJENER}/api/ga-av`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ spillerId }),
  }).catch(() => {})
}

/* --- Ledningen ---------------------------------------------------- */

let hendelser = 0
const stopp = new AbortController()

/** Teller hendelsene spilltjeneren sender, slik en app ville sett dem. */
async function lytt() {
  const svar = await fetch(`${SPILLTJENER}/api/hendelser`, { signal: stopp.signal })
  const leser = svar.body?.getReader()
  if (!leser) throw new Error("Ingen strøm fra spilltjeneren")
  const dekoder = new TextDecoder()
  let rest = ""
  for (;;) {
    const { done, value } = await leser.read()
    if (done) break
    rest += dekoder.decode(value, { stream: true })
    const linjer = rest.split("\n")
    rest = linjer.pop() ?? ""
    for (const linje of linjer) if (linje.startsWith("data:")) hendelser++
  }
}

const vent = (ms: number) => new Promise((f) => setTimeout(f, ms))

/* --- Løpet -------------------------------------------------------- */

const spiller = await bliMed("Test sløyfe")
void lytt().catch((e) => {
  if (!stopp.signal.aborted) si(`mistet strømmen fra spilltjeneren: ${(e as Error).message}`)
})

// Tellingen begynner når strømmen står: tilkoblingen selv gir et par
// hendelser, og de skal ikke telle som at noen sa fra om utgaven.
for (let i = 0; i < 50 && hendelser === 0; i++) await vent(100)
if (hendelser === 0) si("strømmen fra spilltjeneren kom aldri i gang")
await vent(500)
hendelser = 0

const nettleser = await chromium.launch()
const kontekst = await nettleser.newContext()
await kontekst.addCookies([
  { name: "spiller", value: spiller, domain: "localhost", path: "/" },
  { name: "forstelinja-test", value: "1", domain: "localhost", path: "/" },
])

let utloser: string | null = null
let etter = 0
try {
  /*
   * Alle tre som samme spiller, og alle tre skal ha brettet framme før
   * utløseren kommer: da holder hver app en strøm og henter for hver
   * hendelse.
   *
   * Brettet alene er ikke nok. Alle tre tegner tavla også for en anonym
   * leser, ved siden av påmeldingsskjemaet, og navnet står på tavla for
   * alle. Når ikke kapselen fram, er spilleren ingen, ingen flyttes, og
   * testen ville gått grønn med selve feilen i koden. Derfor to sjekker per
   * lasting: det brukeren ser når hun er spilleren, altså brettet uten
   * påmeldingsknappen, og det spilltjeneren mener etterpå, at hun sitter i
   * utgaven som nettopp lastet. Den siste er beviset på at `stack` ble
   * sendt, og den rokkes ikke av et fasebytte underveis.
   */
  for (const app of APPER) {
    const side = await kontekst.newPage()
    side.setDefaultTimeout(20_000)
    await side.goto(app.adresse, { waitUntil: "domcontentloaded" })
    await side
      .locator("#tavle")
      .waitFor()
      .catch(() => si(`${app.navn}: brettet kom aldri fram`))
    if (await side.getByRole("button", { name: "Begynn vakta" }).isVisible()) {
      si(`${app.navn}: siden ber om påmelding, så kapselen nådde ikke fram`)
    }
    const sitterI = await utgave(spiller)
    if (sitterI !== app.navn) {
      si(`${app.navn}: etter lastingen mener spilltjeneren at spilleren sitter i «${sitterI}». Sa ikke appen fra om utgaven?`)
    }
  }

  // La sidelastingene få gjort seg ferdig. Hver av dem sier fra om utgaven,
  // og det er tillatt: det er én hendelse per lasting, ikke én per hendelse.
  await vent(2000)

  const for_ = hendelser
  utloser = await bliMed("Test utløser")
  await vent(3000)
  etter = hendelser - for_

  if (etter > TAK) {
    si(`én påmelding ga ${etter} hendelser fra spilltjeneren på tre sekunder. Det er en sløyfe`)
  } else if (etter === 0) {
    si("påmeldingen ga ingen hendelse i det hele tatt. Strømmen er ikke i live")
  }
} finally {
  stopp.abort()
  await gaAv(spiller)
  if (utloser) await gaAv(utloser)
  await kontekst.close()
  await nettleser.close()
}

if (funn.length > 0) {
  console.error(`Fant ${funn.length} avvik:\n${funn.map((f) => `  - ${f}`).join("\n")}`)
  process.exit(1)
}
console.log(
  `Én utløser ga ${etter} hendelser i vinduet på tre sekunder, og strømmen ble stille igjen.`,
)
