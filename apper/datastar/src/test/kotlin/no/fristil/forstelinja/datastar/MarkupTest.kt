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
      rundeLengdeMs = 120_000,
      grenseUtenSvar = 3,
      poeng = PoengUt(vedtak = 10, hjemmel = 5, kommune = 5, felle = 5, fullPott = 25),
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
      tavle =
        listOf(
          TavleRad(
            plass = 1,
            navn = "Kari",
            poeng = 0,
            sistePoeng = 0,
            harSvart = false,
            stack = "datastar",
            erMeg = true,
          )
        ),
      harSvart = 0,
      medPaSaken = 1,
      meg = MegUt("Kari", 0, 0, 1, 0, false),
      evigToppliste = emptyList(),
    )

  @Test
  fun `serveren kan alltid melde et felt som ugyldig`() {
    val html = brett(tilstand, "Kari", listOf("Bergen")) + blimed()

    // `data-preserve-attr` betyr «ikke rør», og det gjelder begge veier.
    // Fredet vi `aria-invalid` eller `data-state`, kunne serveren aldri
    // meldt feltet som ugyldig: morfingen ville nektet å sette dem.
    val lister = Regex("""data-preserve-attr="([^"]*)"""").findAll(html).map { it.groupValues[1] }.toList()

    // Uten denne ville prøven meldt grønt om hver eneste liste forsvant.
    assertTrue(lister.size >= 8, "fant bare ${lister.size} bevaringslister")

    for (liste in lister) {
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

    // Komponenten flytter `aria-selected` og `tabindex` når brukeren blar,
    // og slår `hidden` av og på på panelene.
    val faner = Regex("""role="tab"[^>]*""").findAll(html).toList()
    assertEquals(2, faner.size, "fant ikke begge fanene")
    for (fane in faner) {
      assertTrue(fane.value.contains(Bevar.FANE), "en fane mangler bevaringslista")
    }

    // Panelene telles for seg: `Bevar.FANEPANEL` er strengen «hidden», og
    // forslagsfeltet bruker den samme strengen. En telling over hele
    // markupen ville derfor vært grønn selv om begge panelene mistet sin.
    val paneler = Regex("""role="tabpanel"[^>]*""").findAll(html).toList()
    assertEquals(2, paneler.size, "fant ikke begge panelene")
    for (panel in paneler) {
      assertTrue(
        panel.value.contains("""data-preserve-attr="${Bevar.FANEPANEL}""""),
        "et fanepanel mangler bevaringslista",
      )
    }
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
    for (tagg in listOf("fs-connection-status")) {
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
    // Spilltjeneren sender ikke fasiten i en runde, men prøven skal si noe
    // om markupen: får den en fasit likevel, skal ingenting av den lekke ut
    // mens fasen er «runde».
    val medFasit =
      tilstand.copy(
        fasit = Fasit("avslatt", "§ 12-3", "fodselsdato"),
        fasitKommune = "Bergen",
        forklaring = "Fødselsdatoen finnes ikke.",
      )
    val lekkasje = brett(medFasit, "Kari", listOf("Bergen"))

    assertFalse(lekkasje.contains("Fødselsdatoen finnes ikke."), "forklaringen lekker")
    assertFalse(lekkasje.contains("Riktig svar:"), "fasiten lekker")
    assertFalse(lekkasje.contains("<fs-dialog id=\"resultat\" open>"), "dialogen åpner seg i runden")

    val html = brett(tilstand, "Kari", listOf("Bergen"))

    assertFalse(html.contains("Fasit"), "fasiten skal ikke være å finne i kildekoden")
  }

  @Test
  fun `en dialog som skal vises står åpen også uten JavaScript`() {
    // `<dialog>` uten `open` er skjult. Sto attributtet bare på verten, var
    // innholdet borte for den som ikke har JavaScript, og i React meldte
    // hydreringen avvik. Denne appen skriver markupen for hånd, så den kan
    // komme i utakt med byggefunksjonene i Fristil uten at noe sier fra.
    val oppgjor =
      tilstand.copy(
        fase = "oppgjor",
        fasit = Fasit("innvilget", "§ 4-1", null),
        fasitKommune = "Bergen",
        forklaring = "Søknaden er i orden.",
      )

    // `\bopen\b` duger ikke: `data-preserve-attr="open"` inneholder ordet,
    // og prøven ville meldt grønt uansett. Det vi er ute etter er et bart
    // attributt, altså `open` med mellomrom foran og mellomrom eller `>`
    // etter. Prøvd: uten dette fanget prøven ikke at attributtet ble fjernet.
    val bartOpen = { tagg: String, html: String ->
      Regex("<$tagg\\b[^>]*\\sopen(\\s|>)").containsMatchIn(html)
    }

    for (html in listOf(velkomst(), resultatdialog(oppgjor))) {
      assertEquals(
        bartOpen("fs-dialog", html),
        bartOpen("dialog", html),
        "open skal stå begge steder eller ingen: $html",
      )
    }
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
      Regex("""class="([^"]*)"""")
        .findAll(html)
        .flatMap { it.groupValues[1].split(" ") }
        .filter { it.startsWith("fs-") }
        .toSet() +
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
        // Rullefeltet rundt en bred tabell står i table.css.
        "fs-table-scroll",
      )

    // `fs-tabs__list` hører i `tabs.css`. Delen etter `__` er en del av
    // komponenten, ikke en komponent for seg.
    val uten =
      brukt.filterNot { klasse ->
        klasse in samlet ||
          lastet.contains(klasse.removePrefix("fs-").substringBefore("__") + ".css")
      }

    assertEquals(emptyList(), uten, "disse klassene har ingen stilark")
  }

  @Test
  fun `ingen id står to ganger i siden`() {
    // Nedtellingen sto med samme id både i toppen og i panelet når omgangen
    // var over. `getElementById` finner bare den første, så panelet ble
    // stående med «… sekunder» mens toppen talte ned.
    // Oppgjøret tegner sakskortet en gang til ved siden av resultatdialogen,
    // og er det eneste stedet en dobbel id kunne oppstått. Uten en fasit
    // returnerer `oppgjor()` tom streng, og prøven ville lest et tomt kort.
    val medFasit =
      tilstand.copy(
        fasit = Fasit("avslatt", "§ 12-3", "fodselsdato"),
        fasitKommune = "Bergen",
        forklaring = "Fordi.",
      )

    for (fase in listOf("runde", "oppgjor", "slutt")) {
      val html = side(medFasit.copy(fase = fase), "Kari", listOf("Bergen"))
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
    // Teksten brytes over flere linjer i malen, så mellomrom slås sammen
    // før den leses. Ellers henger prøven på hvor linjeskiftet tilfeldigvis
    // står.
    val flat = html.replace(Regex("""\s+"""), " ")
    assertTrue(flat.contains("varslet til statsforvalteren"), "avviket skal ha en følge")
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

    val dialogtagg = Regex("""<dialog[^>]*""").find(html)?.value ?: ""
    val verten = Regex("""<fs-dialog[^>]*""").find(html)?.value ?: ""

    assertTrue(dialogtagg.contains("""data-preserve-attr="open""""), "dialogen må fredes")
    assertFalse(verten.contains("data-preserve-attr"), "verten skal ikke fredes")
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

  @Test
  fun `hvert stilark som lastes er faktisk i bruk`() {
    // `session-timeout.css` ble lastet lenge etter at komponenten var ute.
    // Et stilark ingen bruker er dødvekt over nettet og en påstand om at
    // demoen viser fram mer enn den gjør.
    //
    // Alle tre fasene må med: varselboksen står bare i oppgjøret, og
    // sluttlista bare på sluttskjermen.
    val oppgjor = oppgjorMed(Vurdering(true, true, true, true, 25), Svar("avslatt", "§ 12-3", "Bergen", "fodselsdato"))
    val html =
      side(tilstand, "Kari", listOf("Bergen")) +
        brett(tilstand, "Kari", listOf("Bergen")) +
        brett(oppgjor, "Kari") +
        brett(oppgjor.copy(fase = "slutt"), "Kari")

    val brukt =
      Regex("""class="([^"]*)"""")
        .findAll(html)
        .flatMap { it.groupValues[1].split(" ") }
        .filter { it.startsWith("fs-") }
        .map { it.removePrefix("fs-").substringBefore("__") }
        .toSet() +
        Regex("""<(fs-[a-z-]+)""").findAll(html).map { it.groupValues[1].removePrefix("fs-") }.toSet()

    // Disse er bunter eller grunnlag, og har ingen egen klasse i markupen.
    val alltid = setOf("tokens", "field", "label", "input", "help-text", "error-text")

    val ubrukte =
      STILARK.map { it.substringAfterLast("/").removeSuffix(".css") }
        .filterNot { it in alltid || brukt.any { k -> k == it || k.startsWith("$it-") } }

    assertEquals(emptyList(), ubrukte, "disse stilarkene brukes ikke")
  }

  @Test
  fun `ingen dollartegn slipper gjennom fra strengmalene`() {
    // `$$` er en mal som er redigert feil, og det så ut som «$25 poeng» på
    // skjermen. Strengmaler har ingen kompilator som sier fra.
    val html = side(tilstand, "Kari", listOf("Bergen")) + brett(tilstand, "Kari", listOf("Bergen"))

    // Ingenting på skjermen i dette spillet inneholder et dollartegn, så
    // alle som slipper gjennom er en mal som ikke ble erstattet.
    val rundt = html.indexOf("$")
    assertEquals(
      -1,
      rundt,
      "dollartegn i markupen: «${html.substring(maxOf(0, rundt - 40), minOf(html.length, rundt + 40))}»",
    )
  }

  @Test
  fun `den som kom for sent dømmes ikke, men får se fasiten`() {
    // En rad med «Feil» i rødt for noe hun aldri fikk se, er en dom over
    // feil person. Hun skal likevel få vite hva som var riktig, ellers blir
    // neste sak like tilfeldig for henne som for alle andre.
    val html =
      brett(oppgjorMed(Vurdering(false, false, false, false, 0), null, medPaSaken = false), "Kari")

    assertTrue(html.contains("Du kom inn midt i saken"))
    assertFalse(html.contains(">Feil</dd>"), "hun skal ikke få «Feil» på noe hun aldri så")
    assertTrue(html.contains("§ 12-3"), "fasiten skal stå der")
    assertTrue(html.contains(">Kommune</dt>"), "alle fire feltene skal stå der")
  }

  @Test
  fun `siden har et merke serveren kan si fra på`() {
    // Sambandslinja eier sitt eget innhold og kan ikke patches. Serveren
    // skriver derfor på et skjult merke i stedet, og skriptet i siden lytter
    // på det. Uten merket kunne ingenting si fra om at spilltjeneren bak var
    // borte, og skjermen ville stått helt normal og aldri oppdatert seg mer.
    val html = side(tilstand, "Kari", listOf("Bergen"))

    assertTrue(html.contains("""<div id="samband" hidden data-nede="false">"""))
    assertTrue(html.contains("""attributeFilter: ["data-nede"]"""), "skriptet må lytte på merket")
    assertTrue(html.contains("reportFailure"), "og si fra til komponenten")
  }

  @Test
  fun `nedtrekkslistene tegnes i siden`() {
    // Uten `data-picker="styled"` er lista et vindu fra operativsystemet:
    // den følger maskinens tema og ikke sidens, ingen CSS når inn i den, og
    // i Chromes mobilemulering havner den på feil sted og i feil størrelse.
    val html = brett(tilstand, "Kari", listOf("Bergen"))

    val lister = Regex("""<select[^>]*""").findAll(html).map { it.value }.toList()
    assertEquals(2, lister.size, "fant ikke begge nedtrekkslistene")
    for (liste in lister) {
      assertTrue(liste.contains("""data-picker="styled""""), "en liste tegnes ikke i siden")
    }
  }

  @Test
  fun `fasiten kan åpnes igjen etter at dialogen er lukket`() {
    // Lukker du dialogen mens du venter på neste sak, skal du komme til den
    // igjen. Datastar setter `open` på verten, og komponenten gjør kallet.
    val html =
      brett(
        oppgjorMed(Vurdering(true, true, true, true, 25), Svar("avslatt", "§ 12-3", "Bergen", "fodselsdato")),
        "Kari",
      )

    assertTrue(html.contains("Se resultatet"), "knappen mangler")
    assertTrue(
      html.contains("""data-on:click="document.getElementById('resultat')?.setAttribute('open', '')""""),
      "knappen åpner ikke dialogen",
    )
  }

  @Test
  fun `klokka kan riste uten at morfingen tar det bort`() {
    // Skriptet setter `data-rister` og `--spill-rist` på klokka. Serveren
    // skriver ingen av dem, så uten bevaringslista river morfingen dem bort
    // ved neste patch, og ristingen stopper midt i.
    val html = topp(tilstand)

    assertTrue(
      html.contains("""data-preserve-attr="style data-rister""""),
      "klokka freder ikke det skriptet setter",
    )
  }

  @Test
  fun `du ser hvem du er, og hvem som har levert`() {
    // Med flere vinduer oppe er det ikke til å se hvem som er hvem.
    val toPaVakt =
      tilstand.copy(
        tavle =
          listOf(
            TavleRad(1, "Kari Nordmann", 0, 0, true, "datastar", true),
            TavleRad(2, "Ola Hansen", 0, 0, false, "datastar", false),
          )
      )

    val html = brett(toPaVakt, "Kari Nordmann", listOf("Bergen"))

    assertTrue(html.contains(""">KN</span>"""), "initialene i avataren")
    assertTrue(html.contains("Kari Nordmann"), "navnet ditt i topplinja")
    assertTrue(html.contains("(deg)"), "din egen rad skal være merket")
    assertTrue(html.contains("1 av 2 saksbehandlere har levert."))
    assertTrue(html.contains(">Levert<") && html.contains(">Jobber<"), "status per spiller")
  }

  @Test
  fun `oppgjøret sier hvem som kom best ut av saken`() {
    val to =
      oppgjorMed(Vurdering(true, true, true, true, 25), Svar("avslatt", "§ 12-3", "Bergen", "fodselsdato"))
        .copy(
          tavle =
            listOf(
              TavleRad(1, "Kari", 25, 25, true, "datastar", false),
              TavleRad(2, "Ola", 10, 10, true, "datastar", true),
            )
        )

    val html = brett(to, "Ola")

    assertTrue(html.contains("Kari kom best ut av saken"), "hvem som kom best ut")
    assertTrue(html.contains("med 25 av 25 poeng"), "og med hvor mye")
  }

  @Test
  fun `med bare én på vakt står det ingenting om hvem som er best`() {
    val html = brett(oppgjorMed(Vurdering(true, true, true, true, 25), Svar()), "Kari")

    assertFalse(html.contains("kom best ut av saken"), "det er ingen å sammenligne med")
    assertFalse(html.contains("saksbehandlere har levert"), "heller ikke noen å vente på")
  }

  @Test
  fun `velkomsthilsenen forklarer hvorfor det finnes tre utgaver`() {
    val html = side(tilstand, null)

    assertTrue(html.contains("<fs-dialog id=\"velkomst\" open>"), "hilsenen skal stå åpen")
    assertTrue(html.contains("Velkommen til Førstelinja"))
    for (utgave in UTGAVER) {
      assertTrue(html.contains(utgave.navn), "${utgave.navn} mangler i valget")
    }
    assertTrue(html.contains("Du er her"), "utgaven du sitter i skal være merket")
    // Og på knappen, ikke bare et sted på siden: kommentaren ved siden av
    // inneholder ordet, og prøven besto på den.
    assertTrue(
      html.contains("""<button class="fs-button" value="lukk" autofocus>"""),
      "fokus skal stå på knappen, ikke på en lenke til en annen utgave",
    )
  }

  @Test
  fun `hilsenen kommer bare før du har meldt deg på`() {
    // Den står utenfor det serveren patcher, så den kan ikke sprette opp
    // igjen mens du spiller.
    assertFalse(side(tilstand, "Kari").contains("velkomst"), "hilsenen skal være borte etterpå")
  }

  @Test
  fun `du kan bytte utgave uten å gå via hilsenen`() {
    val html = side(tilstand, "Kari", listOf("Bergen"))

    assertTrue(html.contains("""aria-current="page""""), "utgaven du er i skal være merket")
    for (utgave in UTGAVER.filter { it.navn != APPNAVN }) {
      assertTrue(html.contains("""href="${utgave.adresse}""""), "mangler lenke til ${utgave.navn}")
    }
  }

  @Test
  fun `stilarket ligger i felles, og har det markupen trenger`() {
    // Fila deles av alle tre appene. Ligger den som en streng i én av dem,
    // er «samme skjerm i tre rammeverk» en påstand ingen kan holde.
    assertTrue(BRETT_CSS.contains(".topplinje"), "stilarket ser ikke ut som vårt")
    assertTrue(BRETT_CSS.contains(".brett__innhold"))
    assertTrue(BRETT_CSS.length > 5_000, "for lite til å være hele stilarket")
  }
}
