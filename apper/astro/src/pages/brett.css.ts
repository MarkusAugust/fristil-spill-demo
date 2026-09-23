import { readFileSync } from "node:fs"

import type { APIRoute } from "astro"

export const prerender = false

/**
 * Stilarket alle tre appene serverer.
 *
 * Fila ligger i `felles/`, utenfor appene, fordi den er det som gjør at de
 * tre skjermene ser like ut. Den leses én gang og blir stående i minnet.
 */
const BRETT_CSS: string = readFileSync(
  process.env.BRETT_CSS_FIL ?? "../../felles/brett.css",
  "utf8",
)

export const GET: APIRoute = () =>
  new Response(BRETT_CSS, { headers: { "content-type": "text/css" } })
