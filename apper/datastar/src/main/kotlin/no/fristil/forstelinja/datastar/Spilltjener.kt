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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.Json

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
   * Lytter på spilltjeneren, og kobler til igjen om forbindelsen ryker.
   *
   * SSE leses linje for linje framfor med en klientutvidelse. Formatet er
   * tre linjer og en blank, og en håndskrevet leser er lettere å feilsøke
   * enn et lag til.
   */
  suspend fun lytt() {
    while (true) {
      try {
        klient.prepareGet("$adresse/api/hendelser").execute { svar ->
          val kanal: ByteReadChannel = svar.bodyAsChannel()
          while (!kanal.isClosedForRead) {
            val linje = kanal.readUTF8Line() ?: break
            if (linje.startsWith("data:")) puls.emit(Unit)
          }
        }
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
