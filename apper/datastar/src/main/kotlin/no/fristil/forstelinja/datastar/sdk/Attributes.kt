/*
 * Datastar-attributter for kotlinx.html.
 *
 * Hentet fra Markus' egen Datastar-SDK og tilpasset: pakkenavnet er endret,
 * og Jackson er byttet ut med kotlinx.serialization, som er det resten av
 * appen bruker.
 *
 * Poenget med dem er at `data-on:click__debounce.500ms` ikke skal skrives
 * som en streng man kan stave feil, men settes sammen av typede biter.
 */
package no.fristil.forstelinja.datastar.sdk

import kotlinx.html.HTMLTag
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json

// ===== Signals =====

// Object form: data-signals="{foo: 1, bar: 2}"
fun HTMLTag.dataSignals(
    expression: String,
    case: Case? = null,
    ifMissing: Boolean = false,
) {
    attributes["data-signals${signalMods(case, ifMissing)}"] = expression
}

// Key form: data-signals:foo="1"
fun HTMLTag.dataSignals(
    name: String,
    expression: String,
    case: Case? = null,
    ifMissing: Boolean = false,
) {
    attributes["data-signals:$name${signalMods(case, ifMissing)}"] = expression
}

// Convenience: build the object form from Kotlin pairs.
fun HTMLTag.dataSignals(
    vararg pairs: Pair<String, Any?>,
    case: Case? = null,
    ifMissing: Boolean = false,
) {
    attributes["data-signals${signalMods(case, ifMissing)}"] = signalJson(pairs.toMap())
}

private fun signalMods(
    case: Case?,
    ifMissing: Boolean,
): String =
    buildString {
        case?.let { append("__case.${it.value}") }
        if (ifMissing) append("__ifmissing")
    }

// Object form: data-computed="{foo: () => $bar + $baz}"
fun HTMLTag.dataComputed(
    expression: String,
    case: Case? = null,
) {
    attributes["data-computed${caseMod(case)}"] = expression
}

// Key form: data-computed:foo="$bar + $baz"
fun HTMLTag.dataComputed(
    name: String,
    expression: String,
    case: Case? = null,
) {
    attributes["data-computed:$name${caseMod(case)}"] = expression
}

// Renders current signals as JSON in the element's text content. Filter is optional.
fun HTMLTag.dataJsonSignals(
    filter: SignalFilter? = null,
    terse: Boolean = false,
) {
    val key = if (terse) "data-json-signals__terse" else "data-json-signals"
    attributes[key] = filter?.toString() ?: ""
}

private fun caseMod(case: Case?): String = case?.let { "__case.${it.value}" } ?: ""

// ===== Lifecycle =====

fun HTMLTag.dataInit(
    expression: String,
    delayMs: Int? = null,
    viewTransition: Boolean = false,
) {
    val mods =
        buildString {
            delayMs?.let { append("__delay.${it}ms") }
            if (viewTransition) append("__viewtransition")
        }
    attributes["data-init$mods"] = expression
}

fun HTMLTag.dataEffect(expression: String) {
    attributes["data-effect"] = expression
}

// ===== Event listeners =====

fun HTMLTag.dataOn(
    event: String,
    expression: String,
    modifiers: OnModifiers.() -> Unit = {},
) {
    val mods = OnModifiers().apply(modifiers).build()
    attributes["data-on:$event$mods"] = expression
}

fun HTMLTag.dataOnClick(
    expression: String,
    modifiers: OnModifiers.() -> Unit = {},
) = dataOn("click", expression, modifiers)

fun HTMLTag.dataOnSubmit(
    expression: String,
    modifiers: OnModifiers.() -> Unit = {},
) = dataOn("submit", expression, modifiers)

fun HTMLTag.dataOnChange(
    expression: String,
    modifiers: OnModifiers.() -> Unit = {},
) = dataOn("change", expression, modifiers)

fun HTMLTag.dataOnInput(
    expression: String,
    modifiers: OnModifiers.() -> Unit = {},
) = dataOn("input", expression, modifiers)

fun HTMLTag.dataOnSignalPatch(
    expression: String,
    filter: SignalFilter? = null,
    modifiers: TimingModifiers.() -> Unit = {},
) {
    val mods = TimingModifiers().apply(modifiers).build()
    attributes["data-on-signal-patch$mods"] = expression
    filter?.let { attributes["data-on-signal-patch-filter"] = it.toString() }
}

fun HTMLTag.dataOnInterval(
    expression: String,
    modifiers: IntervalModifiers.() -> Unit = {},
) {
    val mods = IntervalModifiers().apply(modifiers).build()
    attributes["data-on-interval$mods"] = expression
}

fun HTMLTag.dataOnIntersect(
    expression: String,
    modifiers: IntersectModifiers.() -> Unit = {},
) {
    val mods = IntersectModifiers().apply(modifiers).build()
    attributes["data-on-intersect$mods"] = expression
}

// ===== Binding =====

fun HTMLTag.dataBind(
    signal: String,
    modifiers: BindModifiers.() -> Unit = {},
) {
    val mods = BindModifiers().apply(modifiers).build()
    attributes["data-bind$mods"] = signal
}

fun HTMLTag.dataRef(
    name: String,
    case: Case? = null,
) {
    attributes["data-ref${caseMod(case)}"] = name
}

// ===== Display =====

fun HTMLTag.dataText(expression: String) {
    attributes["data-text"] = expression
}

fun HTMLTag.dataShow(expression: String) {
    attributes["data-show"] = expression
}

// Object form: data-class="{active: $selected, disabled: $loading}"
fun HTMLTag.dataClass(
    expression: String,
    case: Case? = null,
) {
    attributes["data-class${caseMod(case)}"] = expression
}

// Key form: data-class:active="$selected"
fun HTMLTag.dataClass(
    name: String,
    expression: String,
    case: Case? = null,
) {
    attributes["data-class:$name${caseMod(case)}"] = expression
}

// Object form: data-style="{color: $red ? 'red' : 'blue'}"
fun HTMLTag.dataStyle(expression: String) {
    attributes["data-style"] = expression
}

// Key form: data-style:color="$red ? 'red' : 'blue'"
fun HTMLTag.dataStyle(
    name: String,
    expression: String,
) {
    attributes["data-style:$name"] = expression
}

// Object form: data-attr="{aria-label: $label, disabled: $disabled}"
fun HTMLTag.dataAttr(expression: String) {
    attributes["data-attr"] = expression
}

// Key form: data-attr:aria-label="$label"
fun HTMLTag.dataAttr(
    name: String,
    expression: String,
) {
    attributes["data-attr:$name"] = expression
}

fun HTMLTag.dataIndicator(
    signal: String,
    case: Case? = null,
) {
    attributes["data-indicator${caseMod(case)}"] = signal
}

// ===== Morphing / walker control =====

fun HTMLTag.dataIgnore(selfOnly: Boolean = false) {
    val key = if (selfOnly) "data-ignore__self" else "data-ignore"
    attributes[key] = ""
}

fun HTMLTag.dataIgnoreMorph() {
    attributes["data-ignore-morph"] = ""
}

fun HTMLTag.dataPreserveAttr(names: String) {
    attributes["data-preserve-attr"] = names
}

/**
 * Signalobjektet som JSON.
 *
 * Erstatter Jackson fra den opprinnelige SDK-en. Bare de verdiene et signal
 * kan ha, og ukjente typer skrives som tekst framfor å kaste.
 */
private fun signalJson(verdier: Map<String, Any?>): String =
  JsonObject(
      verdier.mapValues { (_, v) ->
        when (v) {
          null -> JsonPrimitive(null as String?)
          is Number -> JsonPrimitive(v)
          is Boolean -> JsonPrimitive(v)
          else -> JsonPrimitive(v.toString())
        }
      }
    )
    .toString()
