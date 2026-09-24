import { readdirSync, readFileSync } from "node:fs"
import { join } from "node:path"
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

const APPER = ["tanstack", "astro", "skall"]
const ROT = join(import.meta.dir, "..", "apper")

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
    else if (/\.(tsx?|astro)$/.test(o.name) && !o.name.includes(".test.")) {
      // Uten kommentarene: en kodeblokk i en forklaring er ikke markup, og
      // hjelperen i `fristil.ts` beskriver nettopp fella den finnes for.
      const t = readFileSync(p, "utf8")
        .replace(/\/\*[\s\S]*?\*\//g, "")
        .replace(/^\s*\/\/.*$/gm, "")
      for (const mm of t.matchAll(/class(?:Name)?="([^"]*)"/g))
        for (const k of mm[1].split(/\s+/))
          if (k.startsWith("fs-") && fraBygger.has(k)) {
            const n = `${app}|${k}|${fraBygger.get(k)}`
            treff.set(n, (treff.get(n) ?? 0) + 1)
          }
    }
  }
}
for (const app of APPER) {
  try {
    les(join(ROT, app), app)
  } catch {}
}

const funn: string[] = []
for (const [n, c] of treff) {
  const [app, klasse, bygger] = n.split("|")
  funn.push(
    `${app}: skriver .${klasse} for hånd ${c} steder, men fs.${bygger} finnes`,
  )
}

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
