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
  // Hvilken utgave spilleren sitter i nå. Uten denne sto stacken på tavla og
  // i den evige topplista stille når noen byttet utgave underveis.
  url.searchParams.set("stack", "astro")
  const svar = await fetch(url)
  if (!svar.ok) throw new Error(`Spilltjeneren svarte ${svar.status}`)
  return (await svar.json()) as Tilstand
}

/**
 * Overskriften paritetstesten melder seg på med.
 *
 * Testen setter den på hver forespørsel fra nettleseren sin, og appserveren
 * sender den videre til spilltjeneren. Poengene havner da aldri i den evige
 * topplista, som er det eneste som overlever en omstart.
 */
export const TEST_OVERSKRIFT = "x-fristil-test"

/** Om forespørselen kommer fra paritetstesten. */
export function erTest(request: Request): boolean {
  return request.headers.get(TEST_OVERSKRIFT) === "1"
}

export async function bliMed(
  navn: string,
  erTest = false,
): Promise<{ spillerId: string; navn: string }> {
  const svar = await fetch(`${ADRESSE}/api/bli-med`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ navn, stack: "astro", erTest }),
  })
  if (!svar.ok) throw new Error(`Spilltjeneren svarte ${svar.status}`)
  return (await svar.json()) as { spillerId: string; navn: string }
}

/**
 * Melder en spiller av vakta.
 *
 * En ekte spiller trenger den ikke: lukker du fanen, er du borte etter tre
 * runder uten svar. Den finnes fordi paritetstesten melder på en spiller i
 * hver utgave, og en test som lar tre «Test …» stå igjen på tavla skitner
 * til det den skal teste.
 */
export async function gaAv(spillerId: string): Promise<void> {
  await fetch(`${ADRESSE}/api/ga-av`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ spillerId }),
  })
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

/**
 * Hvor lenge strømmen står åpen etter at den siste nettleseren er borte.
 *
 * Ikke null: en oppfriskning av siden er to hendelser, en avmelding og en
 * påmelding, med et lite øyeblikk imellom. Uten pusterommet ville appen
 * lukket og åpnet oppstrømsforbindelsen ved hver eneste oppfriskning.
 */
const PUSTEROM_MS = 60_000

let stopp: AbortController | null = null
let nedtelling: ReturnType<typeof setTimeout> | null = null

/**
 * Lytter på spilltjeneren, og kobler til igjen om forbindelsen ryker.
 *
 * Appen holder **én** forbindelse oppstrøms, uansett hvor mange som spiller
 * her, og den åpnes først når noen faktisk ser på. Det siste er ikke bare
 * ryddighet: Railway lar en tjeneste sove når den ikke har sendt utgående
 * trafikk på fem til ti minutter, og en strøm som står åpen døgnet rundt er
 * nettopp slik trafikk. Med dette sovner både appen og spilltjeneren når
 * ingen spiller, og våkner på første forespørsel.
 *
 * Selve tilstanden er delvis personlig, «dine poeng, din plass», så hver
 * nettleser henter sin egen etterpå. Strømmen sier altså *når* noe skjedde,
 * ikke *hva* hver enkelt skal se.
 */
export function pulsen(lytter: Lytter): () => void {
  lyttere.add(lytter)
  if (nedtelling) {
    clearTimeout(nedtelling)
    nedtelling = null
  }
  startLytting()

  return () => {
    lyttere.delete(lytter)
    if (lyttere.size > 0 || nedtelling) return
    nedtelling = setTimeout(() => {
      nedtelling = null
      if (lyttere.size === 0) {
        stopp?.abort()
        stopp = null
      }
    }, PUSTEROM_MS)
  }
}

function startLytting() {
  if (stopp) return
  const min = new AbortController()
  stopp = min

  void (async () => {
    while (!min.signal.aborted) {
      try {
        const svar = await fetch(`${ADRESSE}/api/hendelser`, { signal: min.signal })
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
        if (min.signal.aborted) break
        console.log(`Mistet spilltjeneren (${(e as Error).message}). Prøver igjen om to sekunder.`)
      }
      if (min.signal.aborted) break
      // Et pulsslag også når forbindelsen ryker: da får hver åpen strøm
      // prøvd å hente tilstanden, kallet feiler, og nettleseren får beskjed.
      for (const l of lyttere) l()
      await new Promise((f) => setTimeout(f, 2000))
    }
  })()
}
