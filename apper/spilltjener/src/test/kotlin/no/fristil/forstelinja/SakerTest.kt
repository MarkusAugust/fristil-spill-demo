package no.fristil.forstelinja

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * At sakene i `felles/saker.json` henger sammen.
 *
 * Fila er delt med tre apper og redigeres for hånd. En fasit som viser til
 * en hjemmel som ikke finnes, eller en felle som ikke er et felt, ville
 * ellers først vist seg som et spill der ingen kan få full pott.
 */
class SakerTest {
  private val enHjemmel = listOf(Hjemmel("§ 4-1", "Alminnelig"))

  private fun sak(id: String, hjemmel: String = "§ 4-1", felle: String? = null) =
    Sak(
      id = id,
      tittel = "T",
      sammendrag = "S",
      tekst = "Selve søknaden.",
      soker = Soker("N", "01.01.1980", "Oslo", "n@example.no"),
      fasit = Fasit(Vedtak.INNVILGET, hjemmel, felle),
      forklaring = "F",
    )

  @Test
  fun `den ekte fila er gyldig`() {
    val samling = lesSaker("../../felles/saker.json")

    assertTrue(samling.saker.size >= RUNDER_PER_SPILL)
    assertTrue(samling.hjemler.isNotEmpty())
  }

  @Test
  fun `for få saker til en omgang avvises`() {
    assertFailsWith<IllegalArgumentException> {
      Sakssamling(hjemler = enHjemmel, saker = listOf(sak("a")))
    }
  }

  @Test
  fun `to saker med samme id avvises`() {
    assertFailsWith<IllegalArgumentException> {
      Sakssamling(hjemler = enHjemmel, saker = (1..RUNDER_PER_SPILL).map { sak("lik") })
    }
  }

  @Test
  fun `fasit som viser til en ukjent hjemmel avvises`() {
    val feil =
      assertFailsWith<IllegalArgumentException> {
        Sakssamling(
          hjemler = enHjemmel,
          saker = (1..RUNDER_PER_SPILL).map { sak("s$it", hjemmel = "§ 99-9") },
        )
      }
    assertTrue(feil.message!!.contains("§ 99-9"))
  }

  @Test
  fun `en felle som ikke er et felt avvises`() {
    assertFailsWith<IllegalArgumentException> {
      Sakssamling(
        hjemler = enHjemmel,
        saker = (1..RUNDER_PER_SPILL).map { sak("s$it", felle = "frisyre") },
      )
    }
  }

  @Test
  fun `nøyaktig én sak har en kommune som ikke finnes`() {
    // Det er fella i 2024/1902: Mosvik ble slått sammen med Inderøy i 2012.
    // Er det flere, har noen ved et uhell laget en felle til, og da blir
    // spillet urettferdig.
    val samling = lesSaker("../../felles/saker.json")
    val kommuner = lesKommuner("../../felles/kommuner.json")

    val utenfor = samling.saker.filter { it.soker.kommune !in kommuner }

    assertEquals(1, utenfor.size, "fant ${utenfor.size} saker med ukjent kommune: ${utenfor.map { it.id }}")
    assertEquals("kommune", utenfor.single().fasit.felle, "den ene må være kommunefella")
  }

  @Test
  fun `hver ekte sak har en forklaring som sier hvorfor`() {
    val samling = lesSaker("../../felles/saker.json")

    val uten = samling.saker.filter { it.forklaring.length < 20 }
    assertEquals(emptyList(), uten.map { it.id }, "forklaringen er der vitsen lander")
  }

  @Test
  fun `hver ekte sak har en søknadstekst`() {
    // Teksten er det spilleren faktisk leser. En sak uten den ville stått
    // som en overskrift og et tomt kort.
    val samling = lesSaker("../../felles/saker.json")

    for (sak in samling.saker) {
      assertTrue(sak.tekst.length > 60, "${sak.id} har ingen søknadstekst å lese")
    }
  }
}
