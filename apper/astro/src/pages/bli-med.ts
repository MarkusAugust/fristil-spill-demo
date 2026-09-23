import type { APIRoute } from "astro"

import { KAPSEL } from "../lib/fristil"
import { bliMed } from "../lib/spilltjener"

export const prerender = false

/**
 * Skjemaet for å bli med.
 *
 * En vanlig POST med en omdirigering tilbake, altså mønsteret som har virket
 * siden før JavaScript. Kapselen må stå før hendelsesstrømmen åpnes, ellers
 * vet serveren ikke hvem som sitter der.
 */
export const POST: APIRoute = async ({ request, cookies, redirect }) => {
  const skjema = await request.formData()
  const navn = String(skjema.get("navn") ?? "")

  try {
    const spiller = await bliMed(navn)
    cookies.set(KAPSEL, spiller.spillerId, {
      path: "/",
      maxAge: 8 * 60 * 60,
      httpOnly: true,
      sameSite: "lax",
    })
  } catch {
    // Spilltjeneren er nede. Forsiden sier fra om det, så vi sender deg dit.
  }

  return redirect("/", 303)
}
