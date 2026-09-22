package no.fristil.forstelinja

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Starter spilltjeneren, og løkka som flytter spillet videre.
 *
 * Løkka tikker fire ganger i sekundet. `tikk()` gjør ingenting før fristen
 * er ute, så den kan kalles så ofte man vil, og fire ganger i sekundet gjør
 * at nedtellingen aldri bommer med mer enn et kvart sekund.
 */
fun main(): Unit = runBlocking {
  val samling = lesSaker()
  val toppliste = Toppliste()
  val spill = Spill(samling, toppliste, tider = Tider.fraMiljo())

  launch {
    while (true) {
      spill.tikk()
      delay(250)
    }
  }

  val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
  // Railways private nett er IPv6. `::` tar imot begge deler på JVM-en.
  val vert = System.getenv("HOST") ?: "::"

  println("Førstelinja: spilltjeneren lytter på $vert:$port med ${samling.saker.size} saker")

  embeddedServer(CIO, port = port, host = vert) { spillModul(spill) }
    .start(wait = true)
}
