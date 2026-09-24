import { chromium, firefox, webkit } from "playwright"

/**
 * At hilsenen ikke legger seg over rammene i skallet.
 *
 * Skallet viser de tre utgavene side om side. Velkomsthilsenen er en
 * presentasjon av nettopp de tre, med en lenke til hver, så i skallet dekker
 * den det den skulle fortalt om: tre modaler, én per ramme, over det
 * publikum er der for å se.
 *
 * Signalet er nettleserens eget, `Sec-Fetch-Dest: iframe`, og ikke en egen
 * adresse fra skallet. Derfor kjører denne testen i alle tre motorene: den
 * påstanden er en påstand om nettleserne, ikke om appene våre, og en motor
 * som slutter å sende overskriften ville gitt tre dialoger i drift uten at
 * noe sa fra.
 *
 * Appene og skallet må kjøre først:
 *
 *     ./kjor.sh rask
 *     cd tester && bun run skall
 */

const SKALL = process.env.SKALL_URL ?? "http://127.0.0.1:8084"
const funn: string[] = []

for (const [navn, motor] of [
  ["chromium", chromium],
  ["firefox", firefox],
  ["webkit", webkit],
] as const) {
  const nettleser = await motor.launch()
  try {
    const kontekst = await nettleser.newContext({
      viewport: { width: 1600, height: 1000 },
    })
    const side = await kontekst.newPage()
    await side.goto(SKALL, { waitUntil: "domcontentloaded", timeout: 45000 })

    /*
     * Adressene leses fra skallets egne `iframe[src]`, ikke fra `frame.url()`.
     *
     * Ved `domcontentloaded` finnes alle tre rammene, men `url()` er tom
     * streng i alle tre motorene. `new URL("")` kaster da, og testen døde med
     * «Invalid URL» framfor å skrive ut funnene sine.
     */
    const adresser = await side
      .locator("iframe")
      .evaluateAll((rammer) => rammer.map((r) => r.getAttribute("src") ?? ""))
    if (adresser.length < 3) {
      funn.push(`${navn}: fant ${adresser.length} rammer, ventet tre`)
      await kontekst.close()
      continue
    }

    for (const [nr, adresse] of adresser.entries()) {
      // Indeks og ikke selektor på `src`: attributtet og den oppløste
      // adressen er ikke alltid like, og en skråstrek til eller fra gjorde at
      // selektoren ikke traff noe.
      const ramme = side.frameLocator("iframe").nth(nr)

      // Vent på at brettet står der, ikke på klokka. Uten det kan testen lese
      // en ramme som ikke er ferdig, og melde grønt fordi ingenting er tegnet.
      await ramme
        .locator("#brett, .blimed")
        .first()
        .waitFor({ timeout: 30000 })
        .catch(() => funn.push(`${navn}: ${vert(adresse)} ble aldri ferdig`))

      /*
       * `:modal` og ikke bare at elementet finnes, slik nabotestene gjør.
       * I TanStack rendres verten med dialogen lukket i én tilstand, og da
       * ser ikke brukeren noe, selv om `#velkomst` står i DOM-en.
       */
      const synlig = await ramme
        .locator("#velkomst dialog")
        .first()
        .evaluate((d) => (d as HTMLDialogElement).matches(":modal"))
        .catch(() => false)
      if (synlig) funn.push(`${navn}: hilsenen står i ${vert(adresse)}`)
    }

    /*
     * Og alene er den der fortsatt, i alle tre. Uten denne halvdelen ville
     * testen vært grønn også om hilsenen forsvant helt.
     *
     * Egen kontekst per app: Astro-utgaven husker i en kapsel at hilsenen er
     * sett, og skallet ville ellers ha satt den.
     */
    for (const adresse of adresser) {
      const egen = await nettleser.newContext()
      const alene = await egen.newPage()
      await alene.goto(adresse, {
        waitUntil: "domcontentloaded",
        timeout: 45000,
      })
      await alene
        .locator("#velkomst dialog")
        .waitFor({ timeout: 30000 })
        .catch(() =>
          funn.push(
            `${navn}: hilsenen mangler når ${vert(adresse)} åpnes alene`,
          ),
        )
      await egen.close()
    }

    await kontekst.close()
  } finally {
    await nettleser.close()
  }
}

/** Verten alene, så meldingene blir korte og ikke kaster på en tom adresse. */
function vert(adresse: string): string {
  try {
    return new URL(adresse).host
  } catch {
    return adresse || "(uten adresse)"
  }
}

if (funn.length > 0) {
  console.error(
    `Fant ${funn.length} avvik:\n${funn.map((f) => `  - ${f}`).join("\n")}`,
  )
  process.exit(1)
}
console.log("Skallet viser tre brett, ikke tre dialoger.")
