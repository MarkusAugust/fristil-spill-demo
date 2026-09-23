import type { APIRoute } from "astro"

import { KAPSEL } from "../lib/fristil"
import { gaAv } from "../lib/spilltjener"

export const prerender = false

/**
 * Går av vakt, og blir borte fra tavla med en gang.
 *
 * Ingen knapp peker hit. Paritetsprøven bruker den for å rydde etter seg, og
 * det er det samme kallet en «gå av vakt»-knapp ville gjort.
 */
export const POST: APIRoute = async ({ cookies }) => {
  const spillerId = cookies.get(KAPSEL)?.value
  if (spillerId) await gaAv(spillerId).catch(() => {})
  cookies.delete(KAPSEL, { path: "/" })
  return new Response("ok")
}
