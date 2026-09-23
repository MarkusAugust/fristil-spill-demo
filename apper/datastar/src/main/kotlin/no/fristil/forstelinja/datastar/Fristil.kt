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
 * Verdiene er hentet fra @fristil/designsystem 0.7.0, ikke skrevet
 * av hukommelsen.
 */
object Bevar {
  /**
   * Hele lista Fristil oppgir, for markup ingen rår over.
   *
   * Den er med for fullstendighetens skyld. Denne appen bruker den ikke:
   * `aria-invalid` og `data-state` er serverens, og fredet vi dem, kunne
   * serveren aldri meldt et felt som ugyldig. Morfingen ville nektet å sette
   * dem.
   */
  const val LEDETEKST = "class for data-required data-optional aria-disabled"
  const val KONTROLL = "id aria-describedby aria-invalid data-state disabled aria-disabled"
  const val HJELPETEKST = "id"
  const val FEILMELDING = "id hidden"

  /**
   * Det `<fs-field>` fyller inn når serveren bare sender struktur.
   *
   * Sender serveren et felt uten id-er, lager komponenten dem i nettleseren.
   * Serveren skriver dem aldri, så morfingen river dem bort ved neste patch
   * av samme område, og feltet mister koblingen mellom ledetekst, kontroll
   * og hjelpetekst. Det skjer her hver gang et skjema sendes inn med feil,
   * og hver gang runden skifter.
   *
   * Smalere enn listene over med vilje: bare det komponenten legger til, og
   * ingenting serveren selv skriver.
   */
  const val KOBLING_LEDETEKST = "class for"
  const val KOBLING_KONTROLL = "id aria-describedby"
  const val KOBLING_HJELPETEKST = "id"

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

/**
 * Navnet denne utgaven har utad.
 *
 * De tre appene er den samme skjermen i hvert sitt rammeverk, og hver av dem
 * sier hvem den er i velkomsthilsenen og i topplinja.
 */
const val APPNAVN = "Datastar"

/**
 * De tre utgavene, og hvor de kjører.
 *
 * Adressene settes med miljøvariabler, slik at det samme oppsettet virker
 * lokalt og utrullet. Lokalt deler de tre kapselen, siden kapsler ikke bryr
 * seg om portnummer, og da beholder du navnet ditt når du bytter utgave.
 */
data class Utgave(val navn: String, val rammeverk: String, val adresse: String)

val UTGAVER =
  listOf(
    Utgave(
      "TanStack Start",
      "React, tegnet i nettleseren",
      System.getenv("TANSTACK_URL") ?: "http://localhost:8082",
    ),
    Utgave(
      "Datastar",
      "Kotlin, ferdige HTML-biter fra serveren",
      System.getenv("DATASTAR_URL") ?: "http://localhost:8081",
    ),
    Utgave(
      "Astro",
      "hele sider fra serveren, med én øy",
      System.getenv("ASTRO_URL") ?: "http://localhost:8083",
    ),
  )

/** Versjonen av designsystemet siden henter fra CDN. */
const val FRISTIL_VERSJON = "0.8.2"

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
      "components/frittstaende/connection-status/connection-status.css",
      "components/css/button/button.css",
      "components/css/select/select.css",
      "components/css/radio/radio.css",
      "components/css/fieldset/fieldset.css",
      "components/css/table/table.css",
      "components/css/card/card.css",
      "components/css/avatar/avatar.css",
      "components/css/badge/badge.css",
      "components/css/tag/tag.css",
      "components/css/toggle-group/toggle-group.css",
      "components/css/alert/alert.css",
      "components/css/heading/heading.css",
      "components/css/paragraph/paragraph.css",
      "components/css/list/list.css",
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
      "components/frittstaende/connection-status/fs-connection-status.js" to
        "defineFsConnectionStatus",
      "components/ramme/error-summary/fs-error-summary.js" to "defineFsErrorSummary",
      "components/ramme/suggestion/fs-suggestion.js" to "defineFsSuggestion",
    )
    .map { (fil, funksjon) -> "$CDN/dist/$fil" to funksjon }

const val DATASTAR_CDN = "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.4/bundles/datastar.js"
