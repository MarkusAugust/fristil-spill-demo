package no.fristil.forstelinja.datastar

import kotlinx.html.FlowContent
import kotlinx.html.HTMLTag
import kotlinx.html.HtmlBlockTag
import kotlinx.html.TagConsumer
import kotlinx.html.attributesMapOf
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
 * Her står bare taggnavnene. Ingen av dem regner ut noe.
 *
 * Det var fristende å skrive en Kotlin-utgave av `fs.field()`, som regnet ut
 * id-er, `for` og `aria-describedby`. Det ville brutt med hele poenget:
 * skulle hver server skrive Fristils regler på nytt, ville Fristil vært et
 * JavaScript-designsystem med en manuell reserve for alle andre.
 *
 * Serveren sender struktur. `<fs-field>` setter koblingen i nettleseren, og
 * gjør det likt uansett hvilket språk markupen kom fra. Det eneste som
 * kreves er at oppdateringene er smale, så koblingen aldri rives bort.
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
