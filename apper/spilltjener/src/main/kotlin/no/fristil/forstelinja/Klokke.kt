package no.fristil.forstelinja

/**
 * Klokka, slik at testene slipper å vente i ekte sekunder.
 *
 * Alt som leser tida i spilltjeneren går gjennom denne: spillet for fristene,
 * varmen for halen etter siste spiller, og topplista for tidsstempelet på
 * raden. Én klokke gjør at en test kan stille alle tre samtidig, og at ingen
 * av dem leser systemklokka på egen hånd bak ryggen på de andre.
 */
fun interface Klokke {
  fun na(): Long

  companion object {
    /** Systemklokka. Den eneste som skal brukes utenfor en test. */
    val system = Klokke { System.currentTimeMillis() }
  }
}
