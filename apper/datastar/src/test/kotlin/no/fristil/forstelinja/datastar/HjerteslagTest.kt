package no.fristil.forstelinja.datastar

import io.github.markusaugust.streamlord.ktor.respondDatastar
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.cio.ChannelWriteException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/**
 * Hvor det havner når nettleseren er borte og bare hjerteslaget skriver.
 *
 * `/hendelser` fanger `ChannelWriteException` utenfor `respondDatastar`, og
 * melder av seeren i en `finally` inne i blokken. Det bygger på hvordan
 * Streamlord gjør det: slaget går ved siden av blokken, og ryker det, blir
 * blokken avbrutt og skrivefeilen kastet ut av `respondDatastar`. Skulle en
 * senere Streamlord svelge feilen, eller kaste den inne i blokken, ville
 * loggen fylles med stakkspor for hver som går hjem, eller seeren aldri
 * meldes av. Denne testen sier fra før det skjer i drift.
 *
 * Den kjører mot CIO, som appen gjør, og ikke mot Ktors testmotor, fordi det
 * er motoren som avgjør hva en lukket forbindelse gir.
 */
class HjerteslagTest {
  @Test
  fun `en lukket fane avbryter blokken og kaster skrivefeilen ut`() {
    val hendt = CopyOnWriteArrayList<String>()
    val server =
      embeddedServer(CIO, port = 0) {
        routing {
          get("/strom") {
            try {
              call.respondDatastar(heartbeat = 100.milliseconds) {
                patchElements("""<div id="a">en</div>""")
                try {
                  awaitCancellation()
                } catch (e: CancellationException) {
                  hendt += "blokken avbrutt"
                  throw e
                } finally {
                  hendt += "finally i blokken"
                }
              }
              hendt += "returnerte"
            } catch (_: ChannelWriteException) {
              hendt += "skrivefeil ute"
            }
          }
        }
      }.start(wait = false)

    try {
      val port = runBlocking { server.engine.resolvedConnectors().first().port }
      Socket("127.0.0.1", port).use { s ->
        s.getOutputStream().write("GET /strom HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
        s.getInputStream().read(ByteArray(4096))
      }
      // Noen slag havner i operativsystemets buffer før skrivingen feiler.
      val frist = System.currentTimeMillis() + 10_000
      while (hendt.size < 3 && System.currentTimeMillis() < frist) Thread.sleep(50)
    } finally {
      server.stop(100, 500)
    }

    assertEquals(listOf("blokken avbrutt", "finally i blokken", "skrivefeil ute"), hendt.toList())
  }
}
