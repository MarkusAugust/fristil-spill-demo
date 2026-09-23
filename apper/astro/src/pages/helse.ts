import type { APIRoute } from "astro"

export const prerender = false

/** Samme svar som de andre tjenestene gir, til oppstart og til Railway. */
export const GET: APIRoute = () => new Response("ok")
