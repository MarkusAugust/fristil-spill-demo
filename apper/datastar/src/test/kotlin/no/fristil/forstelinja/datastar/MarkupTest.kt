package no.fristil.forstelinja.datastar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * At markupen holder Fristils kontrakt.
 *
 * Denne appen er skrevet i Kotlin og kan ikke kalle `fs.field()`. Den må
 * skrive klassene og koblingen selv, og fordi serveren sender det samme
 * området på nytt ved hver patch, må `data-preserve-attr` stå på hvert
 * element komponenten rører.
 *
 * Uten disse prøvene kan en bevaringsliste forsvinne i en redigering, og
 * feilen ville først vist seg som et skjema som mister koblingen sin etter
 * første oppdatering, hos noen andre, senere.
 */
class MarkupTest {
  private val tilstand =
    Tilstand(
      fase = "runde",
      rundeNr = 1,
      runderTotalt = 4,
      fristMs = 1_000,
      faseLengdeMs = 120_000,
      naMs = 0,
      sak =
        SakUt(
          id = "2024/1187",
          tittel = "Høner",
          sammendrag = "…",
          tekst = "Jeg søker om noe, og jeg mener det.",
          soker = Soker("Bjørg", "31.02.1974", "Bergen", "b@example.no"),
        ),
      hjemler = listOf(Hjemmel("§ 4-1", "Alminnelig")),
      tavle = listOf(TavleRad(1, "Kari", 0, "datastar", true)),
      meg = MegUt("Kari", 0, 0, 1, 0, false),
      evigToppliste = emptyList(),
    )

  @Test
  fun `serveren kan alltid melde et felt som ugyldig`() {
    val html = brett(tilstand, "Kari", listOf("Bergen")) + blimed()

    // `data-preserve-attr` betyr «ikke rør», og det gjelder begge veier.
    // Fredet vi `aria-invalid` eller `data-state`, kunne serveren aldri
    // meldt feltet som ugyldig: morfingen ville nektet å sette dem.
    for (liste in Regex("""data-preserve-attr="([^"]*)"""").findAll(html).map { it.groupValues[1] }) {
      assertFalse(liste.contains("aria-invalid"), "«$liste» freder serverens eget svar")
      assertFalse(liste.contains("data-state"), "«$liste» freder serverens eget svar")
    }
  }

  @Test
  fun `et felt som bare sender struktur freder koblingen komponenten lager`() {
    // Sender serveren et felt uten id-er, lager `<fs-field>` dem i
    // nettleseren. Serveren skriver dem aldri, så morfingen river dem bort
    // ved neste patch av samme område, og feltet mister koblingen mellom
    // ledetekst, kontroll og hjelpetekst.
    val html = blimed()

    assertFalse(html.contains("""for="""), "ledeteksten skal ikke kobles av serveren")
    assertTrue(html.contains("""data-preserve-attr="${Bevar.KOBLING_LEDETEKST}""""))
    assertTrue(html.contains("""data-preserve-attr="${Bevar.KOBLING_KONTROLL}""""))
    assertTrue(html.contains("""data-preserve-attr="${Bevar.KOBLING_HJELPETEKST}""""))
  }

  @Test
  fun `serveren kan melde et felt som ugyldig`() {
    val html = brett(tilstand, "Kari", listOf("Bergen"), listOf(Feil("hjemmel", "Mangler")))

    assertTrue(html.contains("""aria-invalid="true""""), "hjemmelen skal være ugyldig")
    assertTrue(html.contains("Du må rette 1 feil"), "feiloppsummeringen")
  }

  @Test
  fun `bare det brukeren eier fredes`() {
    val html = brett(tilstand, "Kari", listOf("Bergen"))

    // Fanevalget, sprettoppvinduet og forslagslista endres i nettleseren,
    // og serveren får aldri vite om det.
    for (liste in listOf(Bevar.FANE, Bevar.SPRETTOPP_VERT, Bevar.FORSLAG_KONTROLL)) {
      assertTrue(
        html.contains("""data-preserve-attr="$liste""""),
        "dette må fredes: $liste",
      )
    }
  }

  @Test
  fun `fanene bærer sine, siden komponenten flytter valget`() {
    val html = brett(tilstand, "Kari", listOf("Bergen"))

    // «aria-selected tabindex» er unik for fanene.
    assertEquals(2, Regex("""data-preserve-attr="${Regex.escape(Bevar.FANE)}"""").findAll(html).count())

    // «hidden» er det ikke: forslagslista og «ingen treff» freder den samme
    // strengen. Her teller vi derfor panelene, og at det finnes minst like
    // mange fredninger som paneler.
    assertEquals(2, Regex("""role="tabpanel"""").findAll(html).count())
    assertTrue(
      Regex("""data-preserve-attr="${Regex.escape(Bevar.FANEPANEL)}"""").findAll(html).count() >= 2,
    )
  }

  @Test
  fun `serveren navngir bare det noe annet må peke på`() {
    val html = brett(tilstand, "Kari", listOf("Bergen"))

    // Feiloppsummeringen lenker til hjemmelen, så den må ha en id serveren
    // kjenner.
    assertTrue(html.contains("""id="hjemmel""""), "hjemmelen må navngis")

    // Fellefeltet peker ingen på. Da skriver serveren bare struktur, og
    // `<fs-field>` setter id, `for` og `aria-describedby` i nettleseren.
    // Skrev vi koblingen her, måtte hver server i hvert språk gjort det
    // samme, og da er ikke designsystemet lenger uavhengig av serveren.
    val fellefeltet = html.substringAfter("Er noe feil i søknaden?").substringBefore("</fs-field>")
    // Feiloppsummeringen lenker også hit, så også dette feltet navngis.
    assertTrue(fellefeltet.contains("""id="felle""""), "fellefeltet må navngis")
    assertTrue(
      fellefeltet.contains("""aria-describedby="felle-hjelp""""),
      "serveren skriver koblingen når den først navngir feltet",
    )
  }

  @Test
  fun `de frittstående komponentene ber morfingen holde seg unna`() {
    val html = side(tilstand, "Kari")

    // De eier sitt eget innhold, og serveren har ingenting å sende for dem.
    // Åpningstaggen kan gå over flere linjer, så hele den leses.
    for (tagg in listOf("fs-session-timeout", "fs-connection-status")) {
      val start = html.indexOf("<$tagg")
      assertTrue(start >= 0, "fant ikke <$tagg>")
      val apningstagg = html.substring(start, html.indexOf('>', start))
      assertTrue(apningstagg.contains("data-ignore-morph"), "$tagg mangler data-ignore-morph")
    }
  }

  @Test
  fun `siden abonnerer på strømmen`() {
    // `data-on:load` finnes ikke i Datastar 1.0.4 og ble ignorert i
    // stillhet, så skjermen oppdaterte seg bare når du selv gjorde noe.
    assertTrue(side(tilstand, "Kari").contains("""data-init="@get('/hendelser')""""))
  }

  @Test
  fun `fasiten står ikke i markupen mens runden pågår`() {
    val html = brett(tilstand, "Kari", listOf("Bergen"))

    assertFalse(html.contains("Fasit"), "fasiten skal ikke være å finne i kildekoden")
  }

  @Test
  fun `patchen har Datastars format`() {
    val ut = patch("<div id=\"a\">en</div>\n<div id=\"b\">to</div>")

    assertEquals(
      listOf(
        "event: datastar-patch-elements",
        """data: elements <div id="a">en</div>""",
        """data: elements <div id="b">to</div>""",
        "",
        "",
      ),
      ut.split("\n"),
    )
  }

  @Test
  fun `stilarkene og komponentene hentes fra CDN, uten npm`() {
    val html = side(tilstand, "Kari")

    assertTrue(STILARK.all { it.startsWith("https://cdn.jsdelivr.net/npm/@fristil/designsystem@") })
    assertTrue(html.contains("defineFsField"), "komponentene må registreres")
    assertTrue(html.contains(FRISTIL_VERSJON), "versjonen skal være pinnet")
  }

  @Test
  fun `hver klasse markupen bruker har et stilark`() {
    // Uten dette kan en komponent tas i bruk uten at CSS-en følger med, og
    // da ser den ut som nettleserens egen. Radioknappene og feltsettet sto
    // slik en stund uten at noe sa fra.
    val html = side(tilstand, "Kari", listOf("Bergen")) + brett(tilstand, "Kari", listOf("Bergen"))

    val brukt =
      Regex("""class="(fs-[a-z-]+)""").findAll(html).map { it.groupValues[1] }.toSet() +
        Regex("""<(fs-[a-z-]+)""").findAll(html).map { it.groupValues[1] }.toSet()

    val lastet = STILARK.joinToString(" ")

    // `field.css` samler ledetekst, felt, hjelpetekst og feilmelding.
    val samlet =
      setOf(
        "fs-label",
        "fs-input",
        "fs-help-text",
        "fs-error-text",
        "fs-legend",
        // Raden rundt en radioknapp står i radio.css, sammen med knappen.
        "fs-radio-row",
      )

    val uten =
      brukt.filterNot { klasse ->
        klasse in samlet || lastet.contains(klasse.removePrefix("fs-") + ".css")
      }

    assertEquals(emptyList(), uten, "disse klassene har ingen stilark")
  }

  @Test
  fun `ingen id står to ganger i siden`() {
    // Nedtellingen sto med samme id både i toppen og i panelet når omgangen
    // var over. `getElementById` finner bare den første, så panelet ble
    // stående med «… sekunder» mens toppen talte ned.
    for (fase in listOf("runde", "oppgjor", "slutt")) {
      val html = side(tilstand.copy(fase = fase), "Kari", listOf("Bergen"))
      val ider = Regex("""id="([^"]+)""").findAll(html).map { it.groupValues[1] }.toList()
      val doble = ider.groupBy { it }.filterValues { it.size > 1 }.keys

      assertEquals(emptySet(), doble, "disse id-ene står flere ganger i fasen $fase")
    }
  }

  @Test
  fun `en ny sak tømmer skjemaet, et nytt oppgjør gjør det ikke`() {
    // Signalene bor i nettleseren og overlever en morfing. Uten en
    // nullstilling sto forrige rundes vedtak ferdig avkrysset i den nye
    // saken, og en spiller kunne sende inn uten å ta stilling til noe.
    val neste =
      tilstand.copy(rundeNr = 2, sak = tilstand.sak.copy(id = "2024/0904", tittel = "Noe annet"))

    assertTrue(erNySak(null, tilstand), "den første saken er alltid ny")
    assertTrue(erNySak(tilstand, neste), "runde to er en ny sak")
    assertTrue(
      erNySak(tilstand, tilstand.copy(sak = tilstand.sak.copy(id = "2024/0904"))),
      "en ny omgang begynner på runde én igjen, men med en annen sak",
    )
    assertFalse(
      erNySak(tilstand, tilstand.copy(fase = "oppgjor")),
      "oppgjøret gjelder den samme saken, og svaret skal bli stående",
    )
  }

  @Test
  fun `saken står som et dokument, med tekst og avsender`() {
    // «Hva er saken?» skal være til å se med én gang: nummer, tittel,
    // ingress og selve søknaden. Fella ligger i opplysningene om søkeren,
    // så begge fanene må leses.
    val html = brett(tilstand, "Kari", listOf("Bergen"))

    assertTrue(html.contains("Sak 2024/1187"), "saksnummeret skal stå der")
    assertTrue(html.contains(tilstand.sak.tittel), "tittelen skal stå der")
    assertTrue(html.contains(tilstand.sak.sammendrag), "ingressen skal stå der")
    assertTrue(html.contains(tilstand.sak.tekst), "selve søknaden skal stå der")
    assertTrue(html.contains(">Søknaden<"), "fanen med teksten")
    assertTrue(html.contains(">Søkeren<"), "fanen med opplysningene")
  }

  @Test
  fun `kvitteringen viser hva som traff, men ikke fasiten`() {
    val svart =
      tilstand.copy(
        meg =
          MegUt(
            navn = "Kari",
            poeng = 15,
            sistePoeng = 15,
            plass = 1,
            forrigePlass = 1,
            harSvart = true,
            svar = Svar("avslatt", "§ 12-3", "Bergen", "fodselsdato"),
            vurdering = Vurdering(true, false, true, true, 20),
          ),
      )

    val html = brett(svart, "Kari", listOf("Bergen"))

    assertTrue(html.contains("Vedtaket er fattet"), "kvitteringen skal vises")
    assertTrue(html.contains("20 av 25 poeng"), "poengene skal stå der")
    assertTrue(html.contains("""data-riktig="true""""), "det som traff")
    assertTrue(html.contains("""data-riktig="false""""), "det som ikke traff")

    // Fasiten hører til oppgjøret. Ville den stått her, kunne den som
    // svarte fortalt de andre hva svaret var mens runden fortsatt gikk.
    assertFalse(html.contains("Fasit<"), "fasiten skal ikke stå i kvitteringen")
    assertFalse(html.contains("Fatt vedtak"), "skjemaet skal være borte")
  }

  @Test
  fun `radioknappene bruker raden Fristil dokumenterer`() {
    // `.fs-label` er et vanlig blokkelement. Ligger knappen inni ledeteksten
    // står de to inntil hverandre uten luft, og det er `.fs-radio-row` som
    // holder dem ved siden av hverandre.
    val html = brett(tilstand, "Kari", listOf("Bergen"))

    assertTrue(html.contains("""<div class="fs-radio-row">"""), "raden skal være der")
    assertTrue(
      html.contains("""<label class="fs-label" for="v-innvilget">Innvilget</label>"""),
      "ledeteksten skal peke på knappen, ikke pakke den inn",
    )
  }

  /** Tilstanden i oppgjøret, med fasit og et svar å vurdere. */
  private fun oppgjorMed(vurdering: Vurdering, svar: Svar?, medPaSaken: Boolean = true) =
    tilstand.copy(
      fase = "oppgjor",
      fasit = Fasit("avslatt", "§ 12-3", "fodselsdato"),
      fasitKommune = "Bergen",
      forklaring = "Fødselsdatoen finnes ikke.",
      meg =
        MegUt(
          navn = "Kari",
          poeng = 20,
          sistePoeng = 20,
          plass = 1,
          forrigePlass = 1,
          harSvart = svar != null,
          medPaSaken = medPaSaken,
          svar = svar,
          vurdering = vurdering,
        ),
    )

  @Test
  fun `resultatdialogen sier hva du svarte og hva som var riktig`() {
    // Det holder ikke å si «feil». Da vet spilleren fortsatt ikke hva hun
    // skulle ha svart, og neste runde blir like tilfeldig.
    val html =
      brett(
        oppgjorMed(
          Vurdering(true, false, true, true, 20),
          Svar("avslatt", "§ 4-1", "Bergen", "fodselsdato"),
        ),
        "Kari",
      )

    assertTrue(html.contains("<fs-dialog id=\"resultat\" open>"), "dialogen skal stå åpen i oppgjøret")
    assertTrue(html.contains("Delvis truffet"), "overskriften skal si hvordan det gikk")
    assertTrue(html.contains("20</strong> av 25 poeng"), "poengene skal stå der")

    // Hjemmelen var feil, og da må det riktige svaret stå.
    assertTrue(html.contains("§ 4-1"), "ditt eget svar skal stå der")
    assertTrue(html.contains("Riktig svar: § 12-3"), "det riktige svaret skal stå der")

    // Alle fire feltene vurderes, kommunen inkludert.
    for (felt in listOf("Utfall", "Hjemmel", "Kommune", "Feil i søknaden")) {
      assertTrue(html.contains(">$felt</dt>"), "«$felt» mangler i dialogen")
    }
  }

  @Test
  fun `den som ikke rakk fristen får et avvik, ikke en poengsum`() {
    val html = brett(oppgjorMed(Vurdering(false, false, false, false, 0), null), "Kari")

    assertTrue(html.contains("Avvik registrert"), "overskriften skal si at det er et avvik")
    assertTrue(html.contains("varslet til fylkesmannen"), "avviket skal ha en følge")
    assertTrue(html.contains("""data-utfall="avvik""""), "dialogen skal fargelegges som avvik")
  }

  @Test
  fun `dialogen står lukket utenom oppgjøret`() {
    val iRunden = brett(tilstand, "Kari", listOf("Bergen"))

    assertFalse(iRunden.contains("<fs-dialog id=\"resultat\" open>"), "dialogen skal ikke åpne seg i en runde")
    assertFalse(iRunden.contains("Riktig svar:"), "fasiten skal ikke stå i markupen i det hele tatt")

    // Spilltjeneren sender fasiten i alle faser etter runden, også når
    // omgangen er slutt. Da er oppgjøret for lengst lest, og dialogen skal
    // ikke legge seg over sluttlista.
    val vedSlutt =
      oppgjorMed(Vurdering(true, true, true, true, 25), Svar("avslatt", "§ 12-3", "Bergen", "fodselsdato"))
        .copy(fase = "slutt")

    assertFalse(
      brett(vedSlutt, "Kari").contains("<fs-dialog id=\"resultat\" open>"),
      "dialogen skal ikke åpne seg på sluttskjermen",
    )
  }

  @Test
  fun `dialogen freder open på dialogen, ikke på verten`() {
    // `showModal()` setter `open` på `<dialog>`, og serveren skriver det
    // aldri. Uten fredningen river morfingen det bort. Verten skal derimot
    // ikke fredes, ellers kan serveren aldri åpne dialogen på nytt.
    val html =
      brett(oppgjorMed(Vurdering(true, true, true, true, 25), Svar("avslatt", "§ 12-3", "Bergen", "fodselsdato")), "Kari")

    assertTrue(html.contains("""data-utfall="full" data-preserve-attr="open""""))
    assertFalse(html.contains("""<fs-dialog id="resultat" open data-preserve-attr"""))
  }

  @Test
  fun `hver komponent som lastes er faktisk i bruk`() {
    // `fs-toast` sto i lista uten at noen kalte `.show()`, og `divider.css`
    // uten at noen brukte `.fs-divider`. En demo som later som den bruker
    // flere komponenter enn den gjør, beviser ikke noe.
    val html = side(tilstand, "Kari", listOf("Bergen")) + brett(tilstand, "Kari", listOf("Bergen"))

    for ((fil, _) in KOMPONENTER) {
      val tagg = fil.substringAfterLast("/").removeSuffix(".js")
      assertTrue(html.contains("<$tagg"), "$tagg registreres, men står ikke i markupen")
    }
  }
}
