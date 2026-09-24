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
    /* Temaet ligger i sitt eget lag, `fristil-tema`, erklært etter
       `fristil`. Det avgjør rekkefølgen uansett når stilarkene lastes, og det
       er nødvendig her: Vite legger Fristils CSS inn etter lenkene våre. */
    links: [
      { rel: "stylesheet", href: "/tema.css" },
      { rel: "stylesheet", href: "/brett.css" },
    ],
    /*
     * Avlyttingen panelet leser fra.
     *
     * `scripts` i `head()` rendres av `<Scripts />`, som står i `<body>`, og
     * ikke i hodet. Det virker likevel, og det er verdt å vite hvorfor: et
     * vanlig skript uten `defer` er parserblokkerende uansett hvor det står,
     * så det kjører før hvert modulskript. Gjør noen det om til en modul,
     * ryker rekkefølgen stille.
     *
     * Her er den ikke strengt nødvendig uansett: React åpner strømmen fra en
     * effekt, altså etter hydreringen. I de to andre appene var den det, og
     * et panel som virker av tilfeldige grunner i én av tre utgaver er ikke
     * et panel man kan tro på.
     */
    scripts: [{ src: "/panel-avlytt.js" }],
  }),
  component: Skall,
})

/** Ruta panelmodulen ligger på. Se kommentaren over effekten. */
const PANELMODUL = "/panel.js"

const PANELTEKST = {
  teknikk: "Serveren sendte JSON, og React tegnet om komponenten.",
  forklaring:
    "Hendelsesstrømmen bærer tilstanden som JSON. React sammenligner og bytter ut nøyaktig de nodene som ble annerledes, uten at siden lastes på nytt.",
}

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

  /*
   * Panelet startes etter hydreringen, og verten rendres av React.
   *
   * Første utgave var en skripttagg i markupen, som i de to andre appene.
   * Panelet sto der ved innlasting og var borte i det brukeren meldte seg på:
   * `document.body` er en del av komponenttreet, og en strukturell rendring
   * kaster bort noder React ikke vet om. Nå eier React boksen, og modulen
   * eier innholdet.
   *
   * Adressen står i en variabel, og ikke som en streng i `import()`. Vite
   * leser et bokstavelig kall og prøver å slå opp fila under bygget, og
   * `/panel.js` er en rute på serveren og ingen fil på disk. Da feilet hele
   * dev-serveren med «Failed to resolve import». En variabel kan ikke leses
   * statisk, og `@vite-ignore` sier i tillegg fra.
   */
  useEffect(() => {
    import(/* @vite-ignore */ PANELMODUL).then(({ startPanel }) =>
      (startPanel as (tekst: typeof PANELTEKST) => void)(PANELTEKST),
    )
  }, [])

  return (
    <html lang="nb" data-theme={tema === "system" ? undefined : tema}>
      <head>
        <HeadContent />
      </head>
      <body>
        <Outlet />
        <Scripts />
        {/* Verten til panelet. React rendrer den tomme boksen, `panel.js`
            fyller den ut. Se kommentaren over effekten. */}
        <div className="panelvert" />
      </body>
    </html>
  )
}
