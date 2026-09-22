package no.fristil.forstelinja.datastar

/**
 * Valideringen av vedtaket, gjort på serveren.
 *
 * Spilltjeneren teller poeng; den bryr seg ikke om skjemaet er fylt ut. Om
 * et vedtak er komplett er et spørsmål om skjemaet, og hører derfor hjemme
 * i appen. De tre appene validerer hver for seg, slik tre ekte apper ville
 * gjort, og det er en del av sammenligningen.
 */
data class Feil(val felt: String, val melding: String)

fun valider(
  vedtak: String?,
  hjemmel: String?,
  kommune: String?,
  felle: String?,
  kommuner: List<String>,
): List<Feil> =
  buildList {
    if (vedtak.isNullOrBlank()) {
      add(Feil("v-innvilget", "Du må velge om saken innvilges eller avslås"))
    }
    if (hjemmel.isNullOrBlank()) {
      add(Feil("hjemmel", "Du må oppgi hvilken hjemmel vedtaket bygger på"))
    }
    if (!kommune.isNullOrBlank() && kommune !in kommuner) {
      add(Feil("kommune", "«$kommune» er ikke en kommune. Slå den opp i lista."))
    }
    // «Nei, saken er i orden» er et svar, og tomt er ikke. Uten dette kunne
    // en spiller låse svar på et spørsmål hun aldri tok stilling til.
    if (felle.isNullOrBlank()) {
      add(Feil("felle", "Du må si om noe er feil i søknaden"))
    }
  }
