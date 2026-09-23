package no.fristil.forstelinja.datastar

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
  val adresse = System.getenv("SPILLTJENER") ?: "http://127.0.0.1:8080"
  val spilltjener = Spilltjener(adresse)
  val kommuner = lesKommuner()

  // Ingen `launch { lytt() }` her: strømmen mot spilltjeneren åpnes først
  // når en nettleser ser på, og lukkes når den siste er borte. Da kan både
  // denne appen og spilltjeneren sove når ingen spiller.

  val port = System.getenv("PORT")?.toIntOrNull() ?: 8081
  val vert = System.getenv("HOST") ?: "::"

  println("Førstelinja · Datastar og Kotlin på $vert:$port, mot $adresse, med ${kommuner.size} kommuner")

  embeddedServer(CIO, port = port, host = vert) { datastarModul(spilltjener, kommuner) }.start(wait = true)
}
