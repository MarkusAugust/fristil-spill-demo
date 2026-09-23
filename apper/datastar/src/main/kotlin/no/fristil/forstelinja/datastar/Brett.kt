package no.fristil.forstelinja.datastar

import java.io.File

/**
 * Stilarket for brettet, lest fra `felles/brett.css`.
 *
 * Fila deles av alle tre appene. Skal demoen vise den samme skjermen i tre
 * rammeverk, kan ikke hver app ha sin egen utgave av hvor boksene ligger, og
 * en Kotlin-streng i denne modulen ville vært nettopp det.
 *
 * Den leses én gang ved oppstart. Endrer du CSS-en, start appen på nytt; det
 * er det samme som for sakene.
 */
val BRETT_CSS: String by lazy {
  val sti = System.getenv("BRETT_CSS_FIL") ?: "../../felles/brett.css"
  val fil = File(sti)
  require(fil.exists()) { "Fant ingen brett.css på «$sti». Sett BRETT_CSS_FIL." }
  fil.readText()
}

/**
 * Panelet som viser hva som ble oppdatert, lest fra `felles/panel.js`.
 *
 * Samme fil og samme grunn som stilarket: panelet skal være det samme i de tre
 * utgavene, ellers viser det forskjeller som er panelets egne.
 */
val PANEL_JS: String by lazy {
  val sti = System.getenv("PANEL_JS_FIL") ?: "../../felles/panel.js"
  val fil = File(sti)
  require(fil.exists()) { "Fant ingen panel.js på «$sti». Sett PANEL_JS_FIL." }
  fil.readText()
}
