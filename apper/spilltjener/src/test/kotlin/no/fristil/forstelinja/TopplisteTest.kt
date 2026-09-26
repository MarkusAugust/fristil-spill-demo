package no.fristil.forstelinja

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Den evige topplista skal vise personer, ikke omganger.
 *
 * Spiller den samme saksbehandleren fem ganger, er det det beste resultatet
 * som hører hjemme i lista. Og «Markus», «markus» og «Markus » er den samme
 * personen som skrev navnet sitt i hui og hast.
 */
class TopplisteTest {
  private val fil = File.createTempFile("toppliste", ".db").apply { delete() }
  private val klokke = FalskKlokke(1_000_000)
  private val toppliste = Toppliste(fil.absolutePath, klokke)

  @AfterTest
  fun rydd() {
    fil.delete()
  }

  @Test
  fun `samme navn står én gang, med det beste resultatet`() {
    toppliste.lagre("Markus", 40, Stack.DATASTAR)
    toppliste.lagre("Markus", 75, Stack.ASTRO)
    toppliste.lagre("Markus", 20, Stack.TANSTACK)

    val topp = toppliste.topp(10)

    assertEquals(1, topp.size, "spilleren står flere ganger: $topp")
    assertEquals(75, topp.first().poeng)
    assertEquals(Stack.ASTRO, topp.first().stack, "raden med flest poeng bestemmer")
  }

  @Test
  fun `store og små bokstaver er den samme spilleren`() {
    toppliste.lagre("Markus", 40, Stack.DATASTAR)
    toppliste.lagre("markus", 90, Stack.ASTRO)

    val topp = toppliste.topp(10)

    assertEquals(1, topp.size, "samme navn med ulik skrivemåte står to ganger: $topp")
    assertEquals(90, topp.first().poeng)
  }

  @Test
  fun `mellomrom i endene er ikke et nytt navn`() {
    toppliste.lagre("Kari ", 30, Stack.ASTRO)
    toppliste.lagre("  Kari", 55, Stack.DATASTAR)

    val topp = toppliste.topp(10)

    assertEquals(1, topp.size, "mellomrom laget en ny spiller: $topp")
    assertEquals("Kari", topp.first().navn)
    assertEquals(55, topp.first().poeng)
  }

  @Test
  fun `to ulike spillere står begge to`() {
    toppliste.lagre("Kari", 30, Stack.ASTRO)
    toppliste.lagre("Ola", 55, Stack.DATASTAR)

    assertEquals(listOf("Ola", "Kari"), toppliste.topp(10).map { it.navn })
  }

  @Test
  fun `tidsstempelet kommer fra klokka, og den som kom først vinner ved likt`() {
    toppliste.lagre("Kari", 55, Stack.ASTRO)
    klokke.gaa(60_000)
    toppliste.lagre("Ola", 55, Stack.DATASTAR)

    val topp = toppliste.topp(10)

    assertEquals(listOf("Kari", "Ola"), topp.map { it.navn }, "likt resultat skal stå i den rekkefølgen det kom")
    assertEquals(listOf(1_000_000L, 1_060_000L), topp.map { it.nar }, "raden fikk ikke tida fra klokka")
  }
}
