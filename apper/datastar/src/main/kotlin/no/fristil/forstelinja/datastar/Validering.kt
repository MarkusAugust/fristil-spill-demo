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

fun valider(vedtak: String?, hjemmel: String?, kommune: String?, kommuner: List<String>): List<Feil> =
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
  }
