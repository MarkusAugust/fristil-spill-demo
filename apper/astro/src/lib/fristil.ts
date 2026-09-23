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

/**
 * Lenka til en annen utgave, med det du er og hva du har valgt.
 *
 * I drift ligger de tre utgavene på hvert sitt domene, og en kapsel gjelder
 * bare for sitt eget. Uten dette mistet du navnet ditt og plassen på tavla i
 * det du byttet, og måtte melde deg på som en ny saksbehandler. Id-en er en
 * billett som følger med i adressen, og appen du kommer til veksler den inn
 * i sin egen kapsel og fjerner den fra adressen igjen.
 *
 * Billetten er i praksis nøkkelen til spilleren, og den som får lenka blir
 * deg. I et spill er det greit. I noe som betyr noe ville dette vært en
 * kortlevd engangsbillett fra spilltjeneren i stedet.
 */
export function lenkeTilUtgave(
  adresse: string,
  spillerId: string | null,
  tema: string | null,
): string {
  const deler = new URLSearchParams()
  if (spillerId) deler.set("spiller", spillerId)
  if (tema === "light" || tema === "dark") deler.set("tema", tema)
  const spørring = deler.toString()
  return spørring ? `${adresse}?${spørring}` : adresse
}

/** Navnet på kapselen som sier hvem som sitter der. */
export const KAPSEL = "spiller"
