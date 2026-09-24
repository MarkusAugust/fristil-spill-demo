import { readFileSync } from "node:fs"

import type { APIRoute } from "astro"

export const prerender = false

/**
 * Avlyttingen av ledningen, som må kjøre før alt annet på siden.
 *
 * Egen fil og ikke en del av `panel.js`, fordi rekkefølgen avgjør: øya her
 * åpner hendelsesstrømmen i det modulen kjører, og alle modulskript kjører
 * etter at dokumentet er parset. Panelet la seg derfor utenpå `EventSource`
 * for sent, og «Med hva» viste aldri det tavla faktisk oppdaterte seg av.
 */
const PANEL: string = readFileSync(
  process.env.PANEL_AVLYTT_JS_FIL ?? "../../felles/panel-avlytt.js",
  "utf8",
)

export const GET: APIRoute = () =>
  new Response(PANEL, { headers: { "content-type": "text/javascript" } })
