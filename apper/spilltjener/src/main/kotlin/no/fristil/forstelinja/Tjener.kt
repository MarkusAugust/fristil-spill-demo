package no.fristil.forstelinja

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.sse.*
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * HTTP-flaten til spilltjeneren.
 *
 * Den sender JSON og aldri HTML. Det er appene som lager HTML, hver på sin
 * måte, og det er nettopp forskjellen mellom dem demoen skal vise.
 *
 * Tjeneren er ment å være intern. På Railway snakker appene med den over
 * det private nettet, og den har ikke noe offentlig domene. CORS er derfor
 * bare for lokal utvikling.
 */

@Serializable data class BliMedInn(val navn: String, val stack: Stack = Stack.UKJENT)

@Serializable data class BliMedUt(val spillerId: String, val navn: String)

@Serializable data class SvarInn(val spillerId: String, val svar: Svar)

@Serializable data class Kvittering(val ok: Boolean, val grunn: String? = null)

val tjenerJson = Json {
  prettyPrint = false
  encodeDefaults = true
}

fun Application.spillModul(spill: Spill, varme: Varme = Varme()) {
  install(ContentNegotiation) { json(tjenerJson) }
  install(SSE)
  install(CORS) {
    anyHost()
    allowHeader(HttpHeaders.ContentType)
    allowMethod(HttpMethod.Post)
  }

  routing {
    get("/helse") { call.respondText("ok") }

    post("/api/bli-med") {
      val inn = call.receive<BliMedInn>()
      val spiller = spill.bliMed(inn.navn, inn.stack)
      call.respond(BliMedUt(spillerId = spiller.id, navn = spiller.navn))
    }

    post("/api/svar") {
      val inn = call.receive<SvarInn>()
      val godtatt = spill.svar(inn.spillerId, inn.svar)
      // Har alle svart, er det ingen grunn til å vente ut fristen.
      if (godtatt) spill.avsluttHvisAlleHarSvart()
      call.respond(
        if (godtatt) Kvittering(true)
        else Kvittering(false, "Runden tar ikke imot svar nå, spilleren er ukjent, eller vedtaket er alt fattet")
      )
    }


    get("/api/tilstand") {
      call.respond(spill.tilstand(call.request.queryParameters["spiller"]))
    }

    /**
     * Hendelsesstrømmen.
     *
     * Hver app holder én forbindelse hit og deler ut til sine egne
     * nettlesere. Hundre spillere på React-appen gir altså én forbindelse
     * her, ikke hundre.
     */
    sse("/api/hendelser") {
      val spillerId = call.request.queryParameters["spiller"]
      // Én app til som ser på. Varmen holder tjenesten våken en stund etter
      // at den siste er borte, slik at en kaffepause ikke starter en ny
      // omgang.
      varme.abonner()
      try {
        send(ServerSentEvent(data = tjenerJson.encodeToString(spill.tilstand(spillerId)), event = "tilstand"))
        spill.endringer.collect {
          send(
            ServerSentEvent(
              data = tjenerJson.encodeToString(spill.tilstand(spillerId)),
              event = "tilstand",
            )
          )
        }
      } finally {
        varme.avmeld()
      }
    }
  }
}
