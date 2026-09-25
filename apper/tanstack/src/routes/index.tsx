import { createFileRoute } from "@tanstack/react-router"
import { createServerFn } from "@tanstack/react-start"
import {
  getCookie,
  getRequestHeader,
  setResponseHeader,
} from "@tanstack/react-start/server"

import { Skjerm } from "../brett"
import { Route as rotRuta } from "./__root"
import { KOMMUNER } from "../kommuner"
import { tilstand } from "../spilltjener"
import type { Skjermbilde } from "../tilstand"

/** Navnet på kapselen som sier hvem som sitter der. */
export const KAPSEL = "spiller"

/**
 * Første skjermbilde, hentet på serveren.
 *
 * Etter dette kommer alt gjennom hendelsesstrømmen. Denne finnes fordi siden
 * skal være ferdig tegnet når den kommer fram, også før noe JavaScript har
 * kjørt.
 */
/**
 * Om siden står i en ramme, altså i skallet.
 *
 * `Sec-Fetch-Dest: iframe` sendes av Chromium, Firefox og WebKit, men bare
 * til en adresse nettleseren regner som sikker: https, `localhost`,
 * `127.0.0.1` og `::1`. Drift er https og `./kjor.sh` er localhost, så begge
 * de vanlige veiene virker. Åpnes skallet over vanlig http mot en maskin på
 * nettet, kommer hilsenen tilbake i rammene. Det er en dårligere visning, og
 * ikke en ødelagt side.
 *
 * Verdien er bare meningsfull på selve dokumentforespørselen. Kjøres loaderen
 * senere over RPC, er `Sec-Fetch-Dest` `empty`, og svaret ville vært usant.
 *
 * `Vary` fordi svaret avhenger av en overskrift, og `Cache-Control` fordi det
 * også avhenger av kapsler: uten den kunne en mellomtjener gitt én spillers
 * side til en annen.
 */
function iRamme(): boolean {
  setResponseHeader("Vary", "Sec-Fetch-Dest, Cookie")
  setResponseHeader("Cache-Control", "private, no-cache")
  return getRequestHeader("sec-fetch-dest") === "iframe"
}

const hentSkjermbilde = createServerFn({ method: "GET" }).handler(
  async (): Promise<
    Skjermbilde & { spillerId: string | null; iRamme: boolean }
  > => {
    const spillerId = getCookie(KAPSEL) ?? null
    return {
      // En dokumentlasting: her sier appen fra hvilken utgave spilleren
      // sitter i. Strømmen gjør det ikke, se `tilstand` i spilltjener.ts.
      tilstand: await tilstand(spillerId, true),
      kommuner: KOMMUNER,
      feil: [],
      // Id-en følger med ned, fordi lenkene til de to andre utgavene bærer
      // den videre. Uten den mister du navnet ditt i det du bytter.
      spillerId,
      /*
       * Nettleseren sier selv at dette er en ramme.
       *
       * `Sec-Fetch-Dest: iframe` sendes av Chromium, Firefox og WebKit, og
       * er testet mot alle tre. Skallet trenger dermed ingen egen adresse,
       * og rammen er fortsatt nøyaktig den siden du kan åpne alene.
       */
      iRamme: iRamme(),
    }
  },
)

export const Route = createFileRoute("/")({
  loader: () => hentSkjermbilde(),
  component: Side,
  errorComponent: Venteside,
})

function Side() {
  // Temaet kommer fra rotruta, som leser kapselen på serveren.
  const data = Route.useLoaderData()
  return (
    <Skjerm
      forste={data}
      tema={rotRuta.useLoaderData()}
      spillerId={data.spillerId}
      iRamme={data.iRamme}
    />
  )
}

/**
 * Når spilltjeneren ikke svarer.
 *
 * Den starter et sekund eller to etter appene, og en rå feilmelding sier
 * ingenting til den som står og venter. Siden laster seg selv på nytt til
 * etaten er tilbake.
 */
function Venteside() {
  return (
    <main className="venteside">
      <h1>Etaten åpner straks</h1>
      <p>Saksbehandlingssystemet starter opp. Siden oppdaterer seg selv.</p>
      <script
        // biome-ignore lint/security/noDangerouslySetInnerHtml: siden skal
        // laste seg selv på nytt uten at React er i drift.
        dangerouslySetInnerHTML={{ __html: "setTimeout(function(){location.reload()},2000)" }}
      />
    </main>
  )
}
