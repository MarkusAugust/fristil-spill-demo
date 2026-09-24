import { chromium } from "playwright"

/**
 * At panelet forteller sant om hva som kom over ledningen.
 *
 * Panelet er demoens eneste måte å vise forskjellen mellom de tre utgavene
 * på, og en feil her ser ikke ut som en feil: ruta sier «Ingenting har kommet
 * over ledningen ennå», og det er en fullt troverdig setning. Tre ekte feil
 * levde i den setningen samtidig, og alle tre feller denne testen:
 *
 * 1. Datastar-utgaven viste aldri noe. Ktor avslutter linjene i
 *    hendelsesstrømmen med CR LF, og parseren delte bare på to LF, så den
 *    fant ikke en eneste ramme i den utgaven som sender mest.
 * 2. Astro-utgaven fanget aldri strømmen sin, fordi panelet la seg utenpå
 *    `EventSource` etter at øya hadde åpnet den. Avlyttingen måtte ut i et
 *    vanlig skript i `<head>`, som kjører før hvert modulskript.
 * 3. Markeringen av det som nettopp ble byttet ut tegnet ingenting, fordi
 *    `--semantic-info-foreground` ikke finnes i Fristil og en ugyldig `var()`
 *    gjør hele regelen ugyldig.
 *
 * Appene må kjøre først:
 *
 *     ./kjor.sh rask
 *     cd tester && bun install && bun run panel
 */

const APPER = [
  {
    navn: "datastar",
    url: process.env.DATASTAR_URL ?? "http://127.0.0.1:8081",
    /** Hva denne utgaven skal vise seg å sende. Se merkelappen i panelet. */
    venter: {
      transport: "SSE",
      format: "HTML",
      hendelse: "datastar-patch-elements",
    },
    rad: "Datastar",
  },
  {
    navn: "tanstack",
    url: process.env.TANSTACK_URL ?? "http://localhost:8082",
    venter: { transport: "SSE", format: "JSON", hendelse: "tilstand" },
    rad: "TanStack Start",
  },
  {
    navn: "astro",
    url: process.env.ASTRO_URL ?? "http://127.0.0.1:8083",
    venter: { transport: "SSE", format: "JSON", hendelse: "puls" },
    rad: "Astro",
  },
]

const funn: string[] = []

const nettleser = await chromium.launch()

try {
  for (const app of APPER) {
    const si = (melding: string) => funn.push(`${app.navn}: ${melding}`)
    const kontekst = await nettleser.newContext()
    await kontekst.addCookies([
      { name: "forstelinja-test", value: "1", url: app.url },
    ])
    const side = await kontekst.newPage()

    await side.goto(app.url, { waitUntil: "domcontentloaded" })

    /*
     * Avlyttingen skal være på plass før appen åpner strømmen sin, og det er
     * hele grunnen til at den er et vanlig skript i `<head>`. Finnes den ikke i
     * det siden er lastet, er rekkefølgen brutt igjen.
     */
    const avlyttet = await side.evaluate(
      () =>
        (window as unknown as { forstelinjaLedning?: { avlyttet?: boolean } })
          .forstelinjaLedning?.avlyttet === true,
    )
    if (!avlyttet)
      si("avlyttingen er ikke lastet, «/panel-avlytt.js» mangler i <head>")

    await side
      .waitForFunction(
        () =>
          document.querySelector("#velkomst dialog")?.matches(":modal") ===
          true,
        undefined,
        { timeout: 15000 },
      )
      .catch(() => si("velkomsthilsenen ble aldri en modal dialog"))
    await side
      .getByRole("button", { name: "Jeg merker nok forskjellen" })
      .click()

    await side.getByLabel("Navnet ditt").fill(`Panel ${app.navn}`)
    await side.getByRole("button", { name: "Begynn vakta" }).click()
    await side.locator("#tavle").waitFor({ timeout: 15000 })

    // Vent på at noe faktisk har kommet, framfor på klokka. Kommer det ingenting
    // i det hele tatt, er det nettopp det testen er her for å si fra om.
    await side
      .waitForFunction(
        () =>
          ((window as unknown as { forstelinjaLedning?: { antall: number } })
            .forstelinjaLedning?.antall ?? 0) > 0,
        undefined,
        { timeout: 20000 },
      )
      .catch(() => si("ingenting ble fanget på ledningen på tjue sekunder"))

    await side.getByRole("button", { name: "Hva skjedde?" }).click()
    const panel = side.locator("#panel")
    await panel.waitFor({ timeout: 5000 })

    /*
     * Vent på at loggen har en linje fra hendelsesstrømmen.
     *
     * Ikke på at den øverste er det, og det er en viktig forskjell. Datastar
     * laster hele siden på nytt når du melder deg på, og patcher så bare når
     * noe faktisk endrer seg, altså ved neste rundeskifte. Rett etter
     * påmeldingen er helsidelastingen ærlig nok det siste som kom.
     */
    await side
      .waitForFunction(
        () =>
          [...document.querySelectorAll(".panel__linjelapp")].some((l) =>
            /SSE/.test(l.textContent ?? ""),
          ),
        undefined,
        { timeout: 40000 },
      )
      .catch(() => si("loggen fikk aldri en linje fra hendelsesstrømmen"))

    /*
     * Vent på at merkelappen er fylt ut, ikke bare på at loggen har en linje.
     *
     * Lesingen traff av og til mellomrommet mellom de to, og da sto
     * merkelappen tom. En test som feiler tilfeldig blir ignorert, og da er
     * den verdiløs.
     */
    await side
      .waitForFunction(
        () =>
          (document.querySelector(".panel__merkelapp")?.textContent ?? "").trim()
            .length > 0,
        undefined,
        { timeout: 15000 },
      )
      .catch(() => si("merkelappen ble aldri fylt ut"))

    const nyttelast =
      (await panel.locator(".panel__nyttelast").textContent()) ?? ""
    if (/Ingenting har kommet/.test(nyttelast))
      si("panelet sier at ingenting har kommet")
    if (/Avlyttingen er ikke lastet/.test(nyttelast))
      si("panelet mangler avlyttingen")
    if (nyttelast.trim().length < 20) si("nyttelasten er tom")

    /*
     * Merkelappen er svaret på spørsmålet panelet stiller, og den skal si både
     * hvordan det kom og hva det var. Testen krever det utgaven faktisk sender,
     * ikke bare at det står noe: et panel som melder JSON i Datastar-utgaven er
     * like galt som et tomt panel, og ser riktigere ut.
     */
    const merkelapp =
      (await panel.locator(".panel__merkelapp").textContent()) ?? ""
    const lapper = await panel.locator(".panel__linjelapp").allTextContents()

    /*
     * Én linje skal oppgi alle tre: transporten, hendelsesnavnet og formatet.
     *
     * Testet hver for seg ville «SSE» vært sant nøyaktig når lista ikke er tom,
     * altså det de to andre alt sier. Og tre påstander som hver treffer sin
     * egen linje ville godtatt en logg der ingen enkeltlinje er riktig.
     */
    const hele = Object.values(app.venter)
    if (!lapper.some((l) => hele.every((del) => l.includes(del)))) {
      si(
        `ingen linje oppgir ${hele.join(" · ")}. Loggen sier: ${JSON.stringify(lapper)}`,
      )
    }
    if (!/\d/.test(merkelapp))
      si(`merkelappen oppgir ingen størrelse: «${merkelapp}»`)

    /*
     * «Med hva» skal være lesbar uten å rulle i panelet. Den sto nederst i en
     * smal rute før, og brukeren måtte rulle for å se det panelet handler om.
     */
    const plass = await side.evaluate(() => {
      const p = document.querySelector<HTMLElement>("#panel")
      const n = document.querySelector<HTMLElement>(".panel__nyttelast")
      if (!p || !n) return null
      const pr = p.getBoundingClientRect()
      const nr = n.getBoundingClientRect()
      return {
        panelRuller: p.scrollHeight > p.clientHeight + 2,
        nyttelastInni: nr.top >= pr.top - 1 && nr.bottom <= pr.bottom + 1,
        hoyde: Math.round(nr.height),
        utenforSkjermen:
          pr.left < 0 || pr.top < 0 || pr.right > window.innerWidth + 1,
      }
    })
    if (!plass) si("fant ikke panelet")
    else {
      if (plass.panelRuller) si("panelet må rulles for å se alt")
      if (!plass.nyttelastInni) si("nyttelasten ligger utenfor panelet")
      if (plass.hoyde < 120)
        si(`nyttelastruta er bare ${plass.hoyde} piksler høy`)
      if (plass.utenforSkjermen) si("panelet ligger delvis utenfor skjermen")
    }

    /*
     * Spørsmålstegnet ved siden av «Med hva».
     *
     * Boksen er sammenligningen demoen finnes for, og den skal si det samme i
     * alle tre utgavene, med riktig rad markert. Skrev hver app sin egen, ville
     * de gått fra hverandre første gang noen endret én av dem.
     */
    const hjelp = side.getByRole("button", {
      name: "Hvordan de tre utgavene henter data",
    })
    const boks = side.locator("#panel-utgaver")

    if (await boks.isVisible()) si("utgavetabellen står åpen uten at noen har trykket")
    await hjelp.click()
    if (!(await boks.isVisible())) si("utgavetabellen åpnet seg ikke")

    const rader = await boks.locator("tbody tr").count()
    if (rader !== 3) si(`utgavetabellen har ${rader} rader, ventet tre`)

    const markert = (await boks.locator("tr[aria-current=true] th").textContent()) ?? ""
    if (!markert.includes(app.rad)) {
      si(`tabellen markerer «${markert.replace(/\s+/g, " ").trim()}», ventet «${app.rad}»`)
    }

    // Boksen skal ta plass fra nyttelasten, men ikke presse den ut av panelet.
    const medBoks = await side.evaluate(() => {
      const p = document.querySelector<HTMLElement>("#panel")
      const n = document.querySelector<HTMLElement>(".panel__nyttelast")
      if (!p || !n) return null
      const pr = p.getBoundingClientRect()
      const nr = n.getBoundingClientRect()
      return { ruller: p.scrollHeight > p.clientHeight + 2, inni: nr.bottom <= pr.bottom + 1 }
    })
    if (medBoks?.ruller) si("panelet må rulles når utgavetabellen er åpen")
    if (medBoks && !medBoks.inni) si("nyttelasten presses ut når utgavetabellen er åpen")

    await hjelp.click()
    if (await boks.isVisible()) si("utgavetabellen lukket seg ikke igjen")

    /*
     * Markeringen av det som nettopp ble byttet ut skal tegne noe.
     *
     * Den pekte på to tokens som ikke finnes, og en ugyldig `var()` gjør hele
     * regelen ugyldig, så `outline` ble `none` og ingen så noen ting. En test
     * på at klassen finnes ville ikke fanget det; den må lese den beregnede
     * stilen.
     */
    const ring = await side.evaluate(() => {
      /*
       * Elementet legges inne i panelets egen vert, ikke i `body`.
       *
       * Panelet observerer resten av siden, så et element som dukker opp og
       * forsvinner ute på brettet ville blitt notert som en oppdatering, og
       * testen ville forurenset akkurat det den måler.
       */
      const vert = document.querySelector(".panelvert") ?? document.body
      const element = document.createElement("div")
      element.className = "panel-lyser"
      element.textContent = "markering"
      vert.append(element)
      const stil = getComputedStyle(element)
      const svar = {
        stil: stil.outlineStyle,
        bredde: stil.outlineWidth,
        farge: stil.outlineColor,
      }
      element.remove()
      return svar
    })
    if (ring.stil === "none" || Number.parseFloat(ring.bredde) < 1) {
      si(`markeringen tegner ingen ring: ${JSON.stringify(ring)}`)
    }

    await kontekst.close()
  }
} finally {
  // Uten `finally` sto en Chromium igjen og kjørte, og ingen funn ble skrevet
  // ut, den dagen en `click()` eller `fill()` kastet.
  await nettleser.close()
}

if (funn.length > 0) {
  console.error(
    `Fant ${funn.length} avvik i panelet:\n${funn.map((f) => `  - ${f}`).join("\n")}`,
  )
  process.exit(1)
}
console.log("Panelet forteller sant i alle tre utgavene.")
