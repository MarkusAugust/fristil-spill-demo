import { readFileSync } from "node:fs"

import type { APIRoute } from "astro"

export const prerender = false

/**
 * Temaet, generert av Fristil av `felles/fristil.tema.json`.
 *
 * Det setter skrift og form, og lar fargene stå: Fristils egen palett er
 * allerede Skatteetatens, med de samme verdiene. Fila ligger i `felles/`,
 * som stilarket, fordi de tre utgavene skal ha det samme temaet.
 */
const TEMA_CSS: string = readFileSync(
  process.env.TEMA_CSS_FIL ?? "../../felles/tema.css",
  "utf8",
)

export const GET: APIRoute = () =>
  new Response(TEMA_CSS, { headers: { "content-type": "text/css" } })
