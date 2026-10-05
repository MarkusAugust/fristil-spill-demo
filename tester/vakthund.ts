import { type BrowserType, chromium, webkit } from "playwright"

/**
 * At Datastar-utgaven kommer seg etter en strøm som dør i stillhet.
 *
 * Dette er feilen som ser ut som at spillet har stoppet: klokka teller til
 * null, og så skjer ingenting. Den ble sett på en telefon i drift, og den er
 * ikke en feil i serveren, som tikker videre fire ganger i sekundet.
 *
 * Datastar henter hendelsesstrømmen med `fetch` og leser den som en strøm.
 * Den kobler til igjen bare når lesingen kaster, og en forbindelse som blir
 * borte uten et ord kaster aldri. Sover telefonen, bytter nettet, eller
 * kutter en mellomtjener forbindelsen, står siden igjen for alltid.
 *
 * De to andre utgavene har hver sin vei ut, og trenger derfor ikke denne
 * testen: `EventSource` kobler til igjen av seg selv, og Astro-utgaven henter
 * en ny side når runden er en annen enn den siden ble tegnet med.
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

// `allSettled`: kaster den ene, skal funnene fra den andre fortsatt komme
// fram, og begge nettleserne lukkes.
const utfall = await Promise.allSettled([sjekk("Chromium", chromium), sjekk("WebKit", webkit)])
for (const [i, u] of utfall.entries()) {
  if (u.status === "rejected") funn.push(`${["Chromium", "WebKit"][i]}: testen stoppet. ${u.reason}`)
}

if (funn.length > 0) {
  console.error(`Fant ${funn.length} avvik:\n${funn.map((f) => `  - ${f}`).join("\n")}`)
  process.exit(1)
}
console.log("Datastar-utgaven henter seg inn igjen når strømmen dør.")
