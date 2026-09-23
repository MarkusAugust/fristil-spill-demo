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
            tekst = "Selve søknaden.",
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

  private val kommuner = listOf("Oslo", "Bergen")

  private fun nyttSpill() = Spill(samling, toppliste, klokke, kommuner = kommuner)

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
  fun `full pott krever vedtak, hjemmel, kommune og fella`() = runTest {
    val spill = nyttSpill()
    val spiller = spill.bliMed("Kari", Stack.TANSTACK)
    val fasit = spill.sak.fasit

    spill.svar(
      spiller.id,
      Svar(fasit.vedtak, fasit.hjemmel, spill.sak.soker.kommune, fasit.felle),
    )
    klokke.gaa(RUNDE_MS)
    spill.tikk()

    val meg = spill.tilstand(spiller.id).meg
    assertEquals(POENG_FULL_POTT, meg?.poeng)
    assertEquals(25, POENG_FULL_POTT, "full pott er 25 poeng")
  }

  @Test
  fun `en kommune som ikke finnes skal svares med tomt felt`() = runTest {
    // Én av sakene viser til en kommune som ble slått sammen med en annen.
    // Da finnes den ikke i registeret, og det riktige er å la feltet stå
    // tomt. Forslagsfeltet sier fra med «Ingen treff» mens du skriver.
    val samlingMedUkjentKommune =
      samling.copy(
        saker =
          samling.saker.mapIndexed { nr, sak ->
            if (nr == 0) sak.copy(soker = sak.soker.copy(kommune = "Mosvik")) else sak
          }
      )
    val spill = Spill(samlingMedUkjentKommune, toppliste, klokke, kommuner = kommuner)
    val spiller = spill.bliMed("Kari", Stack.TANSTACK)

    val ukjent = spill.sak.soker.kommune !in kommuner

    spill.svar(spiller.id, Svar(kommune = null))
    val vurdering = spill.tilstand(spiller.id).meg?.vurdering
    assertNotNull(vurdering)
    assertEquals(ukjent, vurdering.kommuneRiktig, "tomt felt er riktig bare når kommunen mangler")
  }

  @Test
  fun `å se at saken er i orden teller like mye som å se fella`() = runTest {
    val spill = nyttSpill()
    val spiller = spill.bliMed("Ola", Stack.DATASTAR)

    // Bare fella er riktig, resten med vilje feil.
    val feilVedtak = if (spill.sak.fasit.vedtak == Vedtak.INNVILGET) Vedtak.AVSLATT else Vedtak.INNVILGET
    spill.svar(spiller.id, Svar(feilVedtak, "finnes ikke", "Bergen", spill.sak.fasit.felle))
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
  fun `et vedtak kan ikke gjøres om`() = runTest {
    val spill = nyttSpill()
    val spiller = spill.bliMed("Kari", Stack.TANSTACK)
    val fasit = spill.sak.fasit
    val feilVedtak = if (fasit.vedtak == Vedtak.INNVILGET) Vedtak.AVSLATT else Vedtak.INNVILGET
    // Fella teller begge veier, så «ingen felle» er riktig svar i noen saker.
    val feilFelle = if (fasit.felle == null) "epost" else null

    // Spilleren får vite med én gang om svaret traff. Uten låsen kunne hvem
    // som helst prøvd seg fram til full pott.
    assertTrue(spill.svar(spiller.id, Svar(feilVedtak, "finnes ikke", "Bergen", feilFelle)))
    assertFalse(
      spill.svar(
        spiller.id,
        Svar(fasit.vedtak, fasit.hjemmel, spill.sak.soker.kommune, fasit.felle),
      )
    )

    klokke.gaa(RUNDE_MS)
    spill.tikk()

    assertEquals(0, spill.tilstand(spiller.id).meg?.poeng, "det første svaret er det som gjelder")
  }

  @Test
  fun `vurderingen kommer med én gang, og sier hva som traff`() = runTest {
    val spill = nyttSpill()
    val spiller = spill.bliMed("Ola", Stack.DATASTAR)
    val fasit = spill.sak.fasit

    assertNull(spill.tilstand(spiller.id).meg?.vurdering, "ingen vurdering før man har svart")

    // Riktig vedtak, feil hjemmel, riktig kommune, riktig felle.
    spill.svar(
      spiller.id,
      Svar(fasit.vedtak, "finnes ikke", spill.sak.soker.kommune, fasit.felle),
    )

    val vurdering = spill.tilstand(spiller.id).meg?.vurdering
    assertNotNull(vurdering)
    assertTrue(vurdering.vedtakRiktig)
    assertFalse(vurdering.hjemmelRiktig)
    assertTrue(vurdering.kommuneRiktig)
    assertTrue(vurdering.felleRiktig)
    assertEquals(POENG_VEDTAK + POENG_KOMMUNE + POENG_FELLE, vurdering.poeng)

    // Og den skal si det samme som tavla sier etterpå.
    klokke.gaa(RUNDE_MS)
    spill.tikk()
    assertEquals(vurdering.poeng, spill.tilstand(spiller.id).meg?.sistePoeng)
  }

  @Test
  fun `vurderingen gjelder mitt svar, ikke saken`() = runTest {
    val spill = nyttSpill()
    val spiller = spill.bliMed("Ingrid", Stack.ASTRO)

    // Fasiten skal fortsatt være skjult, også for den som har svart.
    spill.svar(spiller.id, Svar(Vedtak.INNVILGET, "§ 4-1", "Oslo", null))

    val tilstand = spill.tilstand(spiller.id)
    assertNotNull(tilstand.meg?.vurdering)
    assertNull(tilstand.fasit)
    assertNull(tilstand.forklaring)
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
    assertFalse(spill.svar(
      spiller.id,
      Svar(vedtak = fasit.vedtak, hjemmel = fasit.hjemmel, felle = fasit.felle),
    ))
    assertEquals(0, spill.tilstand(spiller.id).meg?.poeng)
  }

  @Test
  fun `fristen kortes inn når alle har svart, men ikke til null`() = runTest {
    val spill = nyttSpill()
    val en = spill.bliMed("Kari", Stack.TANSTACK)
    val to = spill.bliMed("Ola", Stack.DATASTAR)

    spill.svar(en.id, Svar(Vedtak.INNVILGET, "§ 4-1", null))
    spill.avsluttHvisAlleHarSvart()
    assertEquals(Fase.RUNDE, spill.fase, "én av to har svart, runden skal gå videre")

    spill.svar(to.id, Svar(Vedtak.INNVILGET, "§ 4-1", null))
    spill.avsluttHvisAlleHarSvart()

    // Ikke med én gang: den som svarte sist trykket nettopp, og skal rekke å
    // se sin egen kvittering før oppgjøret kommer.
    assertEquals(Fase.RUNDE, spill.fase, "oppgjøret kom i samme øyeblikk som siste svar")

    klokke.gaa(PUST_MS)
    spill.tikk()
    assertEquals(Fase.OPPGJOR, spill.fase)
  }

  @Test
  fun `pusten forlenger aldri en frist som alt er kortere`() = runTest {
    val spill = nyttSpill()
    val spiller = spill.bliMed("Kari", Stack.TANSTACK)

    // Svarer noen ett sekund før fristen, skal ikke «alle har svart» skyve
    // den fire sekunder ut.
    klokke.gaa(RUNDE_MS - 1_000)
    spill.svar(spiller.id, Svar(Vedtak.INNVILGET, "§ 4-1", null))
    spill.avsluttHvisAlleHarSvart()

    klokke.gaa(1_000)
    spill.tikk()

    assertEquals(Fase.OPPGJOR, spill.fase, "fristen ble skjøvet ut i stedet for inn")
  }

  @Test
  fun `den som har sittet over flere runder holder ikke de andre igjen`() = runTest {
    val spill = nyttSpill()
    val her = spill.bliMed("Her", Stack.DATASTAR)
    val borte = spill.bliMed("Borte", Stack.ASTRO)

    // «Borte» lukker fana. Etter to runder uten svar skal hun ikke lenger
    // telle med i «alle har svart», ellers måtte de andre vente ut fristen
    // hver eneste runde. Første runde teller ikke: begge meldte seg på midt
    // i den, og var ikke med på saken.
    repeat(3) {
      val fasit = spill.sak.fasit
      spill.svar(
        her.id,
        Svar(fasit.vedtak, fasit.hjemmel, spill.sak.soker.kommune, fasit.felle),
      )
      klokke.gaa(RUNDE_MS)
      spill.tikk()
      klokke.gaa(OPPGJOR_MS)
      spill.tikk()
    }

    assertEquals(Fase.RUNDE, spill.fase)
    val fasit = spill.sak.fasit
    spill.svar(her.id, Svar(fasit.vedtak, fasit.hjemmel, spill.sak.soker.kommune, fasit.felle))
    spill.avsluttHvisAlleHarSvart()

    klokke.gaa(PUST_MS)
    spill.tikk()
    assertEquals(Fase.OPPGJOR, spill.fase, "runden ventet på en som er gått hjem")
    assertNotNull(spill.tilstand(borte.id).meg, "hun står ennå på tavla til omgangen er over")
  }

  @Test
  fun `fire runder, så slutt, så ny omgang`() = runTest {
    val spill = nyttSpill()
    val spiller = spill.bliMed("Kari", Stack.TANSTACK)

    repeat(RUNDER_PER_SPILL) { runde ->
      assertEquals(Fase.RUNDE, spill.fase)
      assertEquals(runde + 1, spill.rundeNr)
      val fasit = spill.sak.fasit
      spill.svar(
        spiller.id,
        Svar(fasit.vedtak, fasit.hjemmel, spill.sak.soker.kommune, fasit.felle),
      )

      klokke.gaa(RUNDE_MS)
      spill.tikk()
      assertEquals(Fase.OPPGJOR, spill.fase)

      klokke.gaa(OPPGJOR_MS)
      spill.tikk()
    }

    assertEquals(Fase.SLUTT, spill.fase)
    assertEquals(RUNDER_PER_SPILL * POENG_FULL_POTT, spill.tilstand(spiller.id).meg?.poeng)

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
      spill.svar(
        spiller.id,
        Svar(fasit.vedtak, fasit.hjemmel, spill.sak.soker.kommune, fasit.felle),
      )
      klokke.gaa(RUNDE_MS)
      spill.tikk()
      klokke.gaa(OPPGJOR_MS)
      spill.tikk()
    }

    val topp = toppliste.topp(10)
    assertEquals(1, topp.size)
    assertEquals("Kari", topp[0].navn)
    assertEquals(RUNDER_PER_SPILL * POENG_FULL_POTT, topp[0].poeng)
    assertEquals(Stack.TANSTACK, topp[0].stack)
  }

  @Test
  fun `topplista viser det beste per navn, ikke hver omgang`() = runTest {
    // Uten grupperingen fylte den samme spilleren hele lista med sine egne
    // omganger, og «Markus» sto fem ganger på en liste som skal si hvem som
    // er best.
    toppliste.lagre("Markus", 20, Stack.DATASTAR)
    toppliste.lagre("Markus", 35, Stack.DATASTAR)
    toppliste.lagre("Markus", 15, Stack.DATASTAR)
    toppliste.lagre("Ingrid", 30, Stack.ASTRO)

    val topp = toppliste.topp(10)

    assertEquals(listOf("Markus", "Ingrid"), topp.map { it.navn })
    assertEquals(listOf(35, 30), topp.map { it.poeng })
  }

  @Test
  fun `den som melder seg på midt i en runde var ikke med på saken`() = runTest {
    val spill = nyttSpill()
    val sen = spill.bliMed("Sen", Stack.DATASTAR)

    // Hun får gjerne svare, og får poeng for det. Det hun ikke skal få, er
    // et avvik for en sak hun aldri så.
    assertEquals(false, spill.tilstand(sen.id).meg?.medPaSaken)

    klokke.gaa(RUNDE_MS)
    spill.tikk()
    klokke.gaa(OPPGJOR_MS)
    spill.tikk()

    assertEquals(true, spill.tilstand(sen.id).meg?.medPaSaken, "fra neste sak er hun med")
  }

  @Test
  fun `den som sitter over nok runder ryddes bort ved omgangsskiftet`() = runTest {
    val spill = nyttSpill()
    val blir = spill.bliMed("Blir", Stack.DATASTAR)
    val gar = spill.bliMed("Går", Stack.ASTRO)

    // En lukket fane sier ikke fra til noen. Uten ryddingen blir hvert besøk
    // stående som et navn på tavla til prosessen starter på nytt.
    //
    // Ryddingen skjer ved omgangsskiftet, ikke midt i en omgang: ble noen
    // fjernet mellom to saker, hoppet skjermen hennes tilbake til «Møt på
    // vakt» uten et ord.
    repeat(RUNDER_PER_SPILL) {
      val fasit = spill.sak.fasit
      spill.svar(blir.id, Svar(fasit.vedtak, fasit.hjemmel, spill.sak.soker.kommune, fasit.felle))
      klokke.gaa(RUNDE_MS)
      spill.tikk()
      klokke.gaa(OPPGJOR_MS)
      spill.tikk()
    }

    assertEquals(Fase.SLUTT, spill.fase)
    assertEquals(
      listOf("Blir", "Går"),
      spill.tilstand(blir.id).tavle.map { it.navn }.sorted(),
      "begge står ennå, for omgangen er ikke over",
    )

    klokke.gaa(SLUTT_MS)
    spill.tikk()

    val navn = spill.tilstand(blir.id).tavle.map { it.navn }
    assertEquals(listOf("Blir"), navn)
    assertNull(spill.tilstand(gar.id).meg, "den som gikk hjem står ikke igjen")
  }

  @Test
  fun `tavla er sortert, og sier hvilken stack hver spiller sitter i`() = runTest {
    val spill = nyttSpill()
    val svak = spill.bliMed("Svak", Stack.ASTRO)
    val sterk = spill.bliMed("Sterk", Stack.DATASTAR)

    val fasit = spill.sak.fasit
    spill.svar(sterk.id, Svar(vedtak = fasit.vedtak, hjemmel = fasit.hjemmel, felle = fasit.felle))
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

  @Test
  fun `bytter du utgave, følger tavla og topplista med`() = runTest {
    val spill = nyttSpill()
    val kari = spill.bliMed("Kari", Stack.DATASTAR)

    // Hun bytter til React-utgaven, og den appen sier fra når den henter
    // tilstanden for henne.
    spill.tilstand(kari.id, Stack.TANSTACK)

    assertEquals(
      Stack.TANSTACK,
      spill.tilstand(null).tavle.first { it.navn == "Kari" }.stack,
      "tavla står igjen med utgaven hun meldte seg på i",
    )
  }

  @Test
  fun `en ukjent utgave rører ikke spilleren`() = runTest {
    val spill = nyttSpill()
    val kari = spill.bliMed("Kari", Stack.ASTRO)

    spill.tilstand(kari.id, Stack.UKJENT)
    spill.tilstand(kari.id, null)

    assertEquals(Stack.ASTRO, spill.tilstand(null).tavle.first().stack)
  }

  @Test
  fun `den som går av vakt er borte med en gang`() = runTest {
    val spill = nyttSpill()
    val kari = spill.bliMed("Kari", Stack.ASTRO)
    spill.bliMed("Ola", Stack.DATASTAR)

    assertTrue(spill.gaAv(kari.id), "sa at spilleren ikke fantes")

    val tavle = spill.tilstand(null).tavle
    assertEquals(listOf("Ola"), tavle.map { it.navn }, "spilleren står igjen på tavla")
  }

  @Test
  fun `å gå av vakt to ganger sier fra andre gang`() = runTest {
    val spill = nyttSpill()
    val kari = spill.bliMed("Kari", Stack.ASTRO)

    assertTrue(spill.gaAv(kari.id))
    assertFalse(spill.gaAv(kari.id), "en ukjent id skal ikke se ut som en avmelding")
  }
}
