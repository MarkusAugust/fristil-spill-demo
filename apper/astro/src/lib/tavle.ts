import { fs } from "@fristil/designsystem"

import { appnavn, farge, initialer } from "./tekst"
import type { TavleRad } from "./tilstand"

/**
 * Én rad på tavla, som HTML.
 *
 * Denne funksjonen kjører begge steder: serveren bruker den når siden
 * tegnes, og øya bruker den når tavla har endret seg. Uten den ville
 * radmarkupen finnes to ganger, i en mal og i et skript, og de to ville gått
 * fra hverandre første gang noen la til en kolonne.
 *
 * Attributtene kommer fra `fs`, ikke skrevet for hånd, så tavla i denne
 * appen kan ikke komme i utakt med de to andre.
 */

/** HTML-koder teksten. Navn kommer fra spillerne, og er ikke vår tekst. */
function trygg(tekst: string): string {
  return tekst
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
}

/** Skriver ut et attributtsett fra `fs` som tekst. */
function attributter(verdier: Record<string, unknown>): string {
  return Object.entries(verdier)
    .filter(([, verdi]) => verdi !== undefined && verdi !== false)
    .map(([navn, verdi]) => (verdi === true ? ` ${navn}` : ` ${navn}="${trygg(String(verdi))}"`))
    .join("")
}

export function tavlerad(rad: TavleRad, oppgjor: boolean): string {
  const merke = oppgjor
    ? `<span${attributter(fs.badge({ color: farge(rad.stack) }))}>${trygg(appnavn(rad.stack))}</span>`
    : rad.harSvart
      ? `<span${attributter(fs.badge({ color: "success" }))}>Levert</span>`
      : `<span${attributter(fs.badge())}>Jobber</span>`

  const runde =
    oppgjor && rad.sistePoeng > 0 ? `<span class="tavle__runde"> +${rad.sistePoeng}</span>` : ""

  return `<tr${rad.erMeg ? ' class="meg"' : ""}>
  <td>${rad.plass}</td>
  <td>
    <span class="tavle__navn">
      <span${attributter(fs.avatar({ size: "small" }))} aria-hidden="true">${trygg(initialer(rad.navn))}</span>
      <span>${trygg(rad.navn)}${rad.erMeg ? '<span class="tavle__deg"> (deg)</span>' : ""}</span>
    </span>
  </td>
  <td class="tavle__poeng">${rad.poeng}${runde}</td>
  <td>${merke}</td>
</tr>`
}

/** Hele tabellkroppen, tom tilstand medregnet. */
export function tavlekropp(rader: TavleRad[], oppgjor: boolean): string {
  if (rader.length === 0) return "<tr><td colspan=\"4\">Ingen på vakt.</td></tr>"
  return rader.map((rad) => tavlerad(rad, oppgjor)).join("\n")
}
