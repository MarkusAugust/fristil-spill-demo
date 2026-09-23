/**
 * Fargetemaet, som brukeren velger og serveren må kjenne.
 *
 * Fristil følger `prefers-color-scheme` av seg selv, og `data-theme` på rota
 * overstyrer. Valget lagres i en kapsel framfor i `localStorage`, fordi
 * serveren skriver `<html>` og må kunne sette attributtet selv. Gjør den
 * ikke det, melder React avvik ved hydreringen, og siden blinker lyst før
 * skriptet rekker å rette den.
 *
 * Kapselen er ikke `httpOnly`: det er velgeren i siden som skriver den.
 */
export const KAPSEL_TEMA = "forstelinja-tema"

export type Tema = "light" | "dark" | "system"

export function erTema(verdi: string | undefined): verdi is Tema {
  return verdi === "light" || verdi === "dark" || verdi === "system"
}

/** Leser valget i nettleseren. */
export function lesTema(): Tema {
  const treff = document.cookie.match(/(?:^|;\s*)forstelinja-tema=([^;]*)/)
  const verdi = treff?.[1]
  return erTema(verdi) ? verdi : "system"
}

/** Skriver valget, og setter det på rota med én gang. */
export function settTema(tema: Tema): void {
  // `SameSite=None` når siden er på https, ellers `Lax`. Samme grunn som for
  // spillerkapselen: i skallet står utgavene i hver sin ramme, på hvert sitt
  // domene, og en `Lax`-kapsel sendes ikke derfra.
  const tvers =
    location.protocol === "https:" ? "SameSite=None; Secure" : "SameSite=Lax"
  document.cookie = `${KAPSEL_TEMA}=${tema}; Path=/; Max-Age=${60 * 60 * 24 * 365}; ${tvers}`
  if (tema === "system") document.documentElement.removeAttribute("data-theme")
  else document.documentElement.setAttribute("data-theme", tema)
  merkLenkene(tema)
}

/**
 * Lenkene til de to andre utgavene bærer valget ditt videre.
 *
 * Serveren skriver dem med `?tema=` ved sidelasting, og det holder helt til
 * du bytter tema etterpå: da står lenkene igjen med det gamle valget, og du
 * fikk systemtemaet i den andre utgaven. Kapselen gjelder bare for sitt eget
 * domene i drift, så lenka er det eneste som kan bære valget over.
 */
export function merkLenkene(tema: Tema): void {
  for (const lenke of document.querySelectorAll<HTMLAnchorElement>(
    ".utgavevelger__lenke, .velkomst__lenke",
  )) {
    const adresse = new URL(lenke.href)
    if (tema === "system") adresse.searchParams.delete("tema")
    else adresse.searchParams.set("tema", tema)
    lenke.href = adresse.toString()
  }
}
