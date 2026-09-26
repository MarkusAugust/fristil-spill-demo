package no.fristil.forstelinja

import java.io.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Sakene spillet stiller, lest fra `felles/saker.json`.
 *
 * Fila er delt med de tre appene, og er JSON og ikke Kotlin med vilje: da
 * kan både JVM-en og de to JavaScript-appene lese den samme kilden, og ingen
 * av dem eier den.
 */

@Serializable
data class Hjemmel(val kode: String, val tekst: String)

@Serializable
data class Soker(
  val navn: String,
  val fodselsdato: String,
  val kommune: String,
  val epost: String,
)

/** Hva som er riktig svar. Sendes aldri til spilleren før runden er over. */
@Serializable
data class Fasit(
  val vedtak: Vedtak,
  val hjemmel: String,
  /** Feltet som er feil, eller `null` når saken er i orden. */
  val felle: String? = null,
)

@Serializable
data class Sak(
  val id: String,
  val tittel: String,
  /** Én setning som ingress, over selve søknaden. */
  val sammendrag: String,
  /** Søknaden med søkerens egne ord. Det er denne teksten spilleren leser. */
  val tekst: String,
  val soker: Soker,
  val fasit: Fasit,
  val forklaring: String,
)

@Serializable
data class Sakssamling(
  @SerialName("_om") val om: String = "",
  val hjemler: List<Hjemmel>,
  val saker: List<Sak>,
) {
  init {
    require(saker.size >= RUNDER_PER_SPILL) {
      "Trenger minst $RUNDER_PER_SPILL saker, fant ${saker.size}"
    }
    require(saker.map { it.id }.toSet().size == saker.size) {
      "To saker har samme id"
    }
    val koder = hjemler.map { it.kode }.toSet()
    val ukjente = saker.map { it.fasit.hjemmel }.filterNot { it in koder }
    require(ukjente.isEmpty()) { "Fasit viser til hjemler som ikke finnes: $ukjente" }

    val felt = setOf("fodselsdato", "kommune", "epost")
    val ukjenteFeller = saker.mapNotNull { it.fasit.felle }.filterNot { it in felt }
    require(ukjenteFeller.isEmpty()) { "Ukjente feller: $ukjenteFeller" }
  }
}

private val json = Json { ignoreUnknownKeys = true }

/**
 * Leser samlingen fra disk.
 *
 * Stien kommer fra `Main.kt`, som leser `SAKER_FIL`. Standarden der peker
 * opp i repoet, siden fila er delt. Blir tjenesten bygd med bare sin egen
 * mappe, må variabelen settes, ellers finnes ikke fila.
 */
fun lesSaker(sti: String): Sakssamling {
  val fil = File(sti)
  require(fil.exists()) { "Fant ingen saker på «$sti». Sett SAKER_FIL." }
  return json.decodeFromString(fil.readText())
}

/**
 * Kommunene appene lar spilleren velge mellom.
 *
 * Spilltjeneren bruker dem ikke selv; det er appene som validerer skjemaet.
 * Men fila hører til de delte dataene, og invarianten om at nøyaktig én sak
 * har en kommune som ikke finnes, hører hjemme i en test her.
 */
@Serializable
data class Kommunesamling(@SerialName("_om") val om: String = "", val kommuner: List<String>)

fun lesKommuner(sti: String): List<String> {
  val fil = File(sti)
  require(fil.exists()) { "Fant ingen kommuner på «$sti». Sett KOMMUNER_FIL." }
  return json.decodeFromString<Kommunesamling>(fil.readText()).kommuner
}
