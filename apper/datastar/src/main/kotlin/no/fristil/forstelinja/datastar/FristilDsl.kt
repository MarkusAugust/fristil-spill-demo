package no.fristil.forstelinja.datastar

import kotlinx.html.FlowContent
import kotlinx.html.HTMLTag
import kotlinx.html.HtmlBlockTag
import kotlinx.html.TagConsumer
import kotlinx.html.attributesMapOf
import kotlinx.html.label
import kotlinx.html.p
import kotlinx.html.visit

/**
 * Fristils komponenter som Kotlin, ikke som strenger.
 *
 * To ting løses her.
 *
 * Det ene er redigering: HTML inne i en strengmal får ingen hjelp i VS Code,
 * verken fullføring, formatering eller feil før den kjører. Som kotlinx.html
 * er markupen Kotlin-kode, og får all hjelpen editoren har for Kotlin.
 *
 * Det andre er viktigere. `fs.field()` regner ut id-er, `for` og
 * `aria-describedby` for den som skriver markup i JavaScript. Kotlin kan
 * ikke kalle den funksjonen, men den kan ha sin egen, og det er nettopp det
 * Fristils dokumentasjon anbefaler for en server som skriver hele markupen
 * selv. `fsFelt` under er den funksjonen. Da kan koblingen ikke glemmes, og
 * ingenting trenger å fredes med `data-preserve-attr`, for komponenten har
 * ingenting å legge til.
 */

// ===== De egendefinerte elementene =====

/**
 * Et egendefinert element.
 *
 * `HtmlBlockTag` er nøkkelen: den gjør at vanlige tagger som `label` og `p`
 * kan skrives inni, akkurat som i en `div`.
 */
class FsTag(navn: String, consumer: TagConsumer<*>) :
  HTMLTag(navn, consumer, attributesMapOf(), null, false, false), HtmlBlockTag

fun FlowContent.fsField(block: FsTag.() -> Unit) = FsTag("fs-field", consumer).visit(block)

fun FlowContent.fsTabs(block: FsTag.() -> Unit) = FsTag("fs-tabs", consumer).visit(block)

fun FlowContent.fsPopover(block: FsTag.() -> Unit) = FsTag("fs-popover", consumer).visit(block)

fun FlowContent.fsErrorSummary(block: FsTag.() -> Unit) =
  FsTag("fs-error-summary", consumer).visit(block)

fun FlowContent.fsSuggestion(block: FsTag.() -> Unit) =
  FsTag("fs-suggestion", consumer).visit(block)

fun FlowContent.fsToast(block: FsTag.() -> Unit) = FsTag("fs-toast", consumer).visit(block)

fun FlowContent.fsConnectionStatus(block: FsTag.() -> Unit) =
  FsTag("fs-connection-status", consumer).visit(block)

fun FlowContent.fsSessionTimeout(block: FsTag.() -> Unit) =
  FsTag("fs-session-timeout", consumer).visit(block)

// ===== Kontrakten =====

/**
 * Id-ene og koblingen for ett felt, regnet ut ett sted.
 *
 * Samme utregning som `computeFieldAttributes` i pakken: hjelpeteksten er
 * alltid med i `aria-describedby`, feilmeldingen bare når feltet faktisk er
 * ugyldig. Ellers ville koblingen pekt på noe som er skjult, og skjermlesere
 * melder da enten ingenting eller noe som ikke står på skjermen.
 */
class Feltkobling(val id: String, val harHjelp: Boolean, val harFeil: Boolean, val ugyldig: Boolean) {
  val hjelpId = "$id-hjelp"
  val feilId = "$id-feil"

  val beskrevetAv: String? =
    listOfNotNull(hjelpId.takeIf { harHjelp }, feilId.takeIf { ugyldig && harFeil })
      .joinToString(" ")
      .ifBlank { null }
}

/**
 * Et felt med ledetekst, hjelpetekst og feilmelding, ferdig koblet.
 *
 * Kallstedet skriver bare selve kontrollen, og får `id`, `aria-describedby`,
 * `aria-invalid` og `data-state` inn i `kobling`.
 */
fun FlowContent.fsFelt(
  id: String,
  ledetekst: String,
  hjelp: String? = null,
  feilmelding: String? = null,
  ugyldig: Boolean = false,
  kontroll: FlowContent.(Feltkobling) -> Unit,
) {
  val kobling = Feltkobling(id, hjelp != null, feilmelding != null, ugyldig)

  fsField {
    label(classes = "fs-label") {
      attributes["for"] = id
      +ledetekst
    }

    this.kontroll(kobling)

    if (hjelp != null) {
      p(classes = "fs-help-text") {
        attributes["id"] = kobling.hjelpId
        +hjelp
      }
    }

    if (feilmelding != null) {
      p(classes = "fs-error-text") {
        attributes["id"] = kobling.feilId
        if (!ugyldig) attributes["hidden"] = "hidden"
        +feilmelding
      }
    }
  }
}

/** Setter `id`, `aria-describedby` og valideringstilstanden på en kontroll. */
fun HTMLTag.koble(kobling: Feltkobling) {
  attributes["id"] = kobling.id
  kobling.beskrevetAv?.let { attributes["aria-describedby"] = it }
  if (kobling.ugyldig) {
    attributes["aria-invalid"] = "true"
    attributes["data-state"] = "invalid"
  }
}
