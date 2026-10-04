import { readdirSync, readFileSync } from "node:fs"
import { basename, join } from "node:path"
import { fs } from "@fristil/designsystem"

/**
 * At appene bruker Fristil slik dokumentasjonen sier.
 *
 * Regelen står på hver komponentside: lager du markupen med JavaScript,
 * kaller du byggefunksjonen. Skriver du klassen for hånd der det finnes en
 * funksjon, mister du to ting. Navnet følger ikke med hvis pakken endrer
 * det, og attributtene funksjonen ellers ville gitt uteblir.
 *
 * Fella er lumsk i begge rammeverkene. I JSX vinner den siste propen, så
 * `{...fs.card()} className="fs-card kort"` kaster det byggefunksjonen ga og
 * virker likevel, helt til klassen endrer navn. I Astro skriver malen ut to
 * `class`-attributter, og nettleseren beholder den første.
 *
 * Datastar-appen er med vilje utenfor: Kotlin kan ikke kalle `fs`, og der er
 * det å skrive klassene nettopp det dokumentasjonen sier.
 *
 *     cd tester && bun run fristilbruk
 */

/*
 * Mappene som leses, og hvorfor `felles` er med.
 *
 * `felles/panel.js` lastes av alle tre spillutgavene, og skriver markupen sin
 * med byggefunksjoner appen sender inn. Regelen gjelder altså der like fullt.
 * Fila lå utenfor både rota og filtypefilteret, så den eneste ekte
 * overtredelsen i repoet var usynlig for nettopp den vaktposten som skulle
 * finne den.
 */
const MAPPER = [
  join(import.meta.dir, "..", "apper", "tanstack"),
  join(import.meta.dir, "..", "apper", "astro"),
  join(import.meta.dir, "..", "apper", "skall"),
  join(import.meta.dir, "..", "felles"),
]

const fraBygger = new Map<string, string>()
for (const [navn, f] of Object.entries(fs)) {
  if (typeof f !== "function") continue
  let svar: unknown
  try {
    svar = f({ id: "x", titleId: "t", count: 1, label: "L" })
  } catch {}
  const samle = (v: unknown): void => {
    if (Array.isArray(v)) return v.forEach(samle)
    if (!v || typeof v !== "object") return
    const o = v as Record<string, unknown>
    if (typeof o.class === "string")
      for (const k of o.class.split(/\s+/))
        if (k && !fraBygger.has(k)) fraBygger.set(k, navn + "()")
    for (const x of Object.values(o)) if (typeof x === "object") samle(x)
  }
  samle(svar)
  for (const [n, v] of Object.entries(f as Record<string, unknown>))
    if (typeof v === "string" && v.startsWith("fs-") && !fraBygger.has(v))
      fraBygger.set(v, `${navn}.${n}`)
}

const treff = new Map<string, number>()
const les = (m: string, app: string): void => {
  for (const o of readdirSync(m, { withFileTypes: true })) {
    if (
      o.name === "node_modules" ||
      o.name === "dist" ||
      o.name === "build" ||
      o.name === ".astro"
    )
      continue
    const p = join(m, o.name)
    if (o.isDirectory()) les(p, app)
    else if (
      /\.(tsx?|jsx?|astro)$/.test(o.name) &&
      !o.name.includes(".test.")
    ) {
      lest += 1
      // Uten kommentarene: en kodeblokk i en forklaring er ikke markup, og
      // hjelperen i `fristil.ts` beskriver nettopp fella den finnes for.
      const t = readFileSync(p, "utf8")
        .replace(/\/\*[\s\S]*?\*\//g, "")
        .replace(/^\s*\/\/.*$/gm, "")
      for (const mm of [
        ...t.matchAll(/class(?:Name)?=["']([^"']*)["']/g),
        ...t.matchAll(/classList\.add\(["']([^"']*)["']/g),
      ])
        for (const k of mm[1].split(/\s+/))
          if (k.startsWith("fs-") && fraBygger.has(k)) {
            const n = `${app}|${k}|${fraBygger.get(k)}`
            treff.set(n, (treff.get(n) ?? 0) + 1)
          }
    }
  }
}
/*
 * Og en teller, så grønt betyr «lest og funnet ingenting».
 *
 * `try {} catch {}` rundt lesingen gjorde at en omdøpt mappe ga «Appene
 * bruker Fristil slik dokumentasjonen sier» på null filer.
 */
const funn: string[] = []

let lest = 0
for (const mappe of MAPPER) les(mappe, basename(mappe))
if (lest < 20)
  funn.push(
    `leste bare ${lest} filer, så sjekken sier ikke noe. Stemmer stiene?`,
  )

for (const [n, c] of treff) {
  const [app, klasse, bygger] = n.split("|")
  funn.push(
    `${app}: skriver .${klasse} for hånd ${c} ${c === 1 ? "sted" : "steder"}, men fs.${bygger} finnes`,
  )
}

/*
 * Klassen er bare halve svaret fra en byggefunksjon.
 *
 * `fs.button({ variant: "ghost" })` gir både `class` og `data-variant`, og
 * `felles/panel.js` skrev lenge av det siste for hånd ved siden av det
 * første. Det er den samme feilen som en håndskrevet klasse: endrer pakken
 * attributtnavnet, følger klassen med og attributtet blir stående.
 *
 * Sjekken må stå her og ikke i nettleseren. `panel.ts` leser de fire
 * attributtene fra DOM-en, og et håndskrevet attributt ved siden av klassen
 * gir nøyaktig den samme DOM-en, så den sjekken kan bare felle en
 * halvreversering. Spørsmålet er hva koden skriver, ikke hva siden viser.
 *
 * Bare `felles/panel.js` leses. Der bygges all markup som tekst, og alt som
 * har en klasse går gjennom `attributter()`, så et `data-*` skrevet ut i en
 * mal er per definisjon skrevet av. I appene er de samme attributtene
 * legitime på elementer uten byggefunksjon.
 */
const PANEL = join(import.meta.dir, "..", "felles", "panel.js")
const AVSKREVNE = ["data-variant", "data-size", "data-color", "data-state"]
const panelkilde = readFileSync(PANEL, "utf8")
for (const attributt of AVSKREVNE) {
  const antall = panelkilde.split(`${attributt}="`).length - 1
  if (antall > 0)
    funn.push(
      `felles/panel.js: skriver ${attributt} for hånd ${antall} ${
        antall === 1 ? "sted" : "steder"
      }, men byggefunksjonen gir det gjennom attributter()`,
    )
}

/*
 * Og tokenene: hver `var(--…)` skal peke på noe som finnes.
 *
 * En `var()` uten reserve mot en variabel som ikke finnes, gjør hele
 * erklæringen ugyldig, og ingenting sier fra. Feilen har stått her fire
 * ganger: `--semantic-surface-background` og `--semantic-page-subtle` ga
 * gjennomsiktige flater, `--semantic-info-*` en ramme som aldri ble tegnet,
 * og `--semantic-page-muted` i skallet var aldri et token. Så kom 0.22, der
 * `--palette-*`, `--semantic-*` og `--size-*` forsvant med ett, og nesten
 * 200 steder i `brett.css` og skallet ville sluttet å virke i stillhet.
 *
 * Det som finnes, er det pakkens samlede stilark definerer, og det appenes
 * egne filer definerer selv, som `--spill-frist-start`. En `var()` med
 * reserve er tatt med vilje og står utenfor, og det samme gjør kommentarene,
 * der de gamle navnene står igjen som historie.
 */
const KILDER = [
  join(import.meta.dir, "..", "felles"),
  join(import.meta.dir, "..", "apper"),
]
const FRISTIL_CSS = join(
  import.meta.dir,
  "node_modules",
  "@fristil",
  "designsystem",
  "dist",
  "fristil.css",
)
const definert = new Set(
  [...readFileSync(FRISTIL_CSS, "utf8").matchAll(/(--[\w-]+)\s*:/g)].map(
    (m) => m[1],
  ),
)
const bruk: { fil: string; navn: string }[] = []
let tokenfiler = 0
const lesTokens = (m: string): void => {
  for (const o of readdirSync(m, { withFileTypes: true })) {
    if (
      ["node_modules", "dist", "build", ".astro", ".output", ".gradle"].includes(
        o.name,
      )
    )
      continue
    const p = join(m, o.name)
    if (o.isDirectory()) lesTokens(p)
    else if (/\.(css|tsx?|jsx?|astro|kt)$/.test(o.name)) {
      tokenfiler += 1
      const t = readFileSync(p, "utf8")
        .replace(/\/\*[\s\S]*?\*\//g, "")
        .replace(/^\s*\/\/.*$/gm, "")
      for (const d of t.matchAll(/(--[\w-]+)\s*:/g)) definert.add(d[1])
      for (const v of t.matchAll(/var\(\s*(--[\w-]+)\s*\)/g))
        bruk.push({
          fil: p.slice(join(import.meta.dir, "..").length + 1),
          navn: v[1],
        })
    }
  }
}
for (const k of KILDER) lesTokens(k)
if (tokenfiler < 20 || definert.size < 100)
  funn.push(
    `leste ${tokenfiler} filer og ${definert.size} definerte variabler, så tokensjekken sier ikke noe. Stemmer stiene?`,
  )
const ukjente = new Map<string, number>()
for (const { fil, navn } of bruk)
  if (!definert.has(navn)) {
    const n = `${fil}: var(${navn})`
    ukjente.set(n, (ukjente.get(n) ?? 0) + 1)
  }
for (const [n, c] of ukjente)
  funn.push(
    `${n} finnes verken i Fristil eller i appen (${c} ${c === 1 ? "sted" : "steder"})`,
  )

if (funn.length > 0) {
  console.error(
    `Fant ${funn.length} avvik:\n${funn
      .sort()
      .map((f) => `  - ${f}`)
      .join("\n")}`,
  )
  process.exit(1)
}
console.log("Appene bruker Fristil slik dokumentasjonen sier.")
