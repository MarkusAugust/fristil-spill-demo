import { readFileSync } from "node:fs"

import { createStartHandler, defaultStreamHandler } from "@tanstack/react-start/server"

import { KOMMUNER } from "./kommuner"
import { bliMed, pulsen, svarInn, tilstand } from "./spilltjener"
import type { Skjermbilde } from "./tilstand"
import { valider } from "./validering"

/**
 * Appserveren.
 *
 * TanStack Start tegner siden, og denne fila legger fire endepunkter ved
 * siden av: stilarket appene deler, skjemaet for å bli med, innsendingen av
 * et vedtak, og hendelsesstrømmen. De er skrevet som vanlige svar på en
 * `Request` framfor som serverfunksjoner, fordi tre av dem ikke er kall fra
 * React: `/brett.css` er en fil, `/bli-med` er et vanlig skjema som skal
 * virke uten JavaScript, og `/hendelser` er en strøm som står åpen.
 */
const start = createStartHandler(defaultStreamHandler)

/** Navnet på kapselen som sier hvem som sitter der. */
const KAPSEL = "spiller"

/** Og den som bærer fargetemaet. */
const KAPSEL_TEMA = "forstelinja-tema"

/** Stilarket alle tre appene serverer, lest fra `felles/`. */
const BRETT_CSS: string = readFileSync(
  process.env.BRETT_CSS_FIL ?? "../../felles/brett.css",
  "utf8",
)

/**
 * Hvordan kapselen skal merkes.
 *
 * `SameSite=Lax` er riktig når appen står alene, men skallet viser de tre
 * utgavene i hver sin ramme, og i drift ligger de på hvert sitt domene. Da
 * er kapselen en tredjepartskapsel, og `Lax` gjør at nettleseren aldri
 * sender den: du kunne se spillet i rammen, men ikke melde deg på.
 * `None` krever `Secure`, altså https, og det har vi bare i drift. Lokalt
 * er alle tre på `localhost`, som er samme nettsted uansett portnummer, og
 * der holder `Lax`.
 */
function kapselTvers(request: Request): string {
  const https =
    request.headers.get("x-forwarded-proto") === "https" ||
    new URL(request.url).protocol === "https:"
  return https ? "SameSite=None; Secure" : "SameSite=Lax"
}

function kapsel(request: Request, navn: string): string | null {
  const rad = request.headers.get("cookie")
  if (!rad) return null
  for (const del of rad.split(";")) {
    const [n, ...resten] = del.trim().split("=")
    if (n === navn) return decodeURIComponent(resten.join("="))
  }
  return null
}

async function skjermbilde(spillerId: string | null, feil: Skjermbilde["feil"] = []) {
  return { tilstand: await tilstand(spillerId), kommuner: KOMMUNER, feil } satisfies Skjermbilde
}

/**
 * Strømmen ned til nettleseren.
 *
 * Her går det **JSON**, ikke HTML. Det er hele forskjellen fra
 * Datastar-utgaven: der har serveren alt tegnet skjermen, her sier den bare
 * at noe har skjedd og sender tilstanden, og React setter sammen DOM-en.
 *
 * Svarer ikke spilltjeneren, sendes det en beskjed om det i stedet for at
 * strømmen bare blir stille. En side som har mistet forbindelsen ser helt
 * normal ut, og det er verre enn en feilmelding.
 */
function hendelser(spillerId: string | null): Response {
  const koder = new TextEncoder()
  let avslutt: (() => void) | null = null

  const strom = new ReadableStream({
    start(styring) {
      let levende = true

      const send = (hendelse: string, data: string) => {
        if (!levende) return
        styring.enqueue(koder.encode(`event: ${hendelse}\ndata: ${data}\n\n`))
      }

      const sendTilstand = async () => {
        try {
          send("tilstand", JSON.stringify(await skjermbilde(spillerId)))
        } catch {
          send("samband", "nede")
        }
      }

      void sendTilstand()
      const slutt = pulsen(() => {
        void sendTilstand()
      })

      // Lukker nettleseren fanen, skal abonnementet bort. Uten dette samler
      // serveren opp lyttere som skriver til en strøm ingen leser.
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

export default {
  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url)

    if (url.pathname === "/helse") return new Response("ok")

    /*
     * Billetten fra en annen utgave.
     *
     * I drift ligger de tre på hvert sitt domene, og en kapsel gjelder bare
     * for sitt eget. Bytter du utgave, kommer du hit med `?spiller=` i
     * adressen, og her veksles den inn i vår egen kapsel. Så en
     * omdirigering, slik at billetten ikke blir stående i adressefeltet og i
     * historikken.
     */
    const billett = url.pathname === "/" ? url.searchParams.get("spiller") : null
    if (billett) {
      const tema = url.searchParams.get("tema")
      const kapsler = [
        `${KAPSEL}=${billett}; Path=/; Max-Age=${8 * 60 * 60}; HttpOnly; ${kapselTvers(request)}`,
      ]
      if (tema === "light" || tema === "dark") {
        kapsler.push(
          `${KAPSEL_TEMA}=${tema}; Path=/; Max-Age=${60 * 60 * 24 * 365}; ${kapselTvers(request)}`,
        )
      }
      const hoder = new Headers({ location: "/" })
      for (const k of kapsler) hoder.append("set-cookie", k)
      return new Response(null, { status: 303, headers: hoder })
    }

    if (url.pathname === "/brett.css") {
      return new Response(BRETT_CSS, { headers: { "content-type": "text/css" } })
    }

    if (url.pathname === "/hendelser") {
      return hendelser(kapsel(request, KAPSEL))
    }

    if (url.pathname === "/bli-med" && request.method === "POST") {
      const skjema = await request.formData()
      const navn = String(skjema.get("navn") ?? "")
      let spiller: { spillerId: string }
      try {
        spiller = await bliMed(navn)
      } catch {
        return new Response(null, { status: 303, headers: { location: "/" } })
      }

      // Kapsel og ikke minne i nettleseren: Astro-appen laster hele sider på
      // nytt, og alle tre skal oppføre seg likt.
      return new Response(null, {
        status: 303,
        headers: {
          location: "/",
          "set-cookie": `${KAPSEL}=${spiller.spillerId}; Path=/; Max-Age=${8 * 60 * 60}; HttpOnly; ${kapselTvers(request)}`,
        },
      })
    }

    if (url.pathname === "/svar" && request.method === "POST") {
      const spillerId = kapsel(request, KAPSEL)
      const inn = (await request.json()) as Record<string, string>

      const vedtak = inn.vedtak?.trim() || null
      const hjemmel = inn.hjemmel?.trim() || null
      const kommune = inn.kommune?.trim() || null
      // «nei» er et svar: saken er i orden. Spilltjeneren kjenner bare
      // feltnavn og `null`, så valget oversettes her.
      const felleValg = inn.felle?.trim() || null
      const felle = felleValg === "nei" ? null : felleValg

      const feil = valider(vedtak, hjemmel, kommune, felleValg, KOMMUNER)
      if (feil.length === 0 && spillerId) {
        await svarInn(spillerId, { vedtak, hjemmel, kommune, felle })
      }

      return Response.json(await skjermbilde(spillerId, feil))
    }

    return start(request)
  },
}
