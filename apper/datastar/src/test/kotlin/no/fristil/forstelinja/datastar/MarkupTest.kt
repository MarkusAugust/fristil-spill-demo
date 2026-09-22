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
      naMs = 0,
      sak =
        SakUt(
          id = "2024/1187",
          tittel = "Høner",
          sammendrag = "…",
          soker = Soker("Bjørg", "31.02.1974", "Bergen", "b@example.no"),
        ),
      hjemler = listOf(Hjemmel("§ 4-1", "Alminnelig")),
      tavle = listOf(TavleRad(1, "Kari", 0, "datastar", true)),
      meg = MegUt("Kari", 0, 0, 1, 0, false),
      evigToppliste = emptyList(),
    )

  @Test
  fun `feltene bærer bevaringslistene komponenten trenger`() {
    val html = brett(tilstand, "Kari")

    assertTrue(html.contains("""data-preserve-attr="${Bevar.LEDETEKST}""""), "ledeteksten")
    assertTrue(html.contains("""data-preserve-attr="${Bevar.KONTROLL}""""), "kontrollen")
    assertTrue(html.contains("""data-preserve-attr="${Bevar.HJELPETEKST}""""), "hjelpeteksten")
  }

  @Test
  fun `fanene bærer sine, siden komponenten flytter valget`() {
    val html = brett(tilstand, "Kari")

    assertEquals(2, Regex("""data-preserve-attr="${Regex.escape(Bevar.FANE)}"""").findAll(html).count())
    assertEquals(2, Regex("""data-preserve-attr="${Regex.escape(Bevar.FANEPANEL)}"""").findAll(html).count())
  }

  @Test
  fun `ledetekst og felt er koblet, slik fs-field ville gjort det`() {
    val html = brett(tilstand, "Kari")

    for (id in listOf("hjemmel", "felle")) {
      assertTrue(html.contains("""<label class="fs-label" for="$id""""), "ledetekst for $id")
      assertTrue(html.contains("""id="$id""""), "felt $id")
    }
  }

  @Test
  fun `de frittstående komponentene ber morfingen holde seg unna`() {
    val html = side(tilstand, "Kari")

    // De eier sitt eget innhold, og serveren har ingenting å sende for dem.
    // Åpningstaggen kan gå over flere linjer, så hele den leses.
    for (tagg in listOf("fs-toast", "fs-connection-status")) {
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
    val html = brett(tilstand, "Kari")

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
}
