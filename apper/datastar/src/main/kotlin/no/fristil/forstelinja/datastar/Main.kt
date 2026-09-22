package no.fristil.forstelinja.datastar

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main(): Unit = runBlocking {
  val adresse = System.getenv("SPILLTJENER") ?: "http://127.0.0.1:8080"
  val spilltjener = Spilltjener(adresse)

  launch { spilltjener.lytt() }

  val port = System.getenv("PORT")?.toIntOrNull() ?: 8081
  val vert = System.getenv("HOST") ?: "::"

  println("Førstelinja · Datastar og Kotlin på $vert:$port, mot $adresse")

  embeddedServer(CIO, port = port, host = vert) { datastarModul(spilltjener) }.start(wait = true)
}
