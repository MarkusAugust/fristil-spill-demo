import { readFileSync } from "node:fs"

import type { APIRoute } from "astro"

export const prerender = false

/**
 * Panelet alle tre appene serverer.
 *
 * Samme fil, samme grunn som `brett.css`: panelet skal være det samme i de tre
 * utgavene, ellers viser det forskjeller som er panelets egne framfor appenes.
 */
const PANEL: string = readFileSync(
  process.env.PANEL_JS_FIL ?? "../../felles/panel.js",
  "utf8",
)

export const GET: APIRoute = () =>
  new Response(PANEL, { headers: { "content-type": "text/javascript" } })
