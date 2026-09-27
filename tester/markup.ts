/**
 * At markupen de tre appene faktisk sender stemmer med Fristil.
 *
 * `diagnoseMarkup` er den samme sjekken editorutvidelsen kjører mens du
 * skriver: elementer som ikke finnes, attributter elementet ikke har,
 * verdier utenfor lista, klasser som `fs-buton`, og `<fs-field>` uten
 * kontroll eller ledetekst. Malene i Kotlin-appen er strenger, og Astro og
 * React skriver JSX, så ingen editor ser den ferdige HTML-en. Her kjøres
 * sjekken over det serveren sender, før noe skript har rukket å rette på
 * det.
 *
 * Kjør med appene oppe (`./kjor.sh`): bun run markup
 */
import { diagnoseMarkup } from "@fristil/designsystem/diagnostics"

const APPER = [
  { navn: "datastar", url: process.env.DATASTAR_URL ?? "http://127.0.0.1:8081" },
  { navn: "tanstack", url: process.env.TANSTACK_URL ?? "http://localhost:8082" },
  { navn: "astro", url: process.env.ASTRO_URL ?? "http://127.0.0.1:8083" },
]

const funn: string[] = []
let sjekket = 0

for (const app of APPER) {
  let html = ""
  for (let forsok = 0; forsok < 5 && html === ""; forsok++) {
    html = await fetch(app.url, { headers: { cookie: "forstelinja-test=1" } })
      .then((svar) => (svar.ok ? svar.text() : ""))
      .catch(() => "")
    if (html === "") await Bun.sleep(1000)
  }
  if (html === "") {
    funn.push(`${app.navn}: siden svarte ikke på fem forsøk`)
    continue
  }
  if (!html.includes("<fs-")) {
    funn.push(`${app.navn}: siden har ingen <fs-…>, så sjekken ser ikke på noe`)
    continue
  }

  for (const f of diagnoseMarkup(html)) {
    const linje = html.slice(0, f.start).split("\n").length
    funn.push(`${app.navn}:${linje}: ${f.severity === "error" ? "feil" : "advarsel"}: ${f.message}`)
  }
  // Sist i løkka: telleren sier hvor mange sider som faktisk ble sjekket.
  sjekket += 1
}

if (funn.length > 0) {
  console.error(`Markupen stemmer ikke med Fristil:\n\n${funn.map((f) => `  ${f}`).join("\n")}\n`)
}
if (sjekket !== APPER.length || sjekket === 0) {
  console.error(`Sjekket ${sjekket} av ${APPER.length} apper.`)
  process.exit(1)
}
if (funn.length > 0) process.exit(1)
console.log(`Markupen fra alle ${sjekket} appene stemmer med Fristil.`)
