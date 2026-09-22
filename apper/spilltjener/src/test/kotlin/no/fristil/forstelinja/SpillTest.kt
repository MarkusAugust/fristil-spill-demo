package no.fristil.forstelinja

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * Reglene i Førstelinja.
 *
 * Klokka er falsk. Å vente ut tretti ekte sekunder per runde ville gjort
 * testene ubrukelige, og en test som venter på klokka feiler tilfeldig når
 * maskinen er travel.
 */
class SpillTest {
  private class FalskKlokke(var tid: Long = 0) : Klokke {
    override fun na() = tid

    fun gaa(ms: Long) {
      tid += ms
    }
  }

  private lateinit var klokke: FalskKlokke
  private lateinit var toppliste: Toppliste
  private lateinit var fil: java.io.File

  /** Én sak med felle, én uten, så begge veier kan prøves. */
  private val samling =
    Sakssamling(
      hjemler = listOf(Hjemmel("§ 4-1", "Alminnelig"), Hjemmel("§ 12-3", "Mangelfullt")),
      saker =
        (1..RUNDER_PER_SPILL).map { nr ->
          Sak(
            id = "sak-$nr",
            tittel = "Sak $nr",
            sammendrag = "…",
            soker = Soker("Navn", "01.01.1980", "Oslo", "navn@example.no"),
            fasit =
              Fasit(
                vedtak = if (nr % 2 == 0) Vedtak.INNVILGET else Vedtak.AVSLATT,
                hjemmel = if (nr % 2 == 0) "§ 4-1" else "§ 12-3",
                felle = if (nr % 2 == 0) null else "epost",
              ),
            forklaring = "Fordi.",
          )
        },
    )

  private fun nyttSpill() = Spill(samling, toppliste, klokke)

  @BeforeTest
  fun foer() {
    klokke = FalskKlokke(1_000_000)
    fil = java.io.File.createTempFile("toppliste", ".db")
    toppliste = Toppliste(fil.absolutePath)
  }

  @AfterTest
  fun etter() {
    fil.delete()
  }

  @Test
  fun `full pott krever vedtak, hjemmel og fella`() = runTest {
    val spill = nyttSpill()
    val spiller = spill.bliMed("Kari", Stack.TANSTACK)
    val fasit = spill.sak.fasit

    spill.svar(spiller.id, Svar(fasit.vedtak, fasit.hjemmel, fasit.felle))
    klokke.gaa(RUNDE_MS)
    spill.tikk()

    val meg = spill.tilstand(spiller.id).meg
    assertEquals(POENG_VEDTAK + POENG_HJEMMEL + POENG_FELLE, meg?.poeng)
  }

  @Test
  fun `å se at saken er i orden teller like mye som å se fella`() = runTest {
    val spill = nyttSpill()
    val spiller = spill.bliMed("Ola", Stack.DATASTAR)

    // Bare fella er riktig, vedtaket og hjemmelen med vilje feil.
    val feilVedtak = if (spill.sak.fasit.vedtak == Vedtak.INNVILGET) Vedtak.AVSLATT else Vedtak.INNVILGET
    spill.svar(spiller.id, Svar(feilVedtak, "finnes ikke", spill.sak.fasit.felle))
    klokke.gaa(RUNDE_MS)
    spill.tikk()

    assertEquals(POENG_FELLE, spill.tilstand(spiller.id).meg?.poeng)
  }

  @Test
  fun `den som ikke svarer får ingenting`() = runTest {
    val spill = nyttSpill()
    val spiller = spill.bliMed("Ingrid", Stack.ASTRO)

    klokke.gaa(RUNDE_MS)
    spill.tikk()

    assertEquals(0, spill.tilstand(spiller.id).meg?.poeng)
  }

  @Test
  fun `fasiten er skjult mens runden pågår`() = runTest {
    val spill = nyttSpill()
    val spiller = spill.bliMed("Kari", Stack.TANSTACK)

    // Ellers kunne hvem som helst lest svaret ut av nettverksfanen.
    assertNull(spill.tilstand(spiller.id).fasit)
    assertNull(spill.tilstand(spiller.id).forklaring)

    klokke.gaa(RUNDE_MS)
    spill.tikk()

    assertNotNull(spill.tilstand(spiller.id).fasit)
    assertNotNull(spill.tilstand(spiller.id).forklaring)
  }

  @Test
  fun `svar utenfor runden tas ikke imot`() = runTest {
    val spill = nyttSpill()
    val spiller = spill.bliMed("Ola", Stack.DATASTAR)

    klokke.gaa(RUNDE_MS)
    spill.tikk()
    assertEquals(Fase.OPPGJOR, spill.fase)

    val fasit = spill.sak.fasit
    assertFalse(spill.svar(spiller.id, Svar(fasit.vedtak, fasit.hjemmel, fasit.felle)))
    assertEquals(0, spill.tilstand(spiller.id).meg?.poeng)
  }

  @Test
  fun `runden avsluttes med én gang alle har svart`() = runTest {
    val spill = nyttSpill()
    val en = spill.bliMed("Kari", Stack.TANSTACK)
    val to = spill.bliMed("Ola", Stack.DATASTAR)

    spill.svar(en.id, Svar(Vedtak.INNVILGET, "§ 4-1", null))
    spill.avsluttHvisAlleHarSvart()
    assertEquals(Fase.RUNDE, spill.fase, "én av to har svart, runden skal gå videre")

    spill.svar(to.id, Svar(Vedtak.INNVILGET, "§ 4-1", null))
    spill.avsluttHvisAlleHarSvart()
    assertEquals(Fase.OPPGJOR, spill.fase)
  }

  @Test
  fun `fire runder, så slutt, så ny omgang`() = runTest {
    val spill = nyttSpill()
    val spiller = spill.bliMed("Kari", Stack.TANSTACK)

    repeat(RUNDER_PER_SPILL) { runde ->
      assertEquals(Fase.RUNDE, spill.fase)
      assertEquals(runde + 1, spill.rundeNr)
      val fasit = spill.sak.fasit
      spill.svar(spiller.id, Svar(fasit.vedtak, fasit.hjemmel, fasit.felle))

      klokke.gaa(RUNDE_MS)
      spill.tikk()
      assertEquals(Fase.OPPGJOR, spill.fase)

      klokke.gaa(OPPGJOR_MS)
      spill.tikk()
    }

    assertEquals(Fase.SLUTT, spill.fase)
    assertEquals(RUNDER_PER_SPILL * 20, spill.tilstand(spiller.id).meg?.poeng)

    klokke.gaa(SLUTT_MS)
    spill.tikk()

    assertEquals(Fase.RUNDE, spill.fase)
    assertEquals(1, spill.rundeNr)
    assertEquals(0, spill.tilstand(spiller.id).meg?.poeng, "ny omgang starter på null")
    assertEquals(
      "Kari",
      spill.tilstand(spiller.id).meg?.navn,
      "spilleren blir med videre, og slipper å skrive navnet sitt hvert tredje minutt",
    )
  }

  @Test
  fun `resultatet havner på den evige topplista`() = runTest {
    val spill = nyttSpill()
    val spiller = spill.bliMed("Kari", Stack.TANSTACK)

    repeat(RUNDER_PER_SPILL) {
      val fasit = spill.sak.fasit
      spill.svar(spiller.id, Svar(fasit.vedtak, fasit.hjemmel, fasit.felle))
      klokke.gaa(RUNDE_MS)
      spill.tikk()
      klokke.gaa(OPPGJOR_MS)
      spill.tikk()
    }

    val topp = toppliste.topp(10)
    assertEquals(1, topp.size)
    assertEquals("Kari", topp[0].navn)
    assertEquals(RUNDER_PER_SPILL * 20, topp[0].poeng)
    assertEquals(Stack.TANSTACK, topp[0].stack)
  }

  @Test
  fun `tavla er sortert, og sier hvilken stack hver spiller sitter i`() = runTest {
    val spill = nyttSpill()
    val svak = spill.bliMed("Svak", Stack.ASTRO)
    val sterk = spill.bliMed("Sterk", Stack.DATASTAR)

    val fasit = spill.sak.fasit
    spill.svar(sterk.id, Svar(fasit.vedtak, fasit.hjemmel, fasit.felle))
    klokke.gaa(RUNDE_MS)
    spill.tikk()

    val tavle = spill.tilstand(svak.id).tavle
    assertEquals(listOf("Sterk", "Svak"), tavle.map { it.navn })
    assertEquals(listOf(Stack.DATASTAR, Stack.ASTRO), tavle.map { it.stack })
    assertTrue(tavle.first { it.navn == "Svak" }.erMeg)
  }

  @Test
  fun `tomt navn blir til Anonym, og lange navn kuttes`() = runTest {
    val spill = nyttSpill()
    assertEquals("Anonym", spill.bliMed("   ", Stack.UKJENT).navn)
    assertEquals(24, spill.bliMed("x".repeat(90), Stack.UKJENT).navn.length)
  }
}
