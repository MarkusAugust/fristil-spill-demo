import { type BrowserType, chromium, webkit } from "playwright"

/**
 * At Datastar-utgaven kommer seg etter en strøm som dør i stillhet.
 *
 * Dette er feilen som ser ut som at spillet har stoppet: klokka teller til
 * null, og så skjer ingenting. Den ble sett på en telefon i drift, og den er
 * ikke en feil i serveren, som tikker videre fire ganger i sekundet.
 *
 * Datastar henter hendelsesstrømmen med `fetch` og leser den som en strøm.
 * Den kobler til igjen når lesingen kaster, og når strømmen slutter pent,
 * men en forbindelse som blir borte uten et ord gjør ingen av delene. Bytter
 * nettet, eller kutter en mellomtjener forbindelsen uten å si fra, står
 * siden igjen for alltid. En side som skjules er noe annet: den strømmen
 * lukker og åpner Datastar selv.
 *
 * De to andre utgavene har hver sin vei ut, og trenger derfor ikke denne
 * testen: `EventSource` kobler til igjen av seg selv, og Astro-utgaven henter
 * en ny side når runden er en annen enn den siden ble tegnet med.
 *
 * Tre sjekker, i begge motorene:
 *
 * - at siden henter seg inn igjen når strømmen er borte uten et ord, med
 *   vakthunden i siden,
 * - at en strøm som slutter pent blir åpnet igjen med en gang, uten
 *   vakthunden. En forbindelse som blir gitt opp underveis slutter slik:
 *   ikke med en feil, men med et svar som bare er ferdig,
 * - at strømmen aldri er stille lenge. Et hjerteslag hvert femtende sekund
 *   er det som hindrer at den slutter i det hele tatt.
 *
 * Appen må kjøre i rask modus, ellers venter testen i to minutter på at en
 * runde skal gå ut:
 *
 *     ./kjor.sh rask
 *     cd tester && bun run vakthund
 */

const url = process.env.DATASTAR_URL ?? "http://127.0.0.1:8081"

const funn: string[] = []

/*
 * Begge motorene, fordi stormen bare viser seg i den ene. Chromium lar en
 * ny `reload()` vente på lastingen som alt er i gang, mens WebKit avbryter
 * den og begynner på nytt. Det var WebKit, på en iPhone, som sto og blinket.
 */
async function sjekk(navn: string, motor: BrowserType) {
  const si = (melding: string) => funn.push(`${navn}: ${melding}`)
  const nettleser = await motor.launch()

  try {
    const kontekst = await nettleser.newContext()
    await kontekst.addCookies([{ name: "forstelinja-test", value: "1", url }])
    const side = await kontekst.newPage()

    /*
     * Hendelsesstrømmen finnes ikke i det hele tatt.
     *
     * Det er den harde utgaven av den samme feilen: serveren tegner siden med
     * en frist i seg, klokka teller ned, og ingen patch kommer noensinne. Uten
     * vakthunden blir siden stående slik til noen laster den selv.
     */
    await side.route("**/hendelser*", (rute) => rute.abort("connectionclosed"))

    let lastinger = 0
    side.on("load", () => {
      lastinger += 1
    })

    /*
     * Hver gang siden hentes, og et mobilnett å hente den over.
     *
     * Vakthunden kalte `location.reload()` på hvert tikk, og hvert kall avbrøt
     * lastingen fra det forrige. Lokalt kommer siden på millisekunder, og da
     * så det ut som én lasting. På telefonen i drift ble det fire i sekundet,
     * og siden kom aldri fram. To sekunders forsinkelse gir stormen tid til å
     * vise seg her også.
     */
    const hentinger: number[] = []
    let forsink = false
    await side.route(
      (adresse) => adresse.pathname === "/",
      async (rute) => {
        if (rute.request().isNavigationRequest()) hentinger.push(Date.now())
        if (forsink) await new Promise((ferdig) => setTimeout(ferdig, 2000))
        await rute.continue().catch(() => {})
      },
    )

    await side.goto(url, { waitUntil: "domcontentloaded" })
    await side.locator(".nedtelling[data-klokke]").waitFor({ timeout: 20000 })

    const frist = Number(
      (await side.locator(".nedtelling[data-klokke]").getAttribute("data-frist")) ?? 0,
    )
    const sekunderIgjen = Math.max(0, Math.round((frist - Date.now()) / 1000))

    /*
     * Vent til fristen har gått ut, og så nådetiden på femten sekunder, med
     * litt margin. Ventingen er på klokka her med vilje: det er nettopp klokka
     * vakthunden teller på.
     */
    const forLastinger = lastinger
    const forHentinger = hentinger.length
    forsink = true
    await side.waitForTimeout((sekunderIgjen + 25) * 1000)

    if (lastinger <= forLastinger) {
      const overtid = Math.round((Date.now() - frist) / 1000)
      si(
        `siden hentet seg ikke inn igjen. Fristen gikk ut for ${overtid} sekunder siden, og vakthunden venter 15.`,
      )
    }

    const nye = hentinger.slice(forHentinger)
    if (nye.length > 0) {
      // Varselet kommer femten sekunder etter at klokka viser null, og siden
      // hentes to sekunder senere. Klokka viser null et halvt sekund før
      // fristen, fordi tallet rundes. Grensen på fjorten er derfor godt under
      // det riktige, og godt over de fire sekundene tikk-tellingen ga.
      const etter = (nye[0] - frist) / 1000
      if (etter < 14) {
        si(
          `siden ble hentet ${etter.toFixed(1)} sekunder etter fristen. Vakthunden skal vente 15, og teller trolig tikk i stedet for sekunder.`,
        )
      }

      // Innenfor de to sekundene den første lastingen tar, skal det ikke komme
      // en til. Kommer det flere, avbryter hver av dem den forrige.
      const iStormen = nye.filter((t) => t - nye[0] < 2000).length
      if (iStormen > 1) {
        si(
          `siden ble hentet ${iStormen} ganger på to sekunder. Vakthunden skal hente den én gang, ikke avbryte sin egen lasting.`,
        )
      }
    }

    await side
      .locator("#brett")
      .waitFor({ timeout: 20000 })
      .catch(() => si("siden ble hentet på nytt, men brettet kom ikke tilbake"))

    await kontekst.close()
  } finally {
    await nettleser.close()
  }
}

/**
 * At en strøm som slutter pent blir åpnet igjen.
 *
 * Uten `retry` kobler Datastar til igjen bare når lesingen kaster. Et svar
 * som er ferdig, med status 200 og et pent avsluttet innhold, regnes som
 * nettopp det: ferdig. Det er en av måtene en strøm kan ta slutt på uten
 * at Datastar prøver igjen, og slik slutter en forbindelse en telefon eller
 * en mellomtjener har gitt opp. På en iPhone i drift sto klokka på null i
 * hver runde til vakthunden hentet siden femten sekunder senere, mens de to
 * andre utgavene gikk rett videre.
 *
 * Med `retry: 'always'` på `@get` er en slik slutt en grunn til å prøve
 * igjen, og serveren sender hele brettet når den nye strømmen åpnes.
 * Sjekken gir ett gyldig svar som slutter med en gang, og krever en ny
 * henting innen få sekunder. Datastar venter ett sekund før det første nye
 * forsøket.
 *
 * Og sambandslinja skal ikke bli stående på «nede» etterpå. Datastar sier
 * «retrying» når den prøver igjen, og «started» bare én gang per `@get`,
 * så uten at hver patch teller som et levende samband, sto linja på «nede»
 * over et brett som oppdaterte seg som normalt.
 */
async function sjekkGjenoppkobling(navn: string, motor: BrowserType) {
  const si = (melding: string) => funn.push(`${navn}: ${melding}`)
  const nettleser = await motor.launch()

  try {
    const kontekst = await nettleser.newContext()
    await kontekst.addCookies([{ name: "forstelinja-test", value: "1", url }])
    const side = await kontekst.newPage()

    const hentinger: number[] = []
    let sluttetVed = 0
    await side.route("**/hendelser*", async (rute) => {
      hentinger.push(Date.now())
      if (hentinger.length > 1) {
        await rute.continue().catch(() => {})
        return
      }
      // Første gang: ett gyldig svar, og så er serveren ferdig. Ingen feil,
      // bare slutt.
      //
      // Men ikke før sambandslinja er registrert. Komponentene kommer fra
      // CDN i et modulskript, og slutter strømmen før de er oppe, har siden
      // ingen linje å sette på «nede», og sjekken av den sier ingenting.
      // I drift slutter strømmen minutter etter lastingen, og da står linja
      // der.
      await side
        .waitForFunction(
          () =>
            typeof (document.querySelector("fs-connection-status") as { reportFailure?: unknown } | null)
              ?.reportFailure === "function",
          undefined,
          { timeout: 15000 },
        )
        .catch(() => si("sambandslinja kom aldri opp på femten sekunder, så sjekken av den sier ingenting."))
      await rute.fulfill({
        status: 200,
        contentType: "text/event-stream",
        body: 'event: datastar-patch-elements\ndata: elements <div id="samband" hidden data-nede="false"></div>\n\n',
      })
      sluttetVed = Date.now()
    })

    await side.goto(url, { waitUntil: "domcontentloaded" })
    await side.locator(".nedtelling[data-klokke]").waitFor({ timeout: 20000 })

    // Seks sekunder fra strømmen sluttet, ikke fra siden kom: Datastar og
    // Fristil hentes fra CDN i en kald nettleser, og det er ikke appens
    // ventetid. Tretti sekunder er grensen for hele løpet.
    const start = Date.now()
    while (hentinger.length < 2 && Date.now() - start < 30000) {
      if (sluttetVed > 0 && Date.now() - sluttetVed > 6000) break
      await side.waitForTimeout(250)
    }
    if (hentinger.length < 2) {
      si(
        sluttetVed > 0
          ? "strømmen sluttet pent, og siden åpnet den ikke igjen på seks sekunder. Da står klokka på null til vakthunden tar siden."
          : "siden åpnet aldri strømmen på tretti sekunder.",
      )
      return
    }

    // Den nye strømmen svarer med hele brettet med en gang. Et sekund er
    // rikelig lokalt, og linja skal da ikke lenger si «nede». Det er
    // teksten brukeren ser som sjekkes, ikke komponentens attributt.
    await side.waitForTimeout(1500)
    const nede = await side
      .getByRole("status")
      .filter({ hasText: "Sambandet til etaten er nede" })
      .count()
    if (nede > 0) {
      si("sambandslinja står på «nede» etter at strømmen var tilbake og brettet kom. En patch som kommer fram skal telle som et levende samband.")
    }

    await kontekst.close()
  } finally {
    await nettleser.close()
  }
}

/**
 * At strømmen aldri er stille lenge.
 *
 * Pulsen fra spilltjeneren slår bare når noe skjer, og sitter én spiller
 * alene i en runde på to minutter, skjer det ingenting. Alt mellom serveren
 * og telefonen kan gi opp en forbindelse som har stått stille lenge nok,
 * og en telefon gjør det selv. Et hjerteslag hvert femtende sekund, en
 * kommentarlinje Datastar hopper over, er det som holder strømmen åpen.
 *
 * Sjekken åpner strømmen med `fetch` fra siden selv og krever en linje som
 * begynner med kolon innen tjue sekunder. Patcher teller ikke: i rask modus
 * kommer det en for hvert faseskifte, og den sier ingenting om stillheten
 * imellom.
 */
async function sjekkHjerteslag(navn: string, motor: BrowserType) {
  const si = (melding: string) => funn.push(`${navn}: ${melding}`)
  const nettleser = await motor.launch()

  try {
    const kontekst = await nettleser.newContext()
    await kontekst.addCookies([{ name: "forstelinja-test", value: "1", url }])
    const side = await kontekst.newPage()
    await side.goto(url, { waitUntil: "domcontentloaded" })

    const etter = await side.evaluate(async () => {
      const FRIST_MS = 20000
      const start = performance.now()
      const svar = await fetch("/hendelser", { headers: { Accept: "text/event-stream" } })
      const leser = svar.body?.getReader()
      if (!leser) return null
      const dekoder = new TextDecoder()
      let tekst = ""
      try {
        while (performance.now() - start < FRIST_MS) {
          const igjen = FRIST_MS - (performance.now() - start)
          const bit = await Promise.race([
            leser.read(),
            new Promise<{ done: true; value: undefined }>((ferdig) =>
              setTimeout(() => ferdig({ done: true, value: undefined }), igjen),
            ),
          ])
          if (bit.done) break
          tekst += dekoder.decode(bit.value, { stream: true })
          // En kommentar er en linje som begynner med kolon. Innholdet i en
          // patch begynner alltid med `data:` eller `event:`.
          if (/(^|\n):/.test(tekst)) return (performance.now() - start) / 1000
        }
      } finally {
        await leser.cancel().catch(() => {})
      }
      return null
    })

    if (etter === null) {
      si("strømmen sto stille i tjue sekunder uten et hjerteslag. Et hjerteslag hvert femtende sekund er det som holder den åpen på en telefon.")
    }

    await kontekst.close()
  } finally {
    await nettleser.close()
  }
}

// `allSettled`: kaster den ene, skal funnene fra de andre fortsatt komme
// fram, og alle nettleserne lukkes.
const motorer: [string, BrowserType][] = [
  ["Chromium", chromium],
  ["WebKit", webkit],
]
const sjekker = motorer.flatMap(([navn, motor]) => [
  [`${navn}, strøm uten et ord`, sjekk(navn, motor)] as const,
  [`${navn}, strøm som slutter pent`, sjekkGjenoppkobling(navn, motor)] as const,
  [`${navn}, hjerteslag`, sjekkHjerteslag(navn, motor)] as const,
])
const utfall = await Promise.allSettled(sjekker.map(([, s]) => s))
for (const [i, u] of utfall.entries()) {
  if (u.status === "rejected") funn.push(`${sjekker[i][0]}: testen stoppet. ${u.reason}`)
}

if (funn.length > 0) {
  console.error(`Fant ${funn.length} avvik:\n${funn.map((f) => `  - ${f}`).join("\n")}`)
  process.exit(1)
}
console.log("Datastar-utgaven holder strømmen i live, åpner den igjen når den slutter, og henter seg inn igjen når den dør.")
