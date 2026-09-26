package no.fristil.forstelinja

/** En klokke testene stiller selv, så ingen test venter på den ekte. */
class FalskKlokke(var tid: Long = 0) : Klokke {
  override fun na() = tid

  fun gaa(ms: Long) {
    tid += ms
  }
}
