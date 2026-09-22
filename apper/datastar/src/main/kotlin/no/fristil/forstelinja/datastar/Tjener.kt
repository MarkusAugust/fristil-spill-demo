package no.fristil.forstelinja.datastar

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.util.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive

/**
 * Appserveren.
 *
 * Den lager HTML, og sender ferdige biter ned til nettleseren når noe endrer
 * seg. Ingen JavaScript regner ut noe her: Datastar morfer inn det serveren
 * har skrevet.
 */

private const val KAPSEL = "spiller"

private val json = Json { ignoreUnknownKeys = true }

/** Datastars format: ett event, og hver linje HTML på sin egen `data:`-linje. */
fun patch(vararg biter: String): String {
  val linjer = biter.joinToString("\n").lines().joinToString("\n") { "data: elements $it" }
  return "event: datastar-patch-elements\n$linjer\n\n"
}

/** Signalene Datastar sender med en `@post`. */
private suspend fun ApplicationCall.signaler(): Map<String, String> {
  val tekst = receiveText().ifBlank { "{}" }
  return runCatching {
      json.parseToJsonElement(tekst).let { rot ->
        rot.let { it as? kotlinx.serialization.json.JsonObject }
          ?.mapValues { (_, v) -> v.jsonPrimitive.content }
          ?: emptyMap()
      }
    }
    .getOrDefault(emptyMap())
}

fun Application.datastarModul(spilltjener: Spilltjener) {
  install(SSE)

  routing {
    get("/helse") { call.respondText("ok") }

    get("/brett.css") {
      call.respondText(BRETT_CSS, ContentType.Text.CSS)
    }

    get("/") {
      val spillerId = call.request.cookies[KAPSEL]
      val tilstand = spilltjener.tilstand(spillerId)
      val navn = tilstand.meg?.navn
      call.respondText(side(tilstand, navn), ContentType.Text.Html)
    }

    post("/bli-med") {
      val navn = call.receiveParameters()["navn"].orEmpty()
      val spiller = spilltjener.bliMed(navn)

      // Kapsel og ikke minne i nettleseren: Astro-appen laster hele sider på
      // nytt, og alle tre skal oppføre seg likt.
      call.response.cookies.append(
        Cookie(KAPSEL, spiller.spillerId, path = "/", maxAge = 60 * 60, httpOnly = true)
      )

      // Og så en omdirigering, ikke en patch. Hendelsesstrømmen leser
      // kapselen når den åpnes, én gang, så den må åpnes på nytt for at
      // serveren skal vite hvem som sitter der.
      call.respondRedirect("/")
    }

    post("/svar") {
      val spillerId = call.request.cookies[KAPSEL]
      val signaler = call.signaler()

      if (spillerId != null) {
        spilltjener.svar(
          spillerId,
          Svar(
            vedtak = signaler["vedtak"]?.ifBlank { null },
            hjemmel = signaler["hjemmel"]?.ifBlank { null },
            felle = signaler["felle"]?.ifBlank { null },
          ),
        )
      }

      val tilstand = spilltjener.tilstand(spillerId)
      call.respondText(
        patch(topp(tilstand), brett(tilstand, tilstand.meg?.navn)),
        ContentType.parse("text/event-stream"),
      )
    }

    /**
     * Strømmen ned til nettleseren.
     *
     * Her går det **HTML**, ikke JSON. Det er hele forskjellen fra de to
     * andre appene: serveren har alt tegnet skjermen, og Datastar bytter ut
     * området.
     */
    sse("/hendelser") {
      val spillerId = call.request.cookies[KAPSEL]

      suspend fun send() {
        val tilstand = spilltjener.tilstand(spillerId)
        val html = patch(topp(tilstand), brett(tilstand, tilstand.meg?.navn))
        // `patch` lager hele SSE-rammen. Her trengs bare innmaten.
        val data = html.removePrefix("event: datastar-patch-elements\n").trimEnd()
        send(
          ServerSentEvent(
            event = "datastar-patch-elements",
            data = data.lines().joinToString("\n") { it.removePrefix("data: ") },
          )
        )
      }

      send()
      spilltjener.puls.collect { send() }
    }
  }
}
