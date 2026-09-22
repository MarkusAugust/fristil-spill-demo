package no.fristil.forstelinja.datastar.sdk

enum class Case(
    val value: String,
) {
    CAMEL("camel"),
    KEBAB("kebab"),
    SNAKE("snake"),
    PASCAL("pascal"),
}

class OnModifiers {
    var once: Boolean = false
    var passive: Boolean = false
    var capture: Boolean = false
    var delayMs: Int? = null
    var debounceMs: Int? = null
    var debounceLeading: Boolean = false
    var debounceNoTrailing: Boolean = false
    var throttleMs: Int? = null
    var throttleNoLeading: Boolean = false
    var throttleTrailing: Boolean = false
    var viewTransition: Boolean = false
    var window: Boolean = false
    var document: Boolean = false
    var outside: Boolean = false
    var preventDefault: Boolean = false
    var stopPropagation: Boolean = false
    var eventCase: Case? = null

    internal fun build(): String =
        buildString {
            if (once) append("__once")
            if (passive) append("__passive")
            if (capture) append("__capture")
            eventCase?.let { append("__case.${it.value}") }
            delayMs?.let { append("__delay.${it}ms") }
            debounceMs?.let {
                append("__debounce.${it}ms")
                if (debounceLeading) append(".leading")
                if (debounceNoTrailing) append(".notrailing")
            }
            throttleMs?.let {
                append("__throttle.${it}ms")
                if (throttleNoLeading) append(".noleading")
                if (throttleTrailing) append(".trailing")
            }
            if (viewTransition) append("__viewtransition")
            if (window) append("__window")
            if (document) append("__document")
            if (outside) append("__outside")
            if (preventDefault) append("__prevent")
            if (stopPropagation) append("__stop")
        }
}

class IntersectModifiers {
    var once: Boolean = false
    var exit: Boolean = false
    var half: Boolean = false
    var full: Boolean = false

    // 0-100 (percentage of element visible)
    var thresholdPercent: Int? = null

    var delayMs: Int? = null
    var debounceMs: Int? = null
    var debounceLeading: Boolean = false
    var debounceNoTrailing: Boolean = false
    var throttleMs: Int? = null
    var throttleNoLeading: Boolean = false
    var throttleTrailing: Boolean = false
    var viewTransition: Boolean = false

    internal fun build(): String =
        buildString {
            if (once) append("__once")
            if (exit) append("__exit")
            if (half) append("__half")
            if (full) append("__full")
            thresholdPercent?.let { append("__threshold.$it") }
            delayMs?.let { append("__delay.${it}ms") }
            debounceMs?.let {
                append("__debounce.${it}ms")
                if (debounceLeading) append(".leading")
                if (debounceNoTrailing) append(".notrailing")
            }
            throttleMs?.let {
                append("__throttle.${it}ms")
                if (throttleNoLeading) append(".noleading")
                if (throttleTrailing) append(".trailing")
            }
            if (viewTransition) append("__viewtransition")
        }
}

class IntervalModifiers {
    // Interval duration.
    var durationMs: Int? = null

    // Execute the first interval immediately (requires durationMs set).
    var leading: Boolean = false
    var viewTransition: Boolean = false

    internal fun build(): String =
        buildString {
            durationMs?.let {
                append("__duration.${it}ms")
                if (leading) append(".leading")
            }
            if (viewTransition) append("__viewtransition")
        }
}

class TimingModifiers {
    var delayMs: Int? = null
    var debounceMs: Int? = null
    var debounceLeading: Boolean = false
    var debounceNoTrailing: Boolean = false
    var throttleMs: Int? = null
    var throttleNoLeading: Boolean = false
    var throttleTrailing: Boolean = false

    internal fun build(): String =
        buildString {
            delayMs?.let { append("__delay.${it}ms") }
            debounceMs?.let {
                append("__debounce.${it}ms")
                if (debounceLeading) append(".leading")
                if (debounceNoTrailing) append(".notrailing")
            }
            throttleMs?.let {
                append("__throttle.${it}ms")
                if (throttleNoLeading) append(".noleading")
                if (throttleTrailing) append(".trailing")
            }
        }
}

class BindModifiers {
    var case: Case? = null

    // Bind to a specific property instead of the default one (must not be read-only).
    var prop: String? = null

    // Which events sync the element property back to the signal.
    var events: List<String>? = null

    internal fun build(): String =
        buildString {
            case?.let { append("__case.${it.value}") }
            prop?.let { append("__prop.$it") }
            events?.let { list ->
                append("__event")
                list.forEach { append(".$it") }
            }
        }
}

// Filter object for data-on-signal-patch-filter and similar attributes that use {include: /../, exclude: /../}.
class SignalFilter(
    val include: Regex? = null,
    val exclude: Regex? = null,
) {
    override fun toString(): String {
        val parts =
            buildList {
                include?.let { add("include: /${it.pattern}/") }
                exclude?.let { add("exclude: /${it.pattern}/") }
            }
        return "{${parts.joinToString(", ")}}"
    }
}
