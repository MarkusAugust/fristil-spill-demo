import type { APIRoute } from "astro"

import { KAPSEL } from "../lib/fristil"
import { pulsen, tilstand } from "../lib/spilltjener"

export const prerender = false

/**
 * Øyas eneste forbindelse til serveren.
 *
 * Her går det **verken HTML eller hele tilstanden**, men et lite JSON-objekt:
 * hvilken runde og fase vi er i, tavla, og om du har svart. Er runden en
 * annen enn den siden ble tegnet med, henter øya en ny side. Det er hele
 * forskjellen fra de to andre appene: siden er serverens, og øya passer bare
 * på når den er utdatert.
 */
export const GET: APIRoute = ({ cookies }) => {
  const spillerId = cookies.get(KAPSEL)?.value ?? null
  const koder = new TextEncoder()
  let avslutt: (() => void) | null = null

  const strom = new ReadableStream({
    start(styring) {
      let levende = true

      const send = (hendelse: string, data: string) => {
        if (!levende) return
        styring.enqueue(koder.encode(`event: ${hendelse}\ndata: ${data}\n\n`))
      }

      const sendPuls = async () => {
        try {
          const t = await tilstand(spillerId)
          send(
            "puls",
            JSON.stringify({
              fase: t.fase,
              rundeNr: t.rundeNr,
              sakId: t.sak.id,
              fristMs: t.fristMs,
              naMs: t.naMs,
              harSvart: t.meg?.harSvart ?? false,
              medPaSaken: t.medPaSaken,
              svartAv: t.harSvart,
              tavle: t.tavle,
            }),
          )
        } catch {
          // Svarer ikke spilltjeneren, skal øya si fra. En side som står
          // stille ser helt normal ut, og det er verre enn en feilmelding.
          send("samband", "nede")
        }
      }

      void sendPuls()
      const slutt = pulsen(() => {
        void sendPuls()
      })

      avslutt = () => {
        levende = false
        slutt()
      }
    },
    cancel() {
      avslutt?.()
    },
  })

  return new Response(strom, {
    headers: {
      "content-type": "text/event-stream",
      "cache-control": "no-cache",
      connection: "keep-alive",
    },
  })
}
