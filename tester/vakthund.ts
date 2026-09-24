import { chromium } from "playwright"

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
const si = (melding: string) => funn.push(melding)

const nettleser = await chromium.launch()

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
  await side.waitForTimeout((sekunderIgjen + 25) * 1000)

  if (lastinger <= forLastinger) {
    const overtid = Math.round((Date.now() - frist) / 1000)
    si(
      `siden hentet seg ikke inn igjen. Fristen gikk ut for ${overtid} sekunder siden, og vakthunden venter 15.`,
    )
  }

  await side
    .locator("#brett")
    .waitFor({ timeout: 20000 })
    .catch(() => si("siden ble hentet på nytt, men brettet kom ikke tilbake"))

  await kontekst.close()
} finally {
  await nettleser.close()
}

if (funn.length > 0) {
  console.error(`Fant ${funn.length} avvik:\n${funn.map((f) => `  - ${f}`).join("\n")}`)
  process.exit(1)
}
console.log("Datastar-utgaven henter seg inn igjen når strømmen dør.")
