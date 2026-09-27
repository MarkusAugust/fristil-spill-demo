/**
 * At markupen de tre appene faktisk sender stemmer med Fristil.
 *
 * `diagnoseMarkup` er den samme sjekken editorutvidelsen kjører mens du
 * skriver: elementer som ikke finnes, attributter elementet ikke har,
 * verdier utenfor lista, klasser som `fs-buton`, og `<fs-field>` uten
 * kontroll eller ledetekst. Malene i Kotlin-appen er strenger, og Astro og
 * React skriver JSX, så ingen editor ser den ferdige HTML-en.
 *
 * To sider per app: forsiden slik serveren sender den, og brettet etter
 * påmelding, der fanene, feiloppsummeringen, sprettoppvinduet og
 * forslagslista står. Forsiden alene så bare velkomstdialogen, og gikk
 * grønn forbi en `hidden="true"` på feiloppsummeringen. Brettet leses fra
 * DOM-en etter at komponentene har kjørt, så det som sjekkes der er
 * serverens attributter pluss det komponentene fylte inn; en gal verdi fra
 * serveren står fortsatt, siden komponentene bare skriver det som mangler.
 *
 * Kjør med appene oppe (`./kjor.sh rask`): bun run markup
 */
import { diagnoseMarkup } from "@fristil/designsystem/diagnostics"
import { chromium } from "playwright"

const APPER = [
  { navn: "datastar", url: process.env.DATASTAR_URL ?? "http://127.0.0.1:8081" },
  { navn: "tanstack", url: process.env.TANSTACK_URL ?? "http://localhost:8082" },
  { navn: "astro", url: process.env.ASTRO_URL ?? "http://127.0.0.1:8083" },
]

const funn: string[] = []
let sjekket = 0

function meld(hvor: string, html: string): void {
  for (const f of diagnoseMarkup(html)) {
    const linje = html.slice(0, f.start).split("\n").length
    funn.push(
      `${hvor}:${linje}: ${f.severity === "error" ? "feil" : "advarsel"}: ${f.message}`,
    )
  }
}

const nettleser = await chromium.launch()
try {
  for (const app of APPER) {
    const kontekst = await nettleser.newContext()
    // Som i paritetstesten: en kapsel, så testspilleren ikke havner i den
    // evige topplista.
    await kontekst.addCookies([{ name: "forstelinja-test", value: "1", url: app.url }])
    const side = await kontekst.newPage()
    side.setDefaultTimeout(20_000)

    let svar: Awaited<ReturnType<typeof side.goto>> = null
    for (let forsok = 0; forsok < 5 && !svar?.ok(); forsok++) {
      svar = await side.goto(app.url, { waitUntil: "domcontentloaded" }).catch(() => null)
      if (!svar?.ok()) await side.waitForTimeout(1000)
    }
    if (!svar?.ok()) {
      funn.push(`${app.navn}: siden svarte ikke på fem forsøk`)
      await kontekst.close()
      continue
    }
    // Forsiden slik serveren sendte den, før noe skript har rørt den.
    const forside = await svar.text()
    if (!forside.includes("<fs-")) {
      funn.push(`${app.navn}: forsiden har ingen <fs-…>, så sjekken ser ikke på noe`)
      await kontekst.close()
      continue
    }
    meld(`${app.navn} forside`, forside)

    // Brettet, etter påmelding. Skjemaet finnes bare mens en runde går, så
    // ventingen er romslig.
    await side.getByRole("button", { name: "Jeg merker nok forskjellen" }).click()
    await side.getByLabel("Navnet ditt").fill(`Markup ${app.navn}`)
    await side.getByRole("button", { name: "Begynn vakta" }).click()
    await side.locator("#tavle").waitFor({ timeout: 15_000 })
    const brettKlart = await side
      .locator("#hjemmel")
      .waitFor({ timeout: 90_000 })
      .then(() => true)
      .catch(() => false)
    if (!brettKlart) {
      funn.push(`${app.navn}: ingen runde begynte på halvannet minutt, så brettet ble ikke sjekket`)
      await kontekst.close()
      continue
    }
    const brett = await side.content()
    if (!brett.includes("<fs-suggestion")) {
      funn.push(`${app.navn}: brettet har ingen forslagsliste, så sjekken så ikke på det den skulle`)
      await kontekst.close()
      continue
    }
    meld(`${app.navn} brett`, brett)
    await kontekst.close()
    // Sist i løkka: telleren sier hvor mange apper som faktisk ble sjekket
    // på begge sidene.
    sjekket += 1
  }
} finally {
  await nettleser.close()
}

if (funn.length > 0) {
  console.error(
    `Markupen stemmer ikke med Fristil:\n\n${funn.map((f) => `  ${f}`).join("\n")}\n`,
  )
}
if (sjekket !== APPER.length || sjekket === 0) {
  console.error(`Sjekket ${sjekket} av ${APPER.length} apper på begge sidene.`)
  process.exit(1)
}
if (funn.length > 0) process.exit(1)
console.log(`Markupen fra alle ${sjekket} appene stemmer med Fristil, på forsiden og brettet.`)
