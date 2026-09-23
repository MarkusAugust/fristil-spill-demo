/**
 * Formen spilltjeneren sender.
 *
 * Skrevet av her, ikke delt som kode med de andre appene. Appene er klienter
 * av et JSON-API, og kontrakten er JSON-en, ikke en felles modul. Da kan de
 * tre skrives i hver sin verden uten å dra hverandre med seg, som er hele
 * poenget med demoen.
 */

export type Soker = {
  navn: string
  fodselsdato: string
  kommune: string
  epost: string
}

export type Hjemmel = { kode: string; tekst: string }

export type SakUt = {
  id: string
  tittel: string
  sammendrag: string
  tekst: string
  soker: Soker
}

export type Fasit = { vedtak: string; hjemmel: string; felle: string | null }

export type Vurdering = {
  vedtakRiktig: boolean
  hjemmelRiktig: boolean
  kommuneRiktig: boolean
  felleRiktig: boolean
  poeng: number
}

export type TavleRad = {
  plass: number
  navn: string
  poeng: number
  sistePoeng: number
  harSvart: boolean
  stack: string
  erMeg: boolean
}

export type Svar = {
  vedtak: string | null
  hjemmel: string | null
  kommune: string | null
  felle: string | null
}

export type MegUt = {
  navn: string
  poeng: number
  sistePoeng: number
  plass: number
  forrigePlass: number
  harSvart: boolean
  medPaSaken: boolean
  runderUtenSvar: number
  svar: Svar | null
  vurdering: Vurdering | null
}

export type ToppEntry = { navn: string; poeng: number; stack: string; nar: number }

export type Poeng = {
  vedtak: number
  hjemmel: number
  kommune: number
  felle: number
  fullPott: number
}

export type Tilstand = {
  fase: "runde" | "oppgjor" | "slutt"
  rundeNr: number
  runderTotalt: number
  fristMs: number
  faseLengdeMs: number
  rundeLengdeMs: number
  grenseUtenSvar: number
  poeng: Poeng
  naMs: number
  sak: SakUt
  hjemler: Hjemmel[]
  fasit: Fasit | null
  fasitKommune: string | null
  forklaring: string | null
  tavle: TavleRad[]
  harSvart: number
  medPaSaken: number
  meg: MegUt | null
  evigToppliste: ToppEntry[]
}

/** Feilene i skjemaet, funnet på serveren. */
export type Feil = { felt: string; melding: string }

/** Det serveren sender ned ved hver oppdatering. */
export type Skjermbilde = {
  tilstand: Tilstand
  kommuner: string[]
  feil: Feil[]
}
