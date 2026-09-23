package no.fristil.forstelinja

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Den varme halen skal holde tjenesten våken etter siste spiller, og bare
 * så lenge den er satt til.
 */
class VarmeTest {
  @Test
  fun `er varm så lenge noen ser på`() {
    val varme = Varme(timer = 0.0)
    varme.abonner()

    assertTrue(varme.erVarm(), "en tjeneste med en seer er alltid varm")
  }

  @Test
  fun `holder varmen en stund etter at den siste er borte`() {
    val varme = Varme(timer = 3.0)
    varme.abonner()
    varme.avmeld()

    val na = System.currentTimeMillis()
    assertTrue(varme.erVarm(na + 2 * 60 * 60 * 1000), "sovnet før halen var over")
    assertFalse(varme.erVarm(na + 4 * 60 * 60 * 1000), "ble aldri kald")
  }

  @Test
  fun `null timer betyr at den sovner med en gang`() {
    val varme = Varme(timer = 0.0)
    varme.abonner()
    varme.avmeld()

    assertFalse(varme.erVarm(System.currentTimeMillis() + 1000), "holdt varmen uten å være bedt om det")
  }
}
