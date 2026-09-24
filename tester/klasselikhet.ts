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
 * **Den spiller en hel runde.** Første utgave leste bare forsiden, og så
 * dermed 19 av 36 klasser: skjemaet, kvitteringen og oppgjøret kommer først
 * etter at du har meldt deg på og fattet et vedtak. Halvparten av systemet
 * var altså utenfor den vaktposten som skulle se hele det.
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

/*
 * Klassene samles mens siden lever, ikke i et øyeblikksbilde.
 *
 * Kvitteringen er en modal som lukkes igjen, forslagslista tegnes bare mens
 * feltet har fokus, og oppgjøret varer i tolv sekunder. Et oppslag til slutt
 * ville sett ingen av dem. Observatøren legges inn før noe skript på siden
 * kjører, og husker hver `fs-`-klasse den har sett.
 */
const SAMLER = `
  window.__fsKlasser = new Set()
  const se = (node) => {
    if (!(node instanceof Element)) return
    for (const el of [node, ...node.querySelectorAll("[class]")])
      for (const k of el.classList) if (k.startsWith("fs-")) window.__fsKlasser.add(k)
  }
  // Observatøren settes på \`document\`, ikke på \`document.documentElement\`.
  // Skriptet kjører før siden er parset, og da finnes rotelementet ikke ennå:
  // \`observe\` kastet «parameter 1 is not of type Node», hele skriptet stoppet,
  // og samlingen sto tom mens sjekken meldte grønt på null klasser.
  new MutationObserver((poster) => {
    for (const post of poster) {
      if (post.type === "attributes") se(post.target)
      else for (const node of post.addedNodes) se(node)
    }
  }).observe(document, {
    subtree: true,
    childList: true,
    attributes: true,
    attributeFilter: ["class"],
  })
  document.addEventListener("DOMContentLoaded", () => se(document.documentElement))
`

const funn: string[] = []
const nettleser = await chromium.launch()
const sett = new Map<string, Set<string>>()
const fullfort = new Set<string>()

try {
  for (const app of APPER) {
    const si = (melding: string) => funn.push(`${app.navn}: ${melding}`)
    const kontekst = await nettleser.newContext()

    // Som i paritetstesten: en kapsel, så testspilleren ikke havner i den
    // evige topplista.
    await kontekst.addCookies([
      { name: "forstelinja-test", value: "1", url: app.url },
    ])

    const side = await kontekst.newPage()
    await side.addInitScript(SAMLER)
    side.setDefaultTimeout(20_000)

    const klasser = new Set<string>()
    /** Henter det observatøren har sett så langt. Astro laster på nytt, og da
     *  begynner den forfra, så dette gjøres ved hvert steg. */
    const samle = async () => {
      for (const k of await side
        .evaluate(() => [...((window as never as { __fsKlasser?: Set<string> }).__fsKlasser ?? [])])
        .catch(() => [] as string[]))
        klasser.add(k)
    }

    /*
     * En oppvarming først. Vite optimaliserer avhengighetene sine ved første
     * forespørsel og svarer «504 Outdated Optimize Dep» mens den holder på,
     * så den aller første lastingen feiler i React-utgaven.
     */
    await side.goto(app.url, { waitUntil: "domcontentloaded" }).catch(() => {})
    await side.waitForTimeout(1500)

    try {
      await side.goto(app.url, { waitUntil: "domcontentloaded" })

      // Velkomsthilsenen er modal først når komponenten har gjort den modal.
      await side.waitForFunction(
        () => document.querySelector("#velkomst dialog")?.matches(":modal") === true,
        undefined,
        { timeout: 15_000 },
      )
      await samle()
      await side.getByRole("button", { name: "Jeg merker nok forskjellen" }).click()

      await side.getByLabel("Navnet ditt").fill(`Test ${app.navn}`)
      await side.getByRole("button", { name: "Begynn vakta" }).click()
      await side.locator("#tavle").waitFor({ timeout: 15_000 })
      await samle()

      const kvittering = side.locator("dialog.resultat[open]")
      const lukkKvittering = async () => {
        if ((await kvittering.count()) === 0) return
        await samle()
        await kvittering
          .getByRole("button", { name: "Lukk" })
          .first()
          .click()
          .catch(() => {})
        await kvittering.waitFor({ state: "detached", timeout: 5000 }).catch(() => {})
      }
      await lukkKvittering()

      /*
       * Panelet bygges av et skript, i React fra en effekt etter hydreringen,
       * så det kommer et øyeblikk etter tavla. Det åpnes ikke her: hele
       * markupen står i DOM-en fra den blir bygget, og `hidden` skjuler den
       * bare. At panelet lar seg åpne er `paritet.ts` sin jobb, og et klikk
       * her feilet tilfeldig når en kvittering lå over knappen.
       */
      await side.locator(".panelknapp").first().waitFor({ timeout: 20_000 })
      await samle()

      // Skjemaet finnes bare mens en runde går. Testen kan lande i et oppgjør.
      await side.locator("#hjemmel").waitFor({ timeout: 90_000 })
      await lukkKvittering()

      // Forslagslista tegner treffene sine mens feltet har fokus.
      await side.locator("#kommune").click()
      await side.locator("#kommune").fill("Inder")
      await side
        .waitForFunction(
          () => {
            const liste = document.getElementById("kommune-list")
            return liste && !liste.hidden
              ? [...liste.querySelectorAll<HTMLElement>("[role='option']")].some((v) => !v.hidden)
              : false
          },
          undefined,
          { timeout: 10_000 },
        )
        .catch(() => si("forslagslista viste ingen treff på «Inder»"))
      await samle()

      // Og så et helt vedtak, som gir kvitteringen.
      await side.locator("#v-innvilget").check()
      await side.selectOption("#hjemmel", { index: 1 })
      await side.locator("#kommune").fill("Inderøy")
      await side.selectOption("#felle", "nei")
      await side.getByRole("button", { name: "Fatt vedtak" }).click()
      await side.getByText("Vedtaket er fattet").waitFor({ timeout: 15_000 })
      await samle()

      fullfort.add(app.navn)
    } catch (e) {
      /*
       * Et løp som stoppet halvveis skal si nettopp det, og ikke bli til
       * atten «klassen finnes bare i de to andre». Sammenligningen hopper
       * over appen, og funnet peker på det som faktisk gikk galt.
       */
      const melding = (e as Error).message
        .split("\n")
        .map((l) => l.trim())
        .filter(Boolean)
        .slice(0, 8)
        .join(" | ")
      si(`kom ikke gjennom løpet: ${melding}`)
      await side.screenshot({ path: `${app.navn}-klasselikhet.png` }).catch(() => {})
    }

    await samle()
    sett.set(app.navn, klasser)

    // Og så av vakt igjen, så testspilleren ikke blir stående på tavla.
    await side
      .evaluate(() => fetch("/ga-av", { method: "POST" }).then(() => undefined))
      .catch(() => {})
    await kontekst.close()
  }
} finally {
  await nettleser.close()
}

const sammenlignes = APPER.filter((a) => fullfort.has(a.navn))

/*
 * Et gulv, så en tom samling ikke kan melde grønt.
 *
 * Observatøren sto en stund på et rotelement som ikke fantes ennå, og da
 * kastet den. Sjekken fant null klasser i alle tre, alle tre var like, og
 * den skrev «De tre utgavene bruker de samme 0 Fristil-klassene». En
 * vaktpost som ikke kan feile sier ingenting.
 */
const GULV = 30
for (const app of sammenlignes) {
  const antall = sett.get(app.navn)?.size ?? 0
  if (antall < GULV)
    funn.push(
      `${app.navn}: samlet bare ${antall} Fristil-klasser gjennom hele løpet, og det er for få til at sjekken sier noe`,
    )
}

if (sammenlignes.length < APPER.length) {
  funn.push(
    `bare ${sammenlignes.length} av ${APPER.length} utgaver kom gjennom løpet, så klassene kan ikke sammenlignes`,
  )
} else {
  const alle = new Set([...sett.values()].flatMap((s) => [...s]))
  for (const klasse of [...alle].sort()) {
    const har = sammenlignes.filter((a) => sett.get(a.navn)?.has(klasse)).map((a) => a.navn)
    if (har.length !== sammenlignes.length)
      funn.push(
        `.${klasse} finnes bare i ${har.join(", ")}, ikke i ${sammenlignes
          .map((a) => a.navn)
          .filter((n) => !har.includes(n))
          .join(", ")}`,
      )
  }
}

if (funn.length > 0) {
  console.error(`Fant ${funn.length} avvik:\n${funn.map((f) => `  - ${f}`).join("\n")}`)
  process.exit(1)
}
console.log(
  `De tre utgavene bruker de samme ${sett.get(APPER[0].navn)?.size ?? 0} Fristil-klassene.`,
)
