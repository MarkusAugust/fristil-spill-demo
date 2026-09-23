/**
 * Fristil i denne appen.
 *
 * Stilarkene importeres i `Side.astro`, altså av bunteren, slik en Astro-app
 * gjør det. Byggefunksjonene kommer fra `@fristil/designsystem`, ikke fra
 * `/react`: her skrives markupen i Astro-maler, og der heter attributtene
 * `class` og `for`, som i HTML.
 */

/** Navnet denne utgaven har utad. */
export const APPNAVN = "Astro"

/** De tre utgavene, og hvor de kjører. */
export type Utgave = { navn: string; rammeverk: string; adresse: string }

export const UTGAVER: Utgave[] = [
  {
    navn: "TanStack Start",
    rammeverk: "React, tegnet i nettleseren",
    adresse: process.env.TANSTACK_URL ?? "http://localhost:8082",
  },
  {
    navn: "Datastar",
    rammeverk: "Kotlin, ferdige HTML-biter fra serveren",
    adresse: process.env.DATASTAR_URL ?? "http://localhost:8081",
  },
  {
    navn: "Astro",
    rammeverk: "hele sider fra serveren, med én øy",
    adresse: process.env.ASTRO_URL ?? "http://localhost:8083",
  },
]

/** Navnet på kapselen som sier hvem som sitter der. */
export const KAPSEL = "spiller"
