package no.fristil.forstelinja.datastar

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.html.div
import kotlinx.html.id
import kotlinx.html.label
import kotlinx.html.stream.createHTML

/**
 * At hjelperne bare er taggnavn, og ikke regner ut noe.
 *
 * Fristils kobling, altså id-er, `for` og `aria-describedby`, settes av
 * `<fs-field>` i nettleseren. Skrev vi den om igjen her, ville hver server
 * måttet gjøre det samme, og da er ikke designsystemet lenger uavhengig av
 * språket på serveren.
 */
class FristilDslTest {
  @Test
  fun `elementene kommer ut med riktig navn`() {
    val html =
      createHTML().div {
        fsField {}
        fsTabs { id = "faner" }
        fsPopover {}
        fsErrorSummary {}
        fsSuggestion {}
        fsToast {}
        fsConnectionStatus {}
        fsSessionTimeout {}
      }

    for (navn in
      listOf(
        "fs-field",
        "fs-tabs",
        "fs-popover",
        "fs-error-summary",
        "fs-suggestion",
        "fs-toast",
        "fs-connection-status",
        "fs-session-timeout",
      )) {
      assertTrue(html.contains("<$navn"), "mangler <$navn>")
    }
  }

  @Test
  fun `vanlige tagger kan skrives inni`() {
    val html = createHTML().div { fsField { label { +"E-post" } } }

    assertTrue(html.contains("<fs-field><label>E-post</label></fs-field>"))
  }
}
