package no.fristil.forstelinja.datastar

import kotlinx.serialization.Serializable

/**
 * Formen spilltjeneren sender.
 *
 * Skrevet av her, ikke delt som Kotlin-kode med spilltjeneren. Appene er
 * klienter av et JSON-API, og kontrakten er JSON-en, ikke en felles modul.
 * Da kan de tre appene skrives i hver sin verden uten å dra hverandre med
 * seg, som er hele poenget med demoen.
 */
@Serializable data class Soker(val navn: String, val fodselsdato: String, val kommune: String, val epost: String)

@Serializable data class Hjemmel(val kode: String, val tekst: String)

@Serializable data class SakUt(val id: String, val tittel: String, val sammendrag: String, val soker: Soker)

@Serializable data class Fasit(val vedtak: String, val hjemmel: String, val felle: String? = null)

@Serializable
data class TavleRad(val plass: Int, val navn: String, val poeng: Int, val stack: String, val erMeg: Boolean)

@Serializable
data class MegUt(
  val navn: String,
  val poeng: Int,
  val sistePoeng: Int,
  val plass: Int,
  val forrigePlass: Int,
  val harSvart: Boolean,
)

@Serializable data class ToppEntry(val navn: String, val poeng: Int, val stack: String, val nar: Long)

@Serializable
data class Tilstand(
  val fase: String,
  val rundeNr: Int,
  val runderTotalt: Int,
  val fristMs: Long,
  val naMs: Long,
  val sak: SakUt,
  val hjemler: List<Hjemmel>,
  val fasit: Fasit? = null,
  val forklaring: String? = null,
  val tavle: List<TavleRad>,
  val meg: MegUt? = null,
  val evigToppliste: List<ToppEntry>,
)

/**
 * Hvem som blir med, og fra hvilken app.
 *
 * `stack` har med vilje ingen standardverdi. kotlinx.serialization utelater
 * en verdi som er lik standardverdien, også når kallstedet har satt den
 * uttrykkelig, og da sa tavla «ukjent» om nettopp den kolonnen som bærer
 * hele poenget med demoen. Uten standardverdi må hvert kallsted si hva det
 * er, og verdien havner alltid på tråden.
 */
@Serializable data class BliMedInn(val navn: String, val stack: String)

@Serializable data class BliMedUt(val spillerId: String, val navn: String)

@Serializable data class Svar(val vedtak: String? = null, val hjemmel: String? = null, val felle: String? = null)

@Serializable data class SvarInn(val spillerId: String, val svar: Svar)
