package no.fristil.forstelinja.datastar

import java.io.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Kommunene spilleren kan velge mellom, fra `felles/kommuner.json`.
 *
 * Lista er delt med de andre appene. Mosvik står med vilje ikke i den: den
 * ble slått sammen med Inderøy i 2012, og en søker som oppgir Mosvik er
 * fella i en av sakene. Finner du ikke kommunen i lista, er det svaret.
 */
@Serializable
data class Kommunesamling(@SerialName("_om") val om: String = "", val kommuner: List<String>)

private val json = Json { ignoreUnknownKeys = true }

fun lesKommuner(
  sti: String = System.getenv("KOMMUNER_FIL") ?: "../../felles/kommuner.json"
): List<String> {
  val fil = File(sti)
  require(fil.exists()) { "Fant ingen kommuner på «$sti». Sett KOMMUNER_FIL." }
  return json.decodeFromString<Kommunesamling>(fil.readText()).kommuner
}
