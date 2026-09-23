/**
 * Ordene skjermen bruker.
 *
 * Samme utregninger som i de to andre appene. De er skrevet av her, ikke
 * delt: appene er tre klienter av det samme JSON-API-et, og skal kunne
 * skrives i hver sin verden.
 */

/** Initialene i avataren. */
export function initialer(navn: string): string {
  const deler = navn.trim().split(/\s+/).filter(Boolean).slice(0, 2)
  return deler.map((d) => d[0]!.toUpperCase()).join("") || "?"
}

/** Navnet appen har utad. Verdien fra spilltjeneren er en maskinverdi. */
export function appnavn(stack: string): string {
  if (stack === "tanstack") return "TanStack"
  if (stack === "datastar") return "Datastar"
  if (stack === "astro") return "Astro"
  return "Ukjent"
}

export function farge(stack: string): "success" | "warning" | "neutral" {
  if (stack === "datastar") return "success"
  if (stack === "astro") return "warning"
  return "neutral"
}

export function feltnavn(felle: string): string {
  if (felle === "fodselsdato") return "fødselsdatoen"
  if (felle === "kommune") return "kommunen"
  if (felle === "epost") return "e-postadressen"
  return felle
}

export function stor(tekst: string): string {
  return tekst.charAt(0).toUpperCase() + tekst.slice(1)
}

export function vedtaksord(vedtak: string | null | undefined): string {
  if (vedtak === "innvilget") return "Innvilget"
  if (vedtak === "avslatt") return "Avslått"
  return "Ikke besvart"
}

export function tallord(n: number): string {
  return ["", "én", "to", "tre", "fire", "fem"][n] ?? String(n)
}
