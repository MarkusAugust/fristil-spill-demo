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

    const rammer = side.frames().filter((f) => f !== side.mainFrame())
    if (rammer.length < 3) {
      funn.push(`${navn}: fant ${rammer.length} rammer, ventet tre`)
      await kontekst.close()
      continue
    }

    for (const ramme of rammer) {
      /*
       * Vent på at brettet står der, ikke på klokka. Uten det kan testen
       * lese en ramme som ikke er ferdig, og melde grønt fordi ingenting er
       * tegnet ennå.
       */
      await ramme
        .locator("#brett, .blimed")
        .first()
        .waitFor({ timeout: 30000 })
        .catch(() => funn.push(`${navn}: en ramme ble aldri ferdig`))

      const antall = await ramme.locator("#velkomst").count()
      if (antall > 0)
        funn.push(`${navn}: hilsenen står i ${new URL(ramme.url()).host}`)
    }

    // Og alene er den der fortsatt. Ellers ville testen vært grønn også om
    // hilsenen forsvant helt.
    const alene = await kontekst.newPage()
    const adresse = new URL(rammer[0].url()).origin
    await alene.goto(adresse, {
      waitUntil: "domcontentloaded",
      timeout: 45000,
    })
    await alene
      .locator("#velkomst dialog")
      .waitFor({ timeout: 30000 })
      .catch(() =>
        funn.push(`${navn}: hilsenen mangler når ${adresse} åpnes alene`),
      )

    await kontekst.close()
  } finally {
    await nettleser.close()
  }
}

if (funn.length > 0) {
  console.error(
    `Fant ${funn.length} avvik:\n${funn.map((f) => `  - ${f}`).join("\n")}`,
  )
  process.exit(1)
}
console.log("Skallet viser tre brett, ikke tre dialoger.")
