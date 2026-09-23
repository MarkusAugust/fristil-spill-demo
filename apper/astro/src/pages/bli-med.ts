import type { APIRoute } from "astro"

import { KAPSEL } from "../lib/fristil"
import { bliMed, erProve } from "../lib/spilltjener"

export const prerender = false

/**
 * Skjemaet for å bli med.
 *
 * En vanlig POST med en omdirigering tilbake, altså mønsteret som har virket
 * siden før JavaScript. Kapselen må stå før hendelsesstrømmen åpnes, ellers
 * vet serveren ikke hvem som sitter der.
 */
export const POST: APIRoute = async ({ request, cookies, redirect, url }) => {
  const skjema = await request.formData()
  const navn = String(skjema.get("navn") ?? "")

  /*
   * `SameSite=None` i drift, `Lax` lokalt.
   *
   * Skallet viser de tre utgavene i hver sin ramme, og i drift ligger de på
   * hvert sitt domene. Da er kapselen en tredjepartskapsel, og `Lax` gjør at
   * nettleseren aldri sender den: du kunne se spillet i rammen, men ikke
   * melde deg på. `None` krever `Secure`, altså https, og det har vi bare i
   * drift. Lokalt er alle tre på `localhost`, som er samme nettsted uansett
   * portnummer.
   */
  const https = url.protocol === "https:"

  try {
    const spiller = await bliMed(navn, erProve(request))
    cookies.set(KAPSEL, spiller.spillerId, {
      path: "/",
      maxAge: 8 * 60 * 60,
      httpOnly: true,
      sameSite: https ? "none" : "lax",
      secure: https,
    })
  } catch {
    // Spilltjeneren er nede. Forsiden sier fra om det, så vi sender deg dit.
  }

  return redirect("/", 303)
}
