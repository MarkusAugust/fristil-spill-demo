/**
 * Tjeneren i drift.
 *
 * Bygget gir en modul som eksporterer `fetch`, ikke en tjener som lytter.
 * Bun ville servert den av seg selv, men da får vi ikke bestemme hvilken
 * adresse den binder seg til, og Railways private nett er IPv6. Derfor
 * denne, som er hele forskjellen mellom utvikling og drift.
 */
import inngang from "./dist/server/server.js"

const port = Number(process.env.PORT ?? 8082)
const hostname = process.env.HOST ?? "::"

Bun.serve({
  port,
  hostname,
  // Strømmen `/hendelser` står åpen så lenge fanen er det. Uten dette
  // klipper Bun den etter ti sekunder, og spillet fryser for alle.
  idleTimeout: 0,
  fetch: (request) => inngang.fetch(request),
})

console.log(`Førstelinja (TanStack Start) lytter på ${hostname}:${port}`)
