package no.fristil.forstelinja

import kotlinx.serialization.Serializable

/**
 * Det spilltjeneren sender ut. Ren JSON, aldri HTML.
 *
 * De tre appene rendrer hver sin måte fra nøyaktig denne formen: React
 * tegner i nettleseren, Kotlin-appen lager HTML-biter på serveren, og Astro
 * rendrer en ny side. Derfor ligger formen her, ett sted.
 *
 * `fasit` og `forklaring` er `null` mens runden pågår. Ellers kunne hvem som
 * helst lest svaret ut av nettverksfanen.
 */

@Serializable
data class SakUt(
  val id: String,
  val tittel: String,
  val sammendrag: String,
  val soker: Soker,
)

@Serializable
data class TavleRad(
  val plass: Int,
  val navn: String,
  val poeng: Int,
  val stack: Stack,
  val erMeg: Boolean,
)

@Serializable
data class MegUt(
  val navn: String,
  val poeng: Int,
  val sistePoeng: Int,
  val plass: Int,
  val forrigePlass: Int,
  val harSvart: Boolean,
  val svar: Svar?,
)

@Serializable
data class ToppEntry(val navn: String, val poeng: Int, val stack: Stack, val nar: Long)

@Serializable
data class Tilstand(
  val fase: Fase,
  val rundeNr: Int,
  val runderTotalt: Int,
  /** Absolutt tidspunkt. Klienten teller ned selv, så et forsinket bud ikke flytter fristen. */
  val fristMs: Long,
  /** Serverens klokke nå, slik at klienten kan regne ut forskjellen mot sin egen. */
  val naMs: Long,
  val sak: SakUt,
  val hjemler: List<Hjemmel>,
  val fasit: Fasit?,
  val forklaring: String?,
  val tavle: List<TavleRad>,
  val meg: MegUt?,
  val evigToppliste: List<ToppEntry>,
)
