package no.fristil.forstelinja

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Den varme halen skal holde tjenesten våken etter siste spiller, og bare
 * så lenge den er satt til.
 *
 * Klokka er falsk, den samme som spillet testes med. Varmen leser tida på
 * fire steder, og en test som regner ut framtida fra systemklokka selv kan
 * ikke se om ett av dem leser feil klokke.
 */
class VarmeTest {
  private val time = 60 * 60 * 1000L

  @Test
  fun `er varm så lenge noen ser på`() {
    val klokke = FalskKlokke(1_000_000)
    val varme = Varme(timer = 0.0, klokke = klokke)
    varme.abonner()

    klokke.gaa(24 * time)
    assertTrue(varme.erVarm(), "en tjeneste med en seer er alltid varm")
  }

  @Test
  fun `holder varmen en stund etter at den siste er borte`() {
    val klokke = FalskKlokke(1_000_000)
    val varme = Varme(timer = 3.0, klokke = klokke)
    varme.abonner()
    varme.avmeld()

    klokke.gaa(2 * time)
    assertTrue(varme.erVarm(), "sovnet før halen var over")
    klokke.gaa(2 * time)
    assertFalse(varme.erVarm(), "ble aldri kald")
  }

  @Test
  fun `halen regnes fra da den siste gikk, ikke fra da tjenesten startet`() {
    val klokke = FalskKlokke(1_000_000)
    val varme = Varme(timer = 1.0, klokke = klokke)
    klokke.gaa(5 * time)
    varme.abonner()
    varme.avmeld()

    klokke.gaa(time / 2)
    assertTrue(varme.erVarm(), "regnet halen fra starten, ikke fra siste seer")
  }

  @Test
  fun `standarden er av, så ingenting slår opp navn på en utviklermaskin`() {
    // `VARM_TIMER` settes i drift og leses i `Main.kt`. Uten den skal
    // klassen selv være av: ingen bakgrunnsløkke, ingen oppslag.
    val klokke = FalskKlokke(1_000_000)
    val varme = Varme(klokke = klokke)
    varme.abonner()
    varme.avmeld()

    klokke.gaa(1000)
    assertFalse(varme.erVarm(), "holdt varmen uten å være bedt om det")
  }

  @Test
  fun `null timer betyr at den sovner med en gang`() {
    val klokke = FalskKlokke(1_000_000)
    val varme = Varme(timer = 0.0, klokke = klokke)
    varme.abonner()
    varme.avmeld()

    klokke.gaa(1000)
    assertFalse(varme.erVarm(), "holdt varmen uten å være bedt om det")
  }
}
