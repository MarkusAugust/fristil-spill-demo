package no.fristil.forstelinja

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Starter spilltjeneren, og løkka som flytter spillet videre.
 *
 * Miljøet leses her, og bare her. Klassene får verdier, ikke variabelnavn:
 * da står alt som kan stilles utenfra på ett sted, og en test trenger aldri
 * å sette miljø for å få en klasse til å oppføre seg. Variablene er
 * dokumentert i README.
 *
 * Løkka tikker fire ganger i sekundet. `tikk()` gjør ingenting før fristen
 * er ute, så den kan kalles så ofte man vil, og fire ganger i sekundet gjør
 * at nedtellingen aldri bommer med mer enn et kvart sekund.
 */
fun main(): Unit = runBlocking {
  val miljo = System.getenv()

  val samling = lesSaker(miljo["SAKER_FIL"] ?: "../../felles/saker.json")
  val kommuner = lesKommuner(miljo["KOMMUNER_FIL"] ?: "../../felles/kommuner.json")
  val toppliste = Toppliste(miljo["TOPPLISTE_FIL"] ?: "toppliste.db")
  val tider =
    Tider(
      runde = miljo["RUNDE_MS"]?.toLongOrNull() ?: RUNDE_MS,
      oppgjor = miljo["OPPGJOR_MS"]?.toLongOrNull() ?: OPPGJOR_MS,
      slutt = miljo["SLUTT_MS"]?.toLongOrNull() ?: SLUTT_MS,
    )
  val spill = Spill(samling, toppliste, tider = tider, kommuner = kommuner)
  val varme = Varme(timer = miljo["VARM_TIMER"]?.toDoubleOrNull() ?: 0.0)

  // Foten i døra, så tjenesten ikke sovner rett etter at noen har spilt.
  launch { varme.hold() }

  launch {
    while (true) {
      spill.tikk()
      delay(250)
    }
  }

  val port = miljo["PORT"]?.toIntOrNull() ?: 8080
  // Railways private nett er IPv6. `::` tar imot begge deler på JVM-en.
  val vert = miljo["HOST"] ?: "::"

  println("Førstelinja: spilltjeneren lytter på $vert:$port med ${samling.saker.size} saker")

  embeddedServer(CIO, port = port, host = vert) { spillModul(spill, varme) }
    .start(wait = true)
}
