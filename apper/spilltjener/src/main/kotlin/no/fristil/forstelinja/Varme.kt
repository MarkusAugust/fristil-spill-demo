package no.fristil.forstelinja

import java.net.InetAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay

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
 * `VARM_TIMER=0` slår det av, og da sovner tjenesten så snart Railway vil.
 */
class Varme(private val timer: Double = System.getenv("VARM_TIMER")?.toDoubleOrNull() ?: 3.0) {
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
      runCatching { InetAddress.getByName("railway.com") }
    }
  }
}
