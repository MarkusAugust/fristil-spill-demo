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

/**
 * Hvordan HTML-en settes inn. Datastars egne navn.
 *
 * `outer` er standarden og morfer hele elementet. `inner` morfer bare
 * innmaten, og er den vi bruker når vi peker ut et område med `selector`.
 */
enum class Modus(val verdi: String) {
  OUTER("outer"),
  INNER("inner"),
}

/**
 * Ett Datastar-event, med hver linje HTML på sin egen `data:`-linje.
 *
 * `selector` er det som gjør patchene smale. Sender vi bare tavla når bare
 * tavla har endret seg, røres verken skjemaet eller fanene, og da kan
 * brukerens tilstand heller ikke gå tapt. Det er den vanlige måten å bruke
 * Datastar på, og den fjerner behovet for `data-preserve-attr` alle steder
 * der serveren ikke trenger å sende noe.
 */
fun patch(
  html: String,
  selector: String? = null,
  modus: Modus = Modus.OUTER,
): String = buildString {
  append("event: datastar-patch-elements\n")
  if (modus != Modus.OUTER) append("data: mode ${modus.verdi}\n")
  selector?.let { append("data: selector $it\n") }
  html.lines().forEach { append("data: elements $it\n") }
  append("\n")
}

/** Flere biter i ett event, alle med `outer`. */
fun patch(vararg biter: String): String = patch(biter.joinToString("\n"))

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

fun Application.datastarModul(spilltjener: Spilltjener, kommuner: List<String>) {
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

      val vedtak = signaler["vedtak"]?.ifBlank { null }
      val hjemmel = signaler["hjemmel"]?.ifBlank { null }
      val kommune = signaler["kommune"]?.ifBlank { null }

      // Appen validerer skjemaet, spilltjeneren teller poeng. Et ufullstendig
      // vedtak sendes aldri videre; det er feiloppsummeringen som svarer.
      val feil = valider(vedtak, hjemmel, kommune, kommuner)

      if (feil.isEmpty() && spillerId != null) {
        spilltjener.svar(
          spillerId,
          Svar(vedtak = vedtak, hjemmel = hjemmel, felle = signaler["felle"]?.ifBlank { null }),
        )
      }

      val tilstand = spilltjener.tilstand(spillerId)
      call.respondText(
        patch(topp(tilstand), brett(tilstand, tilstand.meg?.navn, kommuner, feil)),
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

      // Forrige tilstand, så vi kan sende bare det som faktisk har endret
      // seg. Endres tavla, sendes tavla. Skifter runden, sendes saken også.
      var forrige: Tilstand? = null

      suspend fun sendRamme(html: String) {
        val innmat =
          html
            .removePrefix("event: datastar-patch-elements\n")
            .trimEnd()
            .lines()
            .joinToString("\n") { it.removePrefix("data: ") }
        send(ServerSentEvent(event = "datastar-patch-elements", data = innmat))
      }

      /**
       * Tømmer skjemaet når en ny sak kommer på bordet.
       *
       * Signalene bor i nettleseren og overlever en morfing. Uten dette sto
       * forrige rundes vedtak ferdig avkrysset i den nye saken, sammen med
       * hjemmelen og kommunen, og en spiller kunne sende inn uten å ha tatt
       * stilling til noe. Serveren vet når en ny sak begynner, så det er
       * serveren som nullstiller.
       */
      suspend fun tomSkjemaet() {
        send(
          ServerSentEvent(
            event = "datastar-patch-signals",
            data = """signals {"vedtak": "", "hjemmel": "", "kommune": "", "felle": ""}""",
          )
        )
      }

      suspend fun send() {
        val na = spilltjener.tilstand(spillerId)
        val forr = forrige
        forrige = na

        val nyRunde =
          forr == null ||
            forr.fase != na.fase ||
            forr.rundeNr != na.rundeNr ||
            forr.sak.id != na.sak.id ||
            forr.meg?.navn != na.meg?.navn ||
            forr.meg?.harSvart != na.meg?.harSvart

        if (nyRunde) {
          if (erNySak(forr, na)) tomSkjemaet()

          // Hele brettet. Her er det serveren som eier innholdet uansett.
          sendRamme(patch(topp(na), brett(na, na.meg?.navn, kommuner)))
          return
        }

        if (forr.tavle != na.tavle) {
          // Bare tavla. Skjemaet brukeren står i røres ikke i det hele tatt,
          // og da kan heller ingenting gå tapt.
          sendRamme(patch(tavle(na), selector = "#tavle", modus = Modus.INNER))
        }
      }

      try {
        send()
        spilltjener.puls.collect { send() }
      } catch (_: java.io.IOException) {
        // Nettleseren lukket fanen midt i en skriving. Det er ikke en feil,
        // og en stakksporing for hver som går hjem gjør loggen ubrukelig.
      }
    }
  }
}

/**
 * Om det er en ny sak på bordet, og skjemaet dermed skal stå blankt.
 *
 * Dette er ikke det samme som en ny fase. Går spillet fra runde til oppgjør,
 * står saken fast, og svaret skal bli stående til det er talt opp.
 */
fun erNySak(forrige: Tilstand?, na: Tilstand): Boolean =
  forrige == null || forrige.sak.id != na.sak.id || forrige.rundeNr != na.rundeNr
