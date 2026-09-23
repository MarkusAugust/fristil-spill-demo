import {
  defineFsConnectionStatus,
  defineFsDialog,
  defineFsErrorSummary,
  defineFsField,
  defineFsPopover,
  defineFsSuggestion,
  defineFsTabs,
} from "@fristil/designsystem"
import { HeadContent, Outlet, Scripts, createRootRoute } from "@tanstack/react-router"
import { createServerFn } from "@tanstack/react-start"
import { getCookie } from "@tanstack/react-start/server"
import { useEffect } from "react"

import "../stil"
import { KAPSEL_TEMA, type Tema, erTema } from "../tema"

/**
 * Temaet, hentet på serveren.
 *
 * Valget ligger i en kapsel og ikke i `localStorage`, og det er ikke en
 * smakssak her. `<html>` rendres av React, så et attributt et skript setter
 * før hydreringen er et avvik React melder fra om ved hvert eneste oppslag.
 * Serveren må altså vite temaet for å kunne skrive det selv. Det fjerner
 * samtidig lysglimtet: siden kommer ferdig i riktig tema.
 */
const hentTema = createServerFn({ method: "GET" }).handler(async (): Promise<Tema> => {
  const valgt = getCookie(KAPSEL_TEMA)
  return erTema(valgt) ? valgt : "system"
})

/**
 * Sideskallet.
 *
 * Fristils stilark importeres, ikke lenkes: her er det en bunter, og da er
 * det den som skal sette dem sammen. `brett.css` er derimot lenket, fordi
 * det er den samme fila alle tre appene serverer, og den ligger utenfor
 * denne appen.
 */
export const Route = createRootRoute({
  loader: () => hentTema(),
  head: () => ({
    meta: [
      { charSet: "utf-8" },
      { name: "viewport", content: "width=device-width, initial-scale=1" },
      { title: "Førstelinja · TanStack Start og React" },
    ],
    links: [{ rel: "stylesheet", href: "/brett.css" }],
  }),
  component: Skall,
})

function Skall() {
  const tema = Route.useLoaderData()
  /*
   * Web-komponentene registreres i nettleseren, etter at HTML-en står der.
   *
   * Én kalle per komponent, og ikke en samlefunksjon: registreringen er det
   * eneste som drar koden inn i bunten, så en app som ikke bruker
   * forslagsfeltet skal heller ikke sende det.
   *
   * Importen er statisk, ikke dynamisk. Den kan være det: modulene i Fristil
   * kan lastes på en server, og `defineFs*` gjør ingenting uten
   * `customElements`. Med en dynamisk import var det et lite gap der siden
   * sto med elementene, men uten oppførsel, og i drift rakk første melding
   * fra hendelsesstrømmen å komme inn i det gapet: «reportSuccess is not a
   * function». Lokalt var filene der med en gang, så det viste seg aldri.
   */
  useEffect(() => {
    defineFsField()
    defineFsTabs()
    defineFsPopover()
    defineFsSuggestion()
    defineFsErrorSummary()
    defineFsDialog()
    defineFsConnectionStatus()
  }, [])

  return (
    <html lang="nb" data-theme={tema === "system" ? undefined : tema}>
      <head>
        <HeadContent />
      </head>
      <body>
        <Outlet />
        <Scripts />
      </body>
    </html>
  )
}
