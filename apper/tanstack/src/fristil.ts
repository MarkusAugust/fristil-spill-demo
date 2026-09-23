/**
 * Fristil i denne appen.
 *
 * Til forskjell fra Datastar-appen henter denne pakken **fra npm**, ikke fra
 * CDN. Det er den andre veien inn i designsystemet, og halve poenget med å
 * ha tre apper: den ene beviser at det virker uten byggverktøy, den andre at
 * det virker med.
 *
 * Byggefunksjonene kommer fra `@fristil/designsystem/react`, som gir
 * `className` og `htmlFor` i stedet for `class` og `for`.
 */

/** Navnet denne utgaven har utad. */
export const APPNAVN = "TanStack Start"

/** De tre utgavene, og hvor de kjører. */
export type Utgave = { navn: string; rammeverk: string; adresse: string }

export const UTGAVER: Utgave[] = [
  {
    navn: "TanStack Start",
    rammeverk: "React, tegnet i nettleseren",
    adresse: process.env.TANSTACK_URL ?? "http://localhost:8082",
  },
  {
    navn: "Datastar",
    rammeverk: "Kotlin, ferdige HTML-biter fra serveren",
    adresse: process.env.DATASTAR_URL ?? "http://localhost:8081",
  },
  {
    navn: "Astro",
    rammeverk: "hele sider fra serveren, med én øy",
    adresse: process.env.ASTRO_URL ?? "http://localhost:8083",
  },
]

/**
 * Stilarkene siden trenger.
 *
 * Importert, ikke lenket: her er det en bunter, og da er det den som skal
 * sette dem sammen. Lista er den samme som i Datastar-appen, og det er med
 * vilje: to veier inn, samme utseende ut.
 */
export const STILARK = [
  "tokens/tokens.css",
  "components/ramme/field/field.css",
  "components/ramme/tabs/tabs.css",
  "components/ramme/popover/popover.css",
  "components/ramme/error-summary/error-summary.css",
  "components/ramme/suggestion/suggestion.css",
  "components/ramme/dialog/dialog.css",
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
]
