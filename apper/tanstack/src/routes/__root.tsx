import { HeadContent, Outlet, Scripts, createRootRoute } from "@tanstack/react-router"
import { useEffect } from "react"

import "../stil"

/**
 * Sideskallet.
 *
 * Fristils stilark importeres, ikke lenkes: her er det en bunter, og da er
 * det den som skal sette dem sammen. `brett.css` er derimot lenket, fordi
 * det er den samme fila alle tre appene serverer, og den ligger utenfor
 * denne appen.
 */
export const Route = createRootRoute({
  head: () => ({
    meta: [
      { charSet: "utf-8" },
      { name: "viewport", content: "width=device-width, initial-scale=1" },
      { title: "Førstelinja · TanStack Start og React" },
    ],
    links: [{ rel: "stylesheet", href: "/brett.css" }],
    scripts: [
      {
        // Temaet settes før siden tegnes, ellers blinker den lyst for den
        // som har valgt mørkt. Samme skript som i Datastar-appen, og det er
        // med vilje: valget skal overleve at du bytter utgave.
        children: `try{var v=localStorage.getItem("forstelinja-tema");if(v==="light"||v==="dark"){document.documentElement.setAttribute("data-theme",v)}}catch(e){}`,
      },
    ],
  }),
  component: Skall,
})

function Skall() {
  /*
   * Web-komponentene registreres i nettleseren, etter at HTML-en står der.
   *
   * Én kalle per komponent, og ikke en samlefunksjon: registreringen er det
   * eneste som drar koden inn i bunten, så en app som ikke bruker
   * forslagsfeltet skal heller ikke sende det. Importen er dynamisk fordi
   * `customElements` ikke finnes på serveren.
   */
  useEffect(() => {
    void import("@fristil/designsystem").then((fristil) => {
      fristil.defineFsField()
      fristil.defineFsTabs()
      fristil.defineFsPopover()
      fristil.defineFsSuggestion()
      fristil.defineFsErrorSummary()
      fristil.defineFsDialog()
      fristil.defineFsConnectionStatus()
    })
  }, [])

  return (
    <html lang="nb">
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
