package no.fristil.forstelinja

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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

  // `uuuu` og ikke `yyyy`, siden den strenge utgaven krever et årstall uten
  // epoke. `STRICT` er poenget: den milde utgaven gjør 31. februar om til
  // 28. februar, og da ville fella i hønesaken sett ut som en gyldig dato.
  private val datoformat =
    DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)

  // Ikke en fullstendig adressesjekk, og skal ikke være det. Den ser etter
  // nøyaktig det en saksbehandler ser etter: én krøllalfa, og noe med
  // punktum i etter den.
  private val epostmonster = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

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
  fun `den ekte fila har nok saker til at en omgang ikke gjentar seg`() {
    val samling = lesSaker("../../felles/saker.json")

    // `Sakssamling.init` krever alt at det er minst like mange saker som
    // runder, så en test på nøyaktig det kunne ikke feilet her. Det denne
    // sier er noe annet: med bare fire saker ville hver omgang hatt de
    // samme fire, i stokket rekkefølge, og demoen blitt kjedelig å stå i.
    assertTrue(
      samling.saker.size >= RUNDER_PER_SPILL * 2,
      "bare ${samling.saker.size} saker mot $RUNDER_PER_SPILL runder",
    )

    // Og at det finnes flere hjemler enn svar. «§ 9-1 Støy fra virksomhet i
    // boligområde» og «§ 33-1 Tvangsmulkt» er ingen saks fasit, men de er
    // fristende feilsvar i hønesaken og i gapestokken, og det er nettopp det
    // en hjemmel som ikke er svaret skal være. Uten dem ville lista vært en
    // fasit i seg selv.
    //
    // «§ 8-2 Dyrehold i borettslag» sto her lenge, og er nå fasit i
    // kattesaken. En hjemmel som aldri er riktig noe sted blir lært bort som
    // «aldri § 8-2», og slutter da å friste i hønesaken.
    assertTrue(
      samling.hjemler.size > samling.saker.map { it.fasit.hjemmel }.toSet().size,
      "hver hjemmel er et riktig svar, så lista røper fasiten",
    )
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
  fun `fellene er ekte, og de andre sakene er i orden`() {
    // `Sakssamling.init` sier bare at fella er navnet på et felt. At feltet
    // faktisk er feil sier den ingenting om, og heller ikke at de andre
    // sakene er rene. En sak med «felle: null» og en umulig fødselsdato ville
    // gjort spillet urettferdig uten et ord: ingen spiller kunne fått full
    // pott, og ingen hadde fått vite hvorfor.
    val samling = lesSaker("../../felles/saker.json")
    val iDag = LocalDate.now()

    for (sak in samling.saker) {
      // Tre krav, og det siste er ikke overflødig: datoen i båtsaken er en
      // ekte 7. september, den ligger bare fram i tid.
      val datoenHolder =
        runCatching { LocalDate.parse(sak.soker.fodselsdato, datoformat) }
          .map { it <= iDag && it > iDag.minusYears(120) }
          .getOrDefault(false)
      val epostenHolder = epostmonster.matches(sak.soker.epost)

      when (sak.fasit.felle) {
        "fodselsdato" ->
          assertFalse(
            datoenHolder,
            "${sak.id} har fødselsdatoen som felle, men «${sak.soker.fodselsdato}» er i orden",
          )
        "epost" ->
          assertFalse(
            epostenHolder,
            "${sak.id} har e-posten som felle, men «${sak.soker.epost}» er i orden",
          )
        else -> {
          assertTrue(
            datoenHolder,
            "${sak.id} har fødselsdatoen «${sak.soker.fodselsdato}», som ikke holder, uten at den er fella",
          )
          assertTrue(
            epostenHolder,
            "${sak.id} har e-posten «${sak.soker.epost}», som ikke holder, uten at den er fella",
          )
        }
      }
    }
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
