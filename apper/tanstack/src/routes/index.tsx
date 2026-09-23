import { createFileRoute } from "@tanstack/react-router"
import { createServerFn } from "@tanstack/react-start"
import { getCookie } from "@tanstack/react-start/server"

import { Skjerm } from "../brett"
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
const hentSkjermbilde = createServerFn({ method: "GET" }).handler(
  async (): Promise<Skjermbilde> => {
    const spillerId = getCookie(KAPSEL) ?? null
    return { tilstand: await tilstand(spillerId), kommuner: KOMMUNER, feil: [] }
  },
)

export const Route = createFileRoute("/")({
  loader: () => hentSkjermbilde(),
  component: Side,
  errorComponent: Venteside,
})

function Side() {
  return <Skjerm forste={Route.useLoaderData()} />
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
