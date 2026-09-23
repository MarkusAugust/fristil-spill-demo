import type { Feil } from "./tilstand"

/**
 * Valideringen av vedtaket, gjort på appserveren.
 *
 * Spilltjeneren teller poeng; den bryr seg ikke om skjemaet er fylt ut. Om
 * et vedtak er komplett er et spørsmål om skjemaet, og hører derfor hjemme
 * i appen. De tre appene validerer hver for seg, slik tre ekte apper ville
 * gjort, og det er en del av sammenligningen.
 */
export function valider(
  vedtak: string | null,
  hjemmel: string | null,
  kommune: string | null,
  felle: string | null,
  kommuner: string[],
): Feil[] {
  const feil: Feil[] = []
  if (!vedtak) feil.push({ felt: "v-innvilget", melding: "Du må velge om saken innvilges eller avslås" })
  if (!hjemmel) feil.push({ felt: "hjemmel", melding: "Du må oppgi hvilken hjemmel vedtaket bygger på" })
  if (kommune && !kommuner.includes(kommune)) {
    feil.push({ felt: "kommune", melding: `«${kommune}» er ikke en kommune. Slå den opp i lista.` })
  }
  // «Nei, saken er i orden» er et svar, og tomt er ikke. Uten dette kunne en
  // spiller låse svar på et spørsmål hun aldri tok stilling til.
  if (!felle) feil.push({ felt: "felle", melding: "Du må si om noe er feil i søknaden" })
  return feil
}
