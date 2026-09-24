import { chromium } from "playwright"

/**
 * At de tre utgavene bruker de samme Fristil-klassene.
 *
 * `paritet.ts` sjekker at de oppfører seg likt: roller, ledetekster og at
 * knappene virker. Den så ikke at TanStack-temavelgeren mistet
 * `fs-toggle-group` og ble en kolonne uten ramme, for den fant fortsatt tre
 * knapper med riktig tekst. Skjermen var synlig ødelagt, og alle tre
 * vaktpostene var grønne.
 *
 * Denne teller klassene i DOM-en i stedet. En klasse som finnes i én utgave
 * og ikke i de to andre er enten en feil eller en bevisst forskjell, og en
 * bevisst forskjell i et demospill som handler om at de er like, finnes ikke.
 *
 *     ./kjor.sh rask
 *     cd tester && bun run klasselikhet
 */

const APPER = [
  {
    navn: "datastar",
    url: process.env.DATASTAR_URL ?? "http://127.0.0.1:8081",
  },
  {
    navn: "tanstack",
    url: process.env.TANSTACK_URL ?? "http://localhost:8082",
  },
  { navn: "astro", url: process.env.ASTRO_URL ?? "http://127.0.0.1:8083" },
]

const funn: string[] = []
const nettleser = await chromium.launch()

try {
  const sett = new Map<string, Set<string>>()

  for (const app of APPER) {
    const kontekst = await nettleser.newContext()
    await kontekst.addCookies([
      { name: "forstelinja-test", value: "1", url: app.url },
    ])
    const side = await kontekst.newPage()
    await side.goto(app.url, { waitUntil: "domcontentloaded", timeout: 45000 })

    // Vent på at brettet står der, ikke på klokka.
    await side
      .locator("#brett, .blimed")
      .first()
      .waitFor({ timeout: 30000 })
      .catch(() => funn.push(`${app.navn}: brettet kom aldri`))

    const klasser = await side.evaluate(() => {
      const ut = new Set<string>()
      for (const el of document.querySelectorAll("[class]"))
        for (const k of el.classList) if (k.startsWith("fs-")) ut.add(k)
      return [...ut]
    })
    sett.set(app.navn, new Set(klasser))
    await kontekst.close()
  }

  const alle = new Set([...sett.values()].flatMap((s) => [...s]))
  for (const klasse of [...alle].sort()) {
    const har = APPER.filter((a) => sett.get(a.navn)?.has(klasse)).map(
      (a) => a.navn,
    )
    if (har.length !== APPER.length)
      funn.push(
        `.${klasse} finnes bare i ${har.join(", ")}, ikke i ${APPER.map(
          (a) => a.navn,
        )
          .filter((n) => !har.includes(n))
          .join(", ")}`,
      )
  }
} finally {
  await nettleser.close()
}

if (funn.length > 0) {
  console.error(
    `Fant ${funn.length} avvik:\n${funn.map((f) => `  - ${f}`).join("\n")}`,
  )
  process.exit(1)
}
console.log("De tre utgavene bruker de samme Fristil-klassene.")
