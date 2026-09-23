import type { Tilstand } from "./tilstand"

/**
 * Klienten mot spilltjeneren.
 *
 * Appserveren snakker med spilltjeneren, aldri nettleseren. Det er slik
 * TanStack, Datastar og Astro alle er ment å virke, det holder spilltjeneren
 * intern, og det gjør at den eneste forskjellen mellom de tre appene er hva
 * som går fra appserveren og ned til nettleseren.
 */
const ADRESSE = process.env.SPILLTJENER ?? "http://127.0.0.1:8080"

export async function tilstand(spillerId: string | null): Promise<Tilstand> {
  const url = new URL(`${ADRESSE}/api/tilstand`)
  if (spillerId) url.searchParams.set("spiller", spillerId)
  const svar = await fetch(url)
  if (!svar.ok) throw new Error(`Spilltjeneren svarte ${svar.status}`)
  return (await svar.json()) as Tilstand
}

export async function bliMed(navn: string): Promise<{ spillerId: string; navn: string }> {
  const svar = await fetch(`${ADRESSE}/api/bli-med`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ navn, stack: "tanstack" }),
  })
  if (!svar.ok) throw new Error(`Spilltjeneren svarte ${svar.status}`)
  return (await svar.json()) as { spillerId: string; navn: string }
}

export async function svarInn(
  spillerId: string,
  svar: { vedtak: string | null; hjemmel: string | null; kommune: string | null; felle: string | null },
): Promise<void> {
  await fetch(`${ADRESSE}/api/svar`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ spillerId, svar }),
  })
}

/**
 * Lytter på spilltjeneren, og kobler til igjen om forbindelsen ryker.
 *
 * Appen holder **én** forbindelse oppstrøms, uansett hvor mange som spiller
 * her. Selve tilstanden er delvis personlig, «dine poeng, din plass», så
 * hver nettleser får sin egen etterpå. Strømmen sier altså *når* noe skjedde,
 * ikke *hva* hver enkelt skal se.
 */
type Lytter = () => void

const lyttere = new Set<Lytter>()
let lytterStartet = false

export function pulsen(lytter: Lytter): () => void {
  lyttere.add(lytter)
  startLytting()
  return () => lyttere.delete(lytter)
}

function startLytting() {
  if (lytterStartet) return
  lytterStartet = true
  void (async () => {
    for (;;) {
      try {
        const svar = await fetch(`${ADRESSE}/api/hendelser`)
        const leser = svar.body?.getReader()
        if (!leser) throw new Error("Ingen strøm")
        const dekoder = new TextDecoder()
        let rest = ""
        for (;;) {
          const { done, value } = await leser.read()
          if (done) break
          rest += dekoder.decode(value, { stream: true })
          const linjer = rest.split("\n")
          rest = linjer.pop() ?? ""
          for (const linje of linjer) {
            if (linje.startsWith("data:")) for (const l of lyttere) l()
          }
        }
      } catch (e) {
        console.log(`Mistet spilltjeneren (${(e as Error).message}). Prøver igjen om to sekunder.`)
      }
      // Et pulsslag også når forbindelsen ryker: da får hver åpen strøm
      // prøvd å hente tilstanden, kallet feiler, og nettleseren får beskjed.
      for (const l of lyttere) l()
      await new Promise((f) => setTimeout(f, 2000))
    }
  })()
}
