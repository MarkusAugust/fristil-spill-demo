package no.fristil.forstelinja

import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Holder tjenesten våken en stund etter at den siste spilleren er borte.
 *
 * Railway lar en tjeneste sove når den ikke har sendt **utgående** trafikk på
 * fem til ti minutter, og vekker den på første forespørsel. Terskelen kan
 * ikke stilles. Det er bra for regningen, og for kort for en demo: kommer du
 * tilbake etter en kaffepause, skal omgangen være den samme, ikke en ny.
 *
 * Derfor dette. Så lenge noen ser på, sender tjenesten hendelser og er våken
 * av seg selv. Når den siste er borte, holder et oppslag mot navnetjeneren
 * hvert annet minutt den våken videre, i det antall timer `VARM_TIMER` sier.
 * Så slutter den, og Railway legger den i dvale.
 *
 * Oppslaget er det eneste denne fila gjør, og det finnes bare for å hindre
 * dvalen. Det er verdt å si rett ut: det er en fot i døra, ikke et
 * helsesjekk.
 *
 * Standarden er null, altså av. Den hører hjemme i drift og ingen andre
 * steder, og på en utviklermaskin skal ingenting slå opp navn i bakgrunnen
 * hvert annet minutt. På Railway settes `VARM_TIMER=3`.
 */
class Varme(private val timer: Double = System.getenv("VARM_TIMER")?.toDoubleOrNull() ?: 0.0) {
  private val seere = AtomicInteger(0)
  private val sistSett = AtomicLong(System.currentTimeMillis())

  fun abonner() {
    seere.incrementAndGet()
    sistSett.set(System.currentTimeMillis())
  }

  fun avmeld() {
    seere.decrementAndGet()
    sistSett.set(System.currentTimeMillis())
  }

  /** Sant så lenge noen ser på, eller den varme halen ikke er over. */
  fun erVarm(na: Long = System.currentTimeMillis()): Boolean {
    if (seere.get() > 0) return true
    return na - sistSett.get() < (timer * 60 * 60 * 1000).toLong()
  }

  /**
   * Løkka som holder foten i døra.
   *
   * Den sover to minutter om gangen, som er godt under Railways fem, og gjør
   * ingenting når halen er over. Feiler oppslaget, er det ingenting å gjøre
   * med det her: da er nettet borte, og da sover tjenesten uansett.
   */
  suspend fun hold() {
    if (timer <= 0) return
    while (true) {
      delay(2 * 60 * 1000)
      if (!erVarm()) continue
      // `Dispatchers.IO`, ikke tråden vi står på. `getByName` blokkerer, og
      // løkka kjører i den samme `runBlocking`-konteksten som tjeneren.
      // Uten dette sto hele spilltjeneren stille mens oppslaget pågikk, og
      // paritetsprøven feilet tilfeldig i en annen app for hver kjøring.
      withContext(Dispatchers.IO) { runCatching { InetAddress.getByName("railway.com") } }
    }
  }
}
