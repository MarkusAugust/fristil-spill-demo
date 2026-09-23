package no.fristil.forstelinja.datastar

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Hvor lenge strømmen står åpen etter at den siste nettleseren er borte. */
private const val PUSTEROM_MS = 60_000L

/**
 * Klienten mot spilltjeneren.
 *
 * Appserveren snakker med spilltjeneren, aldri nettleseren. Det er slik
 * TanStack, Astro og denne appen alle er ment å virke, det holder
 * spilltjeneren intern, og det gjør at den eneste forskjellen mellom de tre
 * appene er hva som går fra appserveren og ned til nettleseren.
 */
class Spilltjener(private val adresse: String) {
  private val klient =
    HttpClient(CIO) {
      install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
      // Uten dette dør hendelsesstrømmen med én gang. Ktor-klienten har et
      // standard tidsavbrudd på forespørsler, og en SSE-strøm blir aldri
      // ferdig, så den ble avbrutt før den rakk å si fra om noe.
      install(HttpTimeout) { requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS }
    }

  /**
   * Slår ut hver gang spilltjeneren melder en endring.
   *
   * Appen holder **én** forbindelse oppstrøms, uansett hvor mange som
   * spiller her. Selve tilstanden er delvis personlig, «dine poeng, din
   * plass», så hver nettleser får sin egen etterpå. Strømmen sier altså
   * *når* noe skjedde, ikke *hva* hver enkelt skal se.
   */
  val puls = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 8)

  suspend fun tilstand(spillerId: String?): Tilstand =
    klient
      .get("$adresse/api/tilstand") { spillerId?.let { parameter("spiller", it) } }
      .body()

  suspend fun bliMed(navn: String): BliMedUt =
    klient
      .post("$adresse/api/bli-med") {
        contentType(ContentType.Application.Json)
        setBody(BliMedInn(navn = navn, stack = "datastar"))
      }
      .body()

  suspend fun svar(spillerId: String, svar: Svar) {
    klient.post("$adresse/api/svar") {
      contentType(ContentType.Application.Json)
      setBody(SvarInn(spillerId, svar))
    }
  }

  /**
   * Antallet nettlesere som ser på akkurat nå, og strømmen som følger dem.
   *
   * Oppstrømsforbindelsen åpnes først når noen faktisk ser på, og lukkes når
   * den siste er borte. Det er ikke bare ryddighet: Railway lar en tjeneste
   * sove når den ikke har sendt utgående trafikk på fem til ti minutter, og
   * en strøm som står åpen døgnet rundt er nettopp slik trafikk. Med dette
   * sovner både appen og spilltjeneren når ingen spiller.
   *
   * Pusterommet er der fordi en oppfriskning av siden er to hendelser, en
   * avmelding og en påmelding, med et lite øyeblikk imellom.
   */
  private val seere = java.util.concurrent.atomic.AtomicInteger(0)
  private var lytterJobb: Job? = null
  private var nedtelling: Job? = null

  /**
   * Av- og påmelding må virke i en kansellert koroutine.
   *
   * Når nettleseren lukker fanen, blir strømmens koroutine kansellert, og
   * `finally` kjører. Alt som suspenderer der kaster med en gang, så en
   * `Mutex` gjorde at avmeldingen aldri skjedde: telleren ble stående over
   * null, strømmen mot spilltjeneren ble aldri lukket, og ingen av
   * tjenestene sovnet. Derfor vanlig `synchronized` og ingen suspensjon.
   */
  @Synchronized
  fun abonner(scope: CoroutineScope) {
    seere.incrementAndGet()
    nedtelling?.cancel()
    nedtelling = null
    if (lytterJobb?.isActive != true) lytterJobb = scope.launch { lytt() }
  }

  @Synchronized
  fun avmeld(scope: CoroutineScope) {
    if (seere.decrementAndGet() > 0) return
    nedtelling?.cancel()
    nedtelling =
      scope.launch {
        delay(PUSTEROM_MS)
        stoppHvisTom()
      }
  }

  @Synchronized
  private fun stoppHvisTom() {
    if (seere.get() > 0) return
    lytterJobb?.cancel()
    lytterJobb = null
  }

  /**
   * Lytter på spilltjeneren, og kobler til igjen om forbindelsen ryker.
   *
   * SSE leses linje for linje framfor med en klientutvidelse. Formatet er
   * tre linjer og en blank, og en håndskrevet leser er lettere å feilsøke
   * enn et lag til.
   */
  private suspend fun lytt() {
    while (true) {
      try {
        klient.prepareGet("$adresse/api/hendelser").execute { svar ->
          val kanal: ByteReadChannel = svar.bodyAsChannel()
          while (!kanal.isClosedForRead) {
            val linje = kanal.readUTF8Line() ?: break
            if (linje.startsWith("data:")) puls.emit(Unit)
          }
        }
      } catch (e: CancellationException) {
        // Den siste nettleseren er borte, og strømmen skal lukkes.
        throw e
      } catch (e: Exception) {
        println("Mistet spilltjeneren (${e.message}). Prøver igjen om to sekunder.")
      }

      // Et pulsslag også når forbindelsen ryker, ikke bare når noe skjer.
      //
      // Uten dette hadde appen ingenting å sende ned til nettleserne, og en
      // strøm som ikke skriver ryker aldri. Skjermene ble stående helt
      // normale og aldri oppdatert mer, uten at noen kunne se det. Med
      // pulsen prøver hver strøm å hente tilstanden, det kallet feiler, og
      // strømmen ryker slik den skal: Datastar kobler til igjen, og
      // sambandslinja sier fra.
      puls.emit(Unit)
      delay(2000)
    }
  }
}
