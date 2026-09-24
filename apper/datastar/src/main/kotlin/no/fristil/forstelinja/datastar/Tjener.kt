package no.fristil.forstelinja.datastar

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.util.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.util.cio.ChannelWriteException
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

    get("/tema.css") {
      call.respondText(TEMA_CSS, ContentType.Text.CSS)
    }

    get("/brett.css") {
      call.respondText(BRETT_CSS, ContentType.Text.CSS)
    }

    get("/panel.js") {
      call.respondText(PANEL_JS, ContentType.Text.JavaScript)
    }

    get("/panel-avlytt.js") {
      call.respondText(PANEL_AVLYTT_JS, ContentType.Text.JavaScript)
    }

    get("/") {
      /*
       * Billetten fra en annen utgave.
       *
       * I drift ligger de tre på hvert sitt domene, og en kapsel gjelder
       * bare for sitt eget. Bytter du utgave, kommer du derfor hit med
       * `?spiller=` i adressen, og her veksles den inn i vår egen kapsel.
       * Deretter en omdirigering, så billetten ikke blir stående i
       * adressefeltet og i historikken.
       */
      val billett = call.request.queryParameters["spiller"]
      if (billett != null) {
        val https = call.request.headers["X-Forwarded-Proto"] == "https"
        call.response.cookies.append(
          Cookie(
            KAPSEL,
            billett,
            path = "/",
            maxAge = 8 * 60 * 60,
            httpOnly = true,
            secure = https,
            extensions = mapOf("SameSite" to if (https) "None" else "Lax"),
          )
        )
        call.request.queryParameters["tema"]?.takeIf { it == "light" || it == "dark" }?.let {
          call.response.cookies.append(
            Cookie("forstelinja-tema", it, path = "/", maxAge = 60 * 60 * 24 * 365)
          )
        }
        call.respondRedirect("/")
        return@get
      }

      val spillerId = call.request.cookies[KAPSEL]
      val tilstand =
        try {
          spilltjener.tilstand(spillerId)
        } catch (e: Exception) {
          // Spilltjeneren er nede, eller er ikke kommet opp ennå. En rå
          // stakksporing fra Ktor sier ingenting til den som står og venter.
          call.respondText(venteside(), ContentType.Text.Html, HttpStatusCode.ServiceUnavailable)
          return@get
        }
      val navn = tilstand.meg?.navn
      // Temaet er brukerens valg, og ligger i en kapsel så serveren kan
      // skrive det inn i siden framfor å la et skript rette den etterpå.
      val tema = call.request.cookies["forstelinja-tema"]
      /*
       * Nettleseren sier selv at dette er en ramme. Hvorfor hilsenen da
       * utelates, står i README under «Velkomsthilsenen, og valget mellom de
       * tre».
       *
       * Overskriften sendes bare til en adresse nettleseren regner som
       * sikker: https, `localhost`, `127.0.0.1` og `::1`. Drift er https og
       * `./kjor.sh` er localhost, så begge de vanlige veiene virker. Åpner
       * noen skallet over vanlig http mot en maskin på nettet, altså
       * `http://192.168.x.x:8084`, kommer hilsenen tilbake i rammene.
       * Testet i alle tre motorene. Det er en dårligere visning, ikke en
       * ødelagt side, og reserven er å bruke adressen i drift.
       *
       * `Vary` fordi svaret avhenger av en overskrift, og `Cache-Control`
       * fordi det også avhenger av kapsler: uten den kunne en mellomtjener
       * gitt én spillers side til en annen, og da er hilsenen det minste
       * problemet.
       */
      val iRamme = call.request.headers["Sec-Fetch-Dest"] == "iframe"
      call.response.header(HttpHeaders.Vary, "Sec-Fetch-Dest, Cookie")
      call.response.header(HttpHeaders.CacheControl, "private, no-cache")
      call.respondText(
        side(tilstand, navn, kommuner, tema, spillerId, iRamme),
        ContentType.Text.Html,
      )
    }

    post("/bli-med") {
      val navn = call.receiveParameters()["navn"].orEmpty()
      /*
       * Paritetstesten melder seg på med en kapsel. Uten den ble «Test
       * datastar» stående i den evige topplista hver gang en vakt tok slutt
       * mens testen kjørte.
       *
       * En kapsel og ikke en overskrift: en egendefinert overskrift gjør en
       * forespørsel over domenegrensen til en som krever forhåndssjekk, og da
       * ble hentingen av Fristil fra CDN blokkert i nettleseren.
       */
      val erTest = call.request.cookies["forstelinja-test"] == "1"
      val spiller =
        try {
          spilltjener.bliMed(navn, erTest)
        } catch (e: Exception) {
          call.respondText(venteside(), ContentType.Text.Html, HttpStatusCode.ServiceUnavailable)
          return@post
        }

      // Kapsel og ikke minne i nettleseren: Astro-appen laster hele sider på
      // nytt, og alle tre skal oppføre seg likt.
      // `SameSite=None` i drift, `Lax` lokalt. Skallet viser de tre utgavene
      // i hver sin ramme, og i drift ligger de på hvert sitt domene. Da er
      // kapselen en tredjepartskapsel, og `Lax` gjør at nettleseren aldri
      // sender den: du kunne se spillet i rammen, men ikke melde deg på.
      // `None` krever `Secure`, altså https, og det har vi bare i drift.
      val https = call.request.headers["X-Forwarded-Proto"] == "https"
      call.response.cookies.append(
        Cookie(
          KAPSEL,
          spiller.spillerId,
          path = "/",
          maxAge = 8 * 60 * 60,
          httpOnly = true,
          secure = https,
          extensions = mapOf("SameSite" to if (https) "None" else "Lax"),
        )
      )

      // Og så en omdirigering, ikke en patch. Hendelsesstrømmen leser
      // kapselen når den åpnes, én gang, så den må åpnes på nytt for at
      // serveren skal vite hvem som sitter der.
      call.respondRedirect("/")
    }

    /**
     * Går av vakt, og blir borte fra tavla med en gang.
     *
     * Ingen knapp peker hit. Paritetstesten bruker den for å rydde etter
     * seg, og det er det samme kallet en «gå av vakt»-knapp ville gjort.
     */
    post("/ga-av") {
      val spillerId = call.request.cookies[KAPSEL]
      if (spillerId != null) runCatching { spilltjener.gaAv(spillerId) }
      call.response.cookies.append(Cookie(KAPSEL, "", path = "/", maxAge = 0))
      call.respondText("ok")
    }

    post("/svar") {
      val spillerId = call.request.cookies[KAPSEL]
      val signaler = call.signaler()

      val vedtak = signaler["vedtak"]?.ifBlank { null }
      val hjemmel = signaler["hjemmel"]?.ifBlank { null }
      val kommune = signaler["kommune"]?.ifBlank { null }
      // «nei» er et svar: saken er i orden. Spilltjeneren kjenner bare
      // feltnavn og `null`, så valget oversettes her.
      val felleValg = signaler["felle"]?.ifBlank { null }
      val felle = felleValg?.takeIf { it != "nei" }

      // Appen validerer skjemaet, spilltjeneren teller poeng. Et ufullstendig
      // vedtak sendes aldri videre; det er feiloppsummeringen som svarer.
      val feil = valider(vedtak, hjemmel, kommune, felleValg, kommuner)

      if (feil.isEmpty() && spillerId != null) {
        spilltjener.svar(
          spillerId,
          Svar(
            vedtak = vedtak,
            hjemmel = hjemmel,
            kommune = kommune,
            felle = felle,
          ),
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
      var sambandNede = false

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

      /**
       * Sier fra til nettleseren om at spilltjeneren er borte, eller tilbake.
       *
       * Å la strømmen ryke ville vært det opplagte, men Datastar kobler bare
       * til igjen når lesingen kaster, og en strøm som avsluttes pent gir
       * ingen ny forsøk. Enda verre: en app som ikke skriver noe, ryker
       * aldri i det hele tatt. Skjermene ble stående helt normale og aldri
       * oppdatert mer.
       *
       * Derfor sies det over strømmen i stedet, på et merke skriptet i siden
       * lytter på. Da kan strømmen bli stående, og spillet tar seg inn igjen
       * av seg selv når spilltjeneren er tilbake.
       */
      suspend fun meldSamband(nede: Boolean) {
        sendRamme(
          patch(
            """<div id="samband" hidden data-nede="$nede"></div>"""
          )
        )
      }

      suspend fun send() {
        val na =
          try {
            spilltjener.tilstand(spillerId)
          } catch (e: Exception) {
            if (!sambandNede) {
              sambandNede = true
              meldSamband(true)
            }
            return
          }

        if (sambandNede) {
          sambandNede = false
          meldSamband(false)
          // Alt kan ha skjedd mens vi var borte, så neste patch skal være
          // hele brettet.
          forrige = null
        }

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
          // `forr == null` betyr at strømmen nettopp ble åpnet, og det
          // skjer også når Datastar kobler til igjen etter et nettblink.
          // Å tømme skjemaet da ville tatt et halvutfylt vedtak fra
          // spilleren midt i runden.
          if (forr != null && erNySak(forr, na)) tomSkjemaet()

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

      // Én seer til. Oppstrømsforbindelsen åpnes her, første gang, og
      // lukkes når den siste fanen er borte. Da kan tjenestene sove når
      // ingen spiller.
      spilltjener.abonner(this@datastarModul)

      try {
        send()
        spilltjener.puls.collect { send() }
      } catch (_: ChannelWriteException) {
        // Nettleseren lukket fanen midt i en skriving. Det er ikke en feil,
        // og en stakksporing for hver som går hjem gjør loggen ubrukelig.
        //
        // Bare denne fanges. En feil oppstrøms, altså at spilltjeneren er
        // borte, må få strømmen til å ryke: Datastar kobler til igjen bare
        // når lesingen kaster, og avslutter vi pent, står nettleseren igjen
        // med en helt normal skjerm som aldri oppdaterer seg mer. Da hjelper
        // ingenting annet enn F5, og i et rom med ti skjermer dør alle
        // samtidig uten at noen ser det.
      } finally {
        spilltjener.avmeld(this@datastarModul)
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
