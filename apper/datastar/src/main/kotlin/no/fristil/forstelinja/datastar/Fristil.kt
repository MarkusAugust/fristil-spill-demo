package no.fristil.forstelinja.datastar

/**
 * Kontrakten Fristil krever av en server som ikke kan kalle `fs.field()`.
 *
 * Denne appen er skrevet i Kotlin. Den kan altså ikke kalle byggefunksjonene
 * i pakken, og må i stedet skrive klassene og koblingen selv, og la
 * `<fs-field>` gjøre resten i nettleseren.
 *
 * Fordi serveren sender det samme området på nytt ved hver patch, må malen i
 * tillegg skrive `data-preserve-attr` med navnene på alt komponenten setter.
 * Lista leses fra serverens node, per element, så komponenten kan ikke
 * beskytte seg selv: uten listene river morfingen bort koblingen ved første
 * oppdatering.
 *
 * Verdiene er hentet fra @fristil/designsystem 0.6.1, ikke skrevet av
 * hukommelsen.
 */
object Bevar {
  const val LEDETEKST = "class for data-required data-optional aria-disabled"
  const val KONTROLL = "id aria-describedby aria-invalid data-state disabled aria-disabled"
  const val HJELPETEKST = "id"
  const val FEILMELDING = "id hidden"

  const val SPRETTOPP_VERT = "open"
  const val SPRETTOPP_KNAPP = "aria-expanded"
  const val SPRETTOPP_PANEL = "style"

  const val FANE = "aria-selected tabindex"
  const val FANEPANEL = "hidden"

  const val FORSLAG_KONTROLL = "aria-expanded aria-activedescendant"
  const val FORSLAG_LISTE = "hidden"
  const val FORSLAG_VALG = "aria-selected hidden"
  const val FORSLAG_TOM = "hidden"
}

/** Versjonen av designsystemet siden henter fra CDN. */
const val FRISTIL_VERSJON = "0.6.1"

private const val CDN = "https://cdn.jsdelivr.net/npm/@fristil/designsystem@$FRISTIL_VERSJON"

/**
 * Stilarkene siden trenger, hentet fra CDN.
 *
 * Ingen npm, ingen byggesteg. Det er sporet «Uten byggverktøy» i Fristils
 * egen dokumentasjon, og denne appen er beviset på at det virker.
 */
val STILARK =
  listOf(
      "tokens/tokens.css",
      "components/ramme/field/field.css",
      "components/ramme/tabs/tabs.css",
      "components/ramme/popover/popover.css",
      "components/ramme/dialog/dialog.css",
      "components/ramme/error-summary/error-summary.css",
      "components/ramme/suggestion/suggestion.css",
      "components/frittstaende/session-timeout/session-timeout.css",
      "components/frittstaende/toast/toast.css",
      "components/frittstaende/connection-status/connection-status.css",
      "components/css/button/button.css",
      "components/css/select/select.css",
      "components/css/radio/radio.css",
      "components/css/fieldset/fieldset.css",
      "components/css/table/table.css",
      "components/css/card/card.css",
      "components/css/badge/badge.css",
      "components/css/tag/tag.css",
      "components/css/alert/alert.css",
      "components/css/heading/heading.css",
      "components/css/paragraph/paragraph.css",
      "components/css/list/list.css",
      "components/css/divider/divider.css",
      "components/css/sr-only/sr-only.css",
    )
    .map { "$CDN/src/$it" }

/** Web-komponentene siden registrerer, også fra CDN. */
val KOMPONENTER =
  listOf(
      "components/ramme/field/fs-field.js" to "defineFsField",
      "components/ramme/tabs/fs-tabs.js" to "defineFsTabs",
      "components/ramme/popover/fs-popover.js" to "defineFsPopover",
      "components/ramme/dialog/fs-dialog.js" to "defineFsDialog",
      "components/frittstaende/toast/fs-toast.js" to "defineFsToast",
      "components/frittstaende/connection-status/fs-connection-status.js" to
        "defineFsConnectionStatus",
      "components/ramme/error-summary/fs-error-summary.js" to "defineFsErrorSummary",
      "components/ramme/suggestion/fs-suggestion.js" to "defineFsSuggestion",
      "components/frittstaende/session-timeout/fs-session-timeout.js" to
        "defineFsSessionTimeout",
    )
    .map { (fil, funksjon) -> "$CDN/dist/$fil" to funksjon }

const val DATASTAR_CDN = "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.4/bundles/datastar.js"
