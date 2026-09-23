/**
 * Tjeneren i drift.
 *
 * Bygget gir to ting: en modul som eksporterer `fetch`, og en mappe med
 * klientfilene. Ingen av dem serverer seg selv. `vite dev` gjorde begge
 * deler under utvikling, og derfor viste dette seg først i drift: sida kom
 * ferdig tegnet fra serveren og så helt riktig ut, mens hver eneste
 * skriptfil svarte 404 og ingenting av det som skjer i nettleseren virket.
 *
 * Bun ville dessuten servert `fetch`-modulen av seg selv, men da får vi
 * ikke bestemme hvilken adresse den binder seg til, og Railways private
 * nett er IPv6.
 */
import inngang from "./dist/server/server.js"

const port = Number(process.env.PORT ?? 8082)
const hostname = process.env.HOST ?? "::"

/** Filene Vite har bygget, med hash i navnet. */
const KLIENT = "./dist/client"

Bun.serve({
  port,
  hostname,
  // Strømmen `/hendelser` står åpen så lenge fanen er det. Uten dette
  // klipper Bun den etter ti sekunder, og spillet fryser for alle.
  idleTimeout: 0,

  async fetch(request) {
    const sti = new URL(request.url).pathname

    // `..` i stien er det eneste som kan peke ut av mappa, og Bun løser
    // ikke stien for oss.
    if (sti !== "/" && !sti.includes("..")) {
      const fil = Bun.file(KLIENT + sti)
      if (await fil.exists()) {
        return new Response(fil, {
          headers: {
            // Filnavnene har hash i seg, så de kan mellomlagres for alltid.
            // Alt annet, som et ikon, får en time.
            "cache-control": sti.startsWith("/assets/")
              ? "public, max-age=31536000, immutable"
              : "public, max-age=3600",
          },
        })
      }
    }

    return inngang.fetch(request)
  },
})

console.log(`Førstelinja (TanStack Start) lytter på ${hostname}:${port}`)
