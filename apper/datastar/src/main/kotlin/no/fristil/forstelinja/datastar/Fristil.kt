package no.fristil.forstelinja.datastar


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

/**
 * Lenka til en annen utgave, med det du er og hva du har valgt.
 *
 * I drift ligger de tre utgavene på hvert sitt domene, og en kapsel gjelder
 * bare for sitt eget. Uten dette mistet du navnet ditt og plassen på tavla i
 * det du byttet, og måtte melde deg på som en ny saksbehandler. Id-en er
 * altså en billett som følger med i adressen, og den appen du kommer til
 * bytter den inn i sin egen kapsel og fjerner den fra adressen igjen.
 *
 * Billetten er i praksis nøkkelen til spilleren, og den som får lenka blir
 * deg. I et spill er det greit. I noe som betyr noe ville dette vært en
 * kortlevd engangsbillett fra spilltjeneren i stedet.
 */
fun lenkeTilUtgave(adresse: String, spillerId: String?, tema: String?): String {
  val deler = buildList {
    if (spillerId != null) add("spiller=${java.net.URLEncoder.encode(spillerId, "UTF-8")}")
    if (tema == "light" || tema == "dark") add("tema=$tema")
  }
  return if (deler.isEmpty()) adresse else "$adresse?${deler.joinToString("&")}"
}

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
const val FRISTIL_VERSJON = "0.13.0"

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
      /*
       * De fem under hentes bare av `@import` inne i `field.css` og
       * `suggestion.css`, og da i en andre runde: nettleseren må først laste
       * og lese den ytre fila før den vet at de finnes. På et mobilnett er
       * det en synlig forsinkelse, og den rammer nettopp feltene: kommunefeltet
       * og ledeteksten sto uten ramme og bakgrunn til andre runde kom.
       *
       * Står de her, hentes alt i første runde. `@import`-ene inne i de to
       * filene peker på de samme adressene, så de blir et treff i mellomlageret
       * og ingen ekstra tur.
       */
      "components/css/label/label.css",
      "components/css/input/input.css",
      "components/css/textarea/textarea.css",
      "components/css/help-text/help-text.css",
      "components/css/error-text/error-text.css",
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
