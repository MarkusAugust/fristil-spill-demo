package no.fristil.forstelinja.datastar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.stream.createHTML

/**
 * At `fsFelt` skriver den samme kontrakten `fs.field()` gjør.
 *
 * Det er nettopp det Fristils dokumentasjon anbefaler for en server som ikke
 * kan kalle TypeScript-funksjonen: skriv hele koblingen selv, så har
 * komponenten ingenting å legge til, og ingenting trenger å fredes.
 */
class FristilDslTest {
  private fun felt(ugyldig: Boolean = false, hjelp: String? = "Hjelp", feil: String? = "Feil") =
    createHTML().div {
      fsFelt(id = "epost", ledetekst = "E-post", hjelp = hjelp, feilmelding = feil, ugyldig = ugyldig) {
        input { koble(it) }
      }
    }

  @Test
  fun `ledeteksten peker på feltet, og feltet har id-en`() {
    val html = felt()

    assertTrue(html.contains("""<label class="fs-label" for="epost">"""))
    assertTrue(html.contains("""id="epost""""))
  }

  @Test
  fun `hjelpeteksten er alltid med i aria-describedby`() {
    assertTrue(felt().contains("""aria-describedby="epost-hjelp""""))
  }

  @Test
  fun `feilmeldingen er skjult og utenfor koblingen så lenge feltet er gyldig`() {
    val html = felt(ugyldig = false)

    // `hidden="hidden"` er kotlinx.html sin skrivemåte for et boolsk
    // attributt, og er gyldig HTML.
    assertTrue(html.contains("""<p class="fs-error-text" id="epost-feil" hidden="hidden">"""))

    // Ellers ville aria-describedby pekt på noe skjult, og skjermlesere
    // melder da enten ingenting eller noe som ikke står på skjermen.
    val koblingen = Regex("""aria-describedby="([^"]*)"""").find(html)?.groupValues?.get(1)
    assertEquals("epost-hjelp", koblingen, "feilen skal ikke være i koblingen når feltet er gyldig")
  }

  @Test
  fun `et ugyldig felt melder fra, og feilen blir synlig og koblet`() {
    val html = felt(ugyldig = true)

    assertTrue(html.contains("""aria-invalid="true""""))
    assertTrue(html.contains("""data-state="invalid""""))
    assertTrue(html.contains("""aria-describedby="epost-hjelp epost-feil""""))
    assertFalse(html.contains("""id="epost-feil" hidden"""), "feilen skal vises")
    assertTrue(html.contains("""<p class="fs-error-text" id="epost-feil">"""))
  }

  @Test
  fun `et felt uten hjelpetekst får ingen kobling å peke med`() {
    val html = felt(hjelp = null, feil = null)

    assertFalse(html.contains("aria-describedby"))
  }

  @Test
  fun `ingenting fredes, for komponenten har ingenting å legge til`() {
    assertFalse(felt().contains("data-preserve-attr"))
  }

  @Test
  fun `de egendefinerte elementene kommer ut med riktig navn`() {
    val html =
      createHTML().div {
        fsTabs { id = "faner" }
        fsPopover {}
        fsSuggestion {}
      }

    assertTrue(html.contains("""<fs-tabs id="faner">"""))
    assertTrue(html.contains("<fs-popover>"))
    assertTrue(html.contains("<fs-suggestion>"))
  }
}
