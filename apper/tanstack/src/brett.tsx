import { fs } from "@fristil/designsystem/react"
import { useEffect, useId, useRef, useState } from "react"

import { APPNAVN, UTGAVER, lenkeTilUtgave, med } from "./fristil"
import { type Tema, settTema } from "./tema"
import type { Feil, SakUt, Skjermbilde, TavleRad, Tilstand } from "./tilstand"

/**
 * Skjermen, i React.
 *
 * Dette er den samme skjermen som Datastar-appen tegner, og det er hele
 * poenget. Forskjellen er hvor den tegnes: der sender serveren ferdige
 * HTML-biter, her sender den JSON og React setter sammen DOM-en i
 * nettleseren.
 *
 * Markupen skrives ikke for hånd her. `fs`-byggerne gir klassene og
 * koblingen, og da kan den ikke komme i utakt med designsystemet.
 * `data-preserve-attr` står i det byggerne sender ut, og er uten virkning i
 * denne appen: React eier DOM-en, og ingen morfer den. Det er riktig at det
 * står der likevel, for det er det samme attributtsettet begge apper får.
 */

/** Initialene i avataren. */
function initialer(navn: string): string {
  const deler = navn.trim().split(/\s+/).filter(Boolean).slice(0, 2)
  return deler.map((d) => d[0]!.toUpperCase()).join("") || "?"
}

/** Navnet appen har utad. Enumverdien er en maskinverdi. */
function appnavn(stack: string): string {
  if (stack === "tanstack") return "TanStack"
  if (stack === "datastar") return "Datastar"
  if (stack === "astro") return "Astro"
  return "Ukjent"
}

function farge(stack: string) {
  if (stack === "datastar") return "success" as const
  if (stack === "astro") return "warning" as const
  return "neutral" as const
}

function feltnavn(felle: string): string {
  if (felle === "fodselsdato") return "fødselsdatoen"
  if (felle === "kommune") return "kommunen"
  if (felle === "epost") return "e-postadressen"
  return felle
}

function stor(tekst: string): string {
  return tekst.charAt(0).toUpperCase() + tekst.slice(1)
}

function vedtaksord(vedtak: string | null | undefined): string {
  if (vedtak === "innvilget") return "Innvilget"
  if (vedtak === "avslatt") return "Avslått"
  return "Ikke besvart"
}

function tallord(n: number): string {
  return ["", "én", "to", "tre", "fire", "fem"][n] ?? String(n)
}

/* ------------------------------------------------------------------ */
/* Topplinja                                                           */
/* ------------------------------------------------------------------ */

export function Topplinje({
  tilstand,
  tema,
  spillerId,
}: {
  tilstand: Tilstand
  tema: Tema
  spillerId: string | null
}) {
  const sisteRunde = tilstand.rundeNr >= tilstand.runderTotalt
  const fase =
    tilstand.fase === "runde"
      ? `Runde ${tilstand.rundeNr} av ${tilstand.runderTotalt}`
      : tilstand.fase === "oppgjor"
        ? "Oppgjør"
        : "Omgangen er slutt"
  const merkelapp =
    tilstand.fase === "runde"
      ? "Frist"
      : tilstand.fase === "oppgjor"
        ? sisteRunde
          ? "Sluttstilling"
          : "Neste sak"
        : "Ny omgang"

  return (
    <header className="topplinje">
      <div className="topplinje__innhold stamme">
        <div id="topp" className="topplinje__hoved">
          <div className="topplinje__merke">
            <svg
              className="topplinje__emblem"
              viewBox="0 0 24 24"
              aria-hidden="true"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M6 2.75h6.5l5 5v13.5a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V3.75a1 1 0 0 1 1-1Z" />
              <path d="M12.5 2.75v5.5h5" />
              <path d="M8.5 13.5h7M8.5 17h4.5" />
            </svg>
            <div className="topplinje__ord">
              <h1 className="topplinje__navn">Førstelinja</h1>
              <span className="topplinje__etat">Etaten for alminnelige søknader</span>
            </div>
          </div>

          <p className="topplinje__fase">{fase}</p>

          <p className="topplinje__klokke">
            <span className="topplinje__merkelapp">{merkelapp}</span>
            <Nedtelling
              frist={tilstand.fristMs}
              lengde={tilstand.faseLengdeMs}
              naMs={tilstand.naMs}
              klokke
            />
          </p>

          <span className="topplinje__stack">React · {APPNAVN}</span>

          {tilstand.meg && (
            <p className="topplinje__meg">
              <span {...fs.avatar({ size: "small" })} aria-hidden="true">
                {initialer(tilstand.meg.navn)}
              </span>
              <span className="topplinje__navnet">{tilstand.meg.navn}</span>
            </p>
          )}
        </div>

        <Utgavevelger spillerId={spillerId} tema={tema} />
        <Temavelger tema={tema} />
      </div>
    </header>
  )
}

/**
 * Nedtellingen.
 *
 * Fristen er et absolutt tidspunkt fra serveren, og maskinen her kan gå
 * feil. Avviket regnes ut én gang, mot serverens egen klokke slik den sto da
 * siden ble tegnet, og legges til i hver avlesning.
 */
function Nedtelling({
  frist,
  lengde,
  naMs,
  klokke = false,
}: {
  frist: number
  lengde: number
  naMs: number
  klokke?: boolean
}) {
  const [igjen, settIgjen] = useState(() => Math.max(0, Math.round((frist - naMs) / 1000)))
  const avvik = useRef(0)

  useEffect(() => {
    avvik.current = naMs - Date.now()
  }, [naMs])

  useEffect(() => {
    const id = setInterval(() => {
      settIgjen(Math.max(0, Math.round((frist - (Date.now() + avvik.current)) / 1000)))
    }, 250)
    return () => clearInterval(id)
  }, [frist])

  const del = lengde > 0 ? Math.min(1, Math.max(0, (igjen * 1000) / lengde)) : 1
  const roedt = Math.round(Math.max(0, 1 - del * 2) * 100)
  // Under ti sekunder begynner klokka å riste, og rister mer for hvert
  // sekund. Styrken er et tall; utslaget og farten står i CSS-en, sammen med
  // sperren for den som har bedt om mindre bevegelse.
  const rist = igjen > 0 && igjen <= 10 ? (10 - igjen) / 9 : 0

  const tekst = klokke
    ? `${Math.floor(igjen / 60)}:${String(igjen % 60).padStart(2, "0")}`
    : String(igjen)

  return (
    <span
      className="nedtelling"
      /*
       * Tida på serveren og tida i nettleseren er ikke den samme.
       *
       * Serveren regner ut `igjen` i det siden sendes, og nettleseren regner
       * den ut igjen ved hydreringen, et sekund eller to senere. Da skiller
       * både fargen i `style` og `data-rister` seg, og React meldte
       * «some attributes of the server rendered HTML didn't match». Advarselen
       * kommer bare i utviklingsbygget, men den er ekte, og den er ventet:
       * dette er nøyaktig tilfellet attributtet finnes for. Alt annet i treet
       * skal fortsatt stemme, så det står her og ikke på noe høyere opp.
       */
      suppressHydrationWarning
      data-rister={rist > 0 ? "" : undefined}
      style={
        {
          color: `color-mix(in oklab, var(--spill-frist-slutt) ${roedt}%, var(--spill-frist-start))`,
          "--spill-rist": rist.toFixed(2),
        } as React.CSSProperties
      }
    >
      {tekst}
    </span>
  )
}

/** Lyst, mørkt eller det maskinen sier. */
function Temavelger({ tema }: { tema: Tema }) {
  const [valgt, settValgt] = useState<Tema>(tema)

  /*
   * Ingen effekt som setter temaet ved oppstart.
   *
   * Serveren har alt skrevet `data-theme` på rota, av kapselen, så siden
   * kommer ferdig i riktig tema. Her er det bare brukerens valg som skal
   * gjøre noe, og `settTema` skriver både kapselen og rota.
   */
  return (
    <fieldset {...med(fs.fieldset(), "temavelger")}>
      <legend {...fs.srOnly()}>Fargetema</legend>
      {(
        [
          ["light", "Lyst"],
          ["system", "System"],
          ["dark", "Mørkt"],
        ] as [Tema, string][]
      ).map(([verdi, tekst]) => (
        <label className={fs.toggleGroup.option} key={verdi}>
          <input
            type="radio"
            name="tema"
            value={verdi}
            checked={valgt === verdi}
            onChange={() => {
              settValgt(verdi)
              settTema(verdi)
            }}
          />{" "}
          {tekst}
        </label>
      ))}
    </fieldset>
  )
}

/** Bytt utgave underveis, uten å miste hvem du er. */
function Utgavevelger({ spillerId, tema }: { spillerId: string | null; tema: Tema }) {
  return (
    <nav className="utgavevelger" aria-label="Utgave">
      {UTGAVER.map((utgave) =>
        utgave.navn === APPNAVN ? (
          <span className="utgavevelger__her" aria-current="page" key={utgave.navn}>
            {utgave.navn}
          </span>
        ) : (
          <a
            className="utgavevelger__lenke"
            href={lenkeTilUtgave(utgave.adresse, spillerId, tema === "system" ? null : tema)}
            key={utgave.navn}
          >
            {utgave.navn}
          </a>
        ),
      )}
    </nav>
  )
}

/* ------------------------------------------------------------------ */
/* Saken                                                               */
/* ------------------------------------------------------------------ */

/**
 * Saken slik den ligger på bordet.
 *
 * Fella ligger i opplysningene om søkeren, ikke i teksten, så begge fanene
 * må leses. Det er hele oppgaven.
 */
function Sakskort({ sak }: { sak: SakUt }) {
  const [valgt, settValgt] = useState(0)
  const faner = fs.tabs({ id: "sak", count: 2, selected: valgt, label: "Saken" })
  const navn = ["Søknaden", "Søkeren"]

  return (
    <section {...med(fs.card(), "kort sakskort")}>
      <p className="sakskort__stempel">
        <span {...fs.tag()}>Sak {sak.id}</span>
        <span className="sakskort__status">Til behandling</span>
      </p>

      <h2 {...med(fs.heading({ size: "m" }), "sakskort__tittel")}>
        {sak.tittel}
      </h2>
      <p {...med(fs.paragraph(), "sakskort__ingress")}>
        {sak.sammendrag}
      </p>

      {/* Serveren skriver roller, kobling og hvilken fane som er valgt.
          Komponenten flytter valget når brukeren blar. I denne appen er det
          React som holder `selected`, og komponenten gjør det samme arbeidet
          med piltastene. */}
      <fs-tabs className="sakskort__faner">
        <div {...faner.list}>
          {navn.map((tekst, i) => (
            <button
              {...faner.tabs[i]}
              key={tekst}
              type="button"
              onClick={() => settValgt(i)}
            >
              {tekst}
            </button>
          ))}
        </div>

        <div {...faner.panels[0]}>
          <p {...med(fs.paragraph(), "sakskort__tekst")}>
            {sak.tekst}
          </p>
          <p className="sakskort__signatur">
            Med vennlig hilsen
            <br />
            {sak.soker.navn}
          </p>
        </div>

        <div {...faner.panels[1]}>
          <table {...fs.table()}>
            <tbody>
              <tr>
                <th scope="row">Navn</th>
                <td>{sak.soker.navn}</td>
              </tr>
              <tr>
                <th scope="row">Fødselsdato</th>
                <td>{sak.soker.fodselsdato}</td>
              </tr>
              <tr>
                <th scope="row">Kommune</th>
                <td>{sak.soker.kommune}</td>
              </tr>
              <tr>
                <th scope="row">E-post</th>
                <td>{sak.soker.epost}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </fs-tabs>
    </section>
  )
}

/* ------------------------------------------------------------------ */
/* Vedtaket                                                            */
/* ------------------------------------------------------------------ */

export type Skjema = {
  vedtak: string
  hjemmel: string
  kommune: string
  felle: string
}

const TOMT_SKJEMA: Skjema = { vedtak: "", hjemmel: "", kommune: "", felle: "" }

/** Skjemaet saksbehandleren fyller ut. */
function Vedtakskort({
  bilde,
  skjema,
  settSkjema,
  send,
}: {
  bilde: Skjermbilde
  skjema: Skjema
  settSkjema: (s: Skjema) => void
  send: () => void
}) {
  const { tilstand, kommuner, feil } = bilde
  const feilPa = (felt: string) => feil.find((f) => f.felt === felt)
  const boks = fs.errorSummary({ count: feil.length, id: "feilboks" })
  const [apen, settApen] = useState(false)
  const [markert, settMarkert] = useState(-1)

  const treff = skjema.kommune
    ? kommuner.filter((k) => k.toLowerCase().includes(skjema.kommune.toLowerCase()))
    : kommuner
  const forslag = fs.suggestion({
    id: "kommune",
    count: treff.length,
    activeIndex: markert >= 0 ? markert : undefined,
    open: apen,
    help: true,
    error: Boolean(feilPa("kommune")),
    invalid: Boolean(feilPa("kommune")),
  })

  return (
    <section {...med(fs.card(), "kort vedtakskort")}>
      <h2 {...fs.heading({ size: "s" })}>Ditt vedtak</h2>
      <p className="vedtakskort__ingress">
        Fire spørsmål, {tilstand.poeng.fullPott} poeng. Du kan svare én gang.
      </p>

      {/* Serveren skriver hele boksen, også overskriften og lista.
          Komponenten flytter bare fokus hit og tar klikkene på lenkene. */}
      <fs-error-summary {...boks.container}>
        {feil.length > 0 && (
          <>
            <h3 {...boks.title}>Du må rette {feil.length} feil</h3>
            <ul {...fs.list()}>
              {feil.map((f) => (
                <li key={f.felt}>
                  <a href={`#${f.felt}`}>{f.melding}</a>
                </li>
              ))}
            </ul>
          </>
        )}
      </fs-error-summary>

      <form
        className="skjema"
        onSubmit={(e) => {
          e.preventDefault()
          send()
        }}
      >
        <fieldset
          {...fs.fieldset({ state: feilPa("v-innvilget") ? "invalid" : undefined })}
        >
          <legend {...fs.legend()}>Utfall</legend>
          {[
            ["innvilget", "Innvilget"],
            ["avslatt", "Avslått"],
          ].map(([verdi, tekst]) => (
            <div className={fs.radio.row} key={verdi}>
              <input
                {...fs.radio({ state: feilPa("v-innvilget") ? "invalid" : undefined })}
                type="radio"
                id={verdi === "innvilget" ? "v-innvilget" : "v-avslatt"}
                name="vedtak"
                value={verdi}
                checked={skjema.vedtak === verdi}
                onChange={() => settSkjema({ ...skjema, vedtak: verdi! })}
              />
              <label
                {...fs.label()}
                htmlFor={verdi === "innvilget" ? "v-innvilget" : "v-avslatt"}
              >
                {tekst}
              </label>
            </div>
          ))}
        </fieldset>

        {/* Feiloppsummeringen lenker hit, så serveren navngir feltet og
            skriver hele koblingen selv. */}
        <fs-field>
          <label {...fs.label()} htmlFor="hjemmel">
            Hjemmel
          </label>
          <select
            {...fs.select({
              picker: "styled",
              state: feilPa("hjemmel") ? "invalid" : undefined,
            })}
            id="hjemmel"
            name="hjemmel"
            aria-describedby={feilPa("hjemmel") ? "hjemmel-feil" : undefined}
            value={skjema.hjemmel}
            onChange={(e) => settSkjema({ ...skjema, hjemmel: e.target.value })}
          >
            <option value="">Velg hjemmel</option>
            {tilstand.hjemler.map((h) => (
              <option value={h.kode} key={h.kode}>
                {h.kode} {h.tekst}
              </option>
            ))}
          </select>
          {feilPa("hjemmel") && (
            <p {...fs.errorText()} id="hjemmel-feil">
              {feilPa("hjemmel")!.melding}
            </p>
          )}
        </fs-field>

        <Hjemmelhjelp hjemler={tilstand.hjemler} />

        {/* Kommunefeltet. Her er det React som filtrerer: `treff` regnes ut
            av det som står i feltet, og bare de alternativene rendres.
            `prefiltered` sier fra om det.

            Filteret her er «teksten inneholder søkeordet», altså det samme
            komponenten ville gjort, så de to ville vært enige uansett.
            Attributtet står her fordi det er ærligere: filtreringen skjer én
            gang, i React, og demoen viser hva en app som filtrerer selv skal
            gjøre. Antall treff leses opp av komponenten uansett. */}
        <fs-suggestion className="kommunefelt" prefiltered>
          <label {...forslag.label}>Bekreft kommunen søkeren hører til</label>
          <div {...forslag.field}>
            <input
              {...forslag.control}
              name="kommune"
              value={skjema.kommune}
              onChange={(e) => {
                settSkjema({ ...skjema, kommune: e.target.value })
                settApen(true)
                settMarkert(-1)
              }}
              onFocus={() => settApen(true)}
              onBlur={() => window.setTimeout(() => settApen(false), 120)}
            />
            <ul {...forslag.list}>
              {treff.map((navn, i) => (
                <li
                  {...forslag.options[i]}
                  key={navn}
                  onMouseDown={() => {
                    settSkjema({ ...skjema, kommune: navn })
                    settApen(false)
                  }}
                >
                  {navn}
                </li>
              ))}
            </ul>
            <p {...forslag.empty} hidden={treff.length > 0}>
              Ingen treff. Finnes kommunen fortsatt?
            </p>
            <span {...forslag.status} />
          </div>
          <p {...forslag.help}>
            Begynn å skrive, så kommer forslagene. Finner du den ikke, la feltet stå tomt.
          </p>
          {feilPa("kommune") && (
            <p {...forslag.error}>{feilPa("kommune")!.melding}</p>
          )}
        </fs-suggestion>

        <fs-field>
          <label {...fs.label()} htmlFor="felle">
            Er noe feil i søknaden?
          </label>
          <select
            {...fs.select({
              picker: "styled",
              state: feilPa("felle") ? "invalid" : undefined,
            })}
            id="felle"
            name="felle"
            aria-describedby={`felle-hjelp${feilPa("felle") ? " felle-feil" : ""}`}
            value={skjema.felle}
            onChange={(e) => settSkjema({ ...skjema, felle: e.target.value })}
          >
            <option value="">Velg</option>
            <option value="nei">Nei, saken er i orden</option>
            <option value="fodselsdato">Fødselsdatoen</option>
            <option value="kommune">Kommunen</option>
            <option value="epost">E-postadressen</option>
          </select>
          <p {...fs.helpText()} id="felle-hjelp">
            Å se at alt er i orden teller like mye.
          </p>
          {feilPa("felle") && (
            <p {...fs.errorText()} id="felle-feil">
              {feilPa("felle")!.melding}
            </p>
          )}
        </fs-field>

        <button {...med(fs.button(), "skjema__send")} type="submit">
          Fatt vedtak
        </button>
      </form>
    </section>
  )
}

/**
 * Hjelpen til hjemlene.
 *
 * Serveren skriver koblingen mellom knappen og panelet; komponenten
 * plasserer det og lukker det. Om vinduet er åpent er brukerens tilstand, og
 * her er det React som holder den.
 */
function Hjemmelhjelp({ hjemler }: { hjemler: { kode: string; tekst: string }[] }) {
  const [apen, settApen] = useState(false)
  const boks = fs.popover({ id: "hjemmelhjelp", open: apen })

  return (
    <fs-popover {...boks.host} className="hjelpelenke" placement="bottom-start">
      <button
        {...boks.trigger}
        {...fs.button({ variant: "ghost" })}
        type="button"
        onClick={() => settApen((a) => !a)}
      >
        Hva betyr hjemlene?
      </button>
      <div {...boks.panel}>
        <ul {...fs.list()}>
          {hjemler.map((h) => (
            <li key={h.kode}>
              <strong>{h.kode}</strong> {h.tekst}
            </li>
          ))}
        </ul>
      </div>
    </fs-popover>
  )
}

/* ------------------------------------------------------------------ */
/* Kvittering, oppgjør og slutt                                        */
/* ------------------------------------------------------------------ */

/**
 * Svaret er levert, og spilleren får vite hvordan det gikk med én gang.
 *
 * Fasiten står ikke her. Den kommer når runden er over og alle har levert.
 * Det som står her er bare om ditt eget svar traff, og det er trygt fordi et
 * svar ikke kan gjøres om.
 */
function Kvittering({ tilstand }: { tilstand: Tilstand }) {
  const vurdering = tilstand.meg?.vurdering
  const poeng = vurdering?.poeng ?? 0
  const full = tilstand.poeng.fullPott

  const farge = poeng >= full ? "success" : poeng > 0 ? "info" : "warning"
  const overskrift =
    poeng >= full ? "Full pott" : poeng > 0 ? "Delvis truffet" : "Ingen uttelling"

  const linjer: [string, boolean, number][] = vurdering
    ? [
        ["Utfall", vurdering.vedtakRiktig, tilstand.poeng.vedtak],
        ["Hjemmel", vurdering.hjemmelRiktig, tilstand.poeng.hjemmel],
        ["Kommune", vurdering.kommuneRiktig, tilstand.poeng.kommune],
        ["Feil i søknaden", vurdering.felleRiktig, tilstand.poeng.felle],
      ]
    : []

  return (
    <section {...med(fs.card(), "kort vedtakskort")}>
      <h2 {...fs.heading({ size: "s" })}>Vedtaket er fattet</h2>

      <div {...fs.alert({ color: farge })}>
        <p className={fs.alert.title}>{overskrift}</p>
        <p>
          Du fikk{" "}
          <strong>
            {poeng} av {full} poeng
          </strong>{" "}
          på denne saken.
        </p>
      </div>

      {linjer.length > 0 && (
        <ul {...med(fs.list({ variant: "plain" }), "kvittering")}>
          {linjer.map(([navn, riktig, verdt]) => (
            <li className="kvittering__linje" data-riktig={String(riktig)} key={navn}>
              <span className="kvittering__felt">{navn}</span>
              <span className="kvittering__dom">{riktig ? "Riktig" : "Feil"}</span>
              <span className="kvittering__verdt">{riktig ? `+${verdt}` : "0"}</span>
            </li>
          ))}
        </ul>
      )}

      <p className="vedtakskort__ingress">
        Fasiten og begrunnelsen kommer når runden er over, og alle har levert.
      </p>
    </section>
  )
}

/** Hvem som kom best ut av saken. */
function BesteIRunden({ tilstand }: { tilstand: Tilstand }) {
  if (tilstand.tavle.length < 2) return null
  const beste = tilstand.tavle.reduce<TavleRad | null>(
    (best, rad) => (!best || rad.sistePoeng > best.sistePoeng ? rad : best),
    null,
  )
  if (!beste || beste.sistePoeng <= 0) return null

  const delt = tilstand.tavle.filter((r) => r.sistePoeng === beste.sistePoeng)
  const hvem =
    delt.length === 1 && beste.erMeg
      ? "Du kom best ut av saken"
      : delt.length === 1
        ? `${beste.navn} kom best ut av saken`
        : delt.length === tilstand.tavle.length
          ? "Alle kom likt ut av saken"
          : `${delt.map((r) => r.navn).join(" og ")} kom likt best ut`

  return (
    <p {...med(fs.alert({ color: "success" }), "beste")}>
      <strong>{hvem}</strong> med {beste.sistePoeng} av {tilstand.poeng.fullPott} poeng.
    </p>
  )
}

/** Fasit, poeng og plassering. Saken står igjen over, så svaret har noe å vise til. */
function Oppgjor({ tilstand, apneResultat }: { tilstand: Tilstand; apneResultat: () => void }) {
  const fasit = tilstand.fasit
  if (!fasit) return null
  const meg = tilstand.meg

  const flytting =
    !meg || meg.forrigePlass === 0
      ? ""
      : meg.plass < meg.forrigePlass
        ? `Du gikk fra ${meg.forrigePlass}. til ${meg.plass}. plass.`
        : meg.plass > meg.forrigePlass
          ? `Du falt fra ${meg.forrigePlass}. til ${meg.plass}. plass.`
          : `Du holder ${meg.plass}. plass.`

  return (
    <>
      <Sakskort sak={tilstand.sak} />

      <BesteIRunden tilstand={tilstand} />

      <section {...med(fs.card(), "kort fasitkort")}>
        <div className="fasitkort__topp">
          <h2 {...fs.heading({ size: "s" })}>Fasit</h2>
          <button
            {...fs.button({ variant: "secondary" })}
            type="button"
            onClick={apneResultat}
          >
            Se resultatet
          </button>
        </div>

        <dl className="fasit">
          <div className="fasit__rad">
            <dt>Utfall</dt>
            <dd>{fasit.vedtak === "innvilget" ? "Innvilget" : "Avslått"}</dd>
          </div>
          <div className="fasit__rad">
            <dt>Hjemmel</dt>
            <dd>{fasit.hjemmel}</dd>
          </div>
          <div className="fasit__rad">
            <dt>Kommune</dt>
            <dd>{tilstand.fasitKommune ?? "Ingen, kommunen finnes ikke lenger"}</dd>
          </div>
          <div className="fasit__rad">
            <dt>Feil i søknaden</dt>
            <dd>{fasit.felle ? stor(feltnavn(fasit.felle)) : "Ingen, saken var i orden"}</dd>
          </div>
        </dl>

        <div {...fs.alert({ color: "info" })}>
          <p>{tilstand.forklaring ?? ""}</p>
        </div>

        {meg && (
          <>
            <p className="poeng">
              <strong>+{meg.sistePoeng}</strong> denne runden · {meg.poeng} til sammen
            </p>
            <p className="vedtakskort__ingress">{flytting}</p>
          </>
        )}
      </section>
    </>
  )
}

/**
 * Ordet for plasseringen, og linja under.
 *
 * Den samme teksten står i alle tre utgavene. En vakt er over på få minutter,
 * og det eneste man sitter igjen med er hvor det gikk.
 */
function plasseringsord(plass: number): string {
  if (plass === 1) return "Gull"
  if (plass === 2) return "Sølv"
  if (plass === 3) return "Bronse"
  return `${plass}. plass`
}

function plasseringslinje(
  plass: number,
  antall: number,
  poeng: number,
  mulige: number,
): string {
  // Null poeng og gull er en ekte kombinasjon, og den fortjener sin egen linje.
  if (poeng === 0 && plass === 1) return "Null poeng, og likevel gull. Det sier mest om de andre."
  if (poeng === 0) return "Null poeng. Det skjer i førstelinja."

  const av = `${poeng} av ${mulige} mulige poeng.`
  if (plass === 1 && antall === 1) return `${av} Du var alene på vakt, så seieren var ikke omstridt.`
  if (plass === 1) return `${av} Du vant vakta.`
  if (plass === 2) return `${av} Så nær.`
  if (plass === 3) return `${av} Du kom deg på pallen.`
  return `${av} ${plass}. plass av ${antall}.`
}

/** Sluttstilling: din egen plassering, omgangens resultat, og den evige lista. */
function Slutt({ tilstand }: { tilstand: Tilstand }) {
  const meg = tilstand.meg
  const mulige = tilstand.poeng.fullPott * tilstand.runderTotalt

  return (
    <section {...med(fs.card(), "kort sluttkort")}>
      <h2 {...fs.heading({ size: "m" })}>Vakta er over</h2>

      {meg && (
        <div className="plassering" data-plass={meg.plass}>
          <span className="medalje medalje--stor" data-plass={meg.plass} aria-hidden="true">
            {meg.plass}
          </span>
          <div>
            <p className="plassering__ord">{plasseringsord(meg.plass)}</p>
            <p className="plassering__linje">
              {plasseringslinje(meg.plass, tilstand.tavle.length, meg.poeng, mulige)}
            </p>
          </div>
        </div>
      )}

      <h3 {...fs.heading({ size: "xs" })}>Omgangens resultat</h3>
      <div className={fs.table.scroll} tabIndex={0}>
        <table {...fs.table()}>
          <thead>
            <tr>
              <th>#</th>
              <th>Navn</th>
              <th>Poeng</th>
              <th>App</th>
            </tr>
          </thead>
          <tbody>
            {tilstand.tavle.length === 0 ? (
              <tr>
                <td colSpan={4}>Ingen spilte denne omgangen.</td>
              </tr>
            ) : (
              tilstand.tavle.map((rad) => (
                <tr key={rad.navn + rad.plass} className={rad.erMeg ? "meg" : undefined}>
                  <td>
                    <span className="medalje" data-plass={rad.plass} aria-hidden="true">
                      {rad.plass}
                    </span>
                  </td>
                  <td>
                    {rad.navn}
                    {rad.erMeg && <span className="deg">deg</span>}
                  </td>
                  <td>{rad.poeng}</td>
                  <td>
                    <span {...fs.badge({ color: farge(rad.stack) })}>{appnavn(rad.stack)}</span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <p {...fs.paragraph()}>
        Ny omgang starter om{" "}
        <Nedtelling
          frist={tilstand.fristMs}
          lengde={tilstand.faseLengdeMs}
          naMs={tilstand.naMs}
        />{" "}
        sekunder.
      </p>

      <h3 {...fs.heading({ size: "xs" })}>Evig toppliste</h3>
      <div className={fs.table.scroll} tabIndex={0}>
        <table {...fs.table()}>
          <thead>
            <tr>
              <th>#</th>
              <th>Navn</th>
              <th>Poeng</th>
              <th>App</th>
            </tr>
          </thead>
          <tbody>
            {tilstand.evigToppliste.length === 0 ? (
              <tr>
                <td colSpan={4}>Ingen ennå.</td>
              </tr>
            ) : (
              tilstand.evigToppliste.map((rad, i) => (
                <tr key={rad.navn + i}>
                  <td>{i + 1}</td>
                  <td>{rad.navn}</td>
                  <td>{rad.poeng}</td>
                  <td>
                    <span {...fs.badge({ color: farge(rad.stack) })}>{appnavn(rad.stack)}</span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </section>
  )
}

/** Tavla. Den samme i alle tre appene, og det er hele poenget. */
function Tavle({ tilstand }: { tilstand: Tilstand }) {
  // I runden sier den fjerde kolonnen hvem som har levert; det er det du
  // lurer på da. I oppgjøret sier den hvilken app hver spiller sitter i, som
  // er hele poenget med demoen.
  const oppgjor = tilstand.fase !== "runde"
  const paVakt = tilstand.tavle.length
  const levert = tilstand.tavle.filter((r) => r.harSvart).length

  return (
    <>
      <h2 {...fs.heading({ size: "s" })}>På vakt nå</h2>

      {!oppgjor && paVakt > 1 && (
        <p className="vedtakskort__svart" data-alle={String(levert >= paVakt)}>
          {levert >= paVakt
            ? "Alle har levert. Oppgjøret kommer straks."
            : `${levert} av ${paVakt} saksbehandlere har levert.`}
        </p>
      )}

      <div className={fs.table.scroll} tabIndex={0}>
        <table {...med(fs.table(), "tavle__tabell")}>
          <thead>
            <tr>
              <th>#</th>
              <th>Navn</th>
              <th>Poeng</th>
              <th>{oppgjor ? "App" : "Status"}</th>
            </tr>
          </thead>
          <tbody>
            {tilstand.tavle.length === 0 ? (
              <tr>
                <td colSpan={4}>Ingen på vakt.</td>
              </tr>
            ) : (
              tilstand.tavle.map((rad) => (
                <tr className={rad.erMeg ? "meg" : undefined} key={rad.plass}>
                  <td>{rad.plass}</td>
                  <td>
                    <span className="tavle__navn">
                      <span {...fs.avatar({ size: "small" })} aria-hidden="true">
                        {initialer(rad.navn)}
                      </span>
                      <span>
                        {rad.navn}
                        {rad.erMeg && <span className="tavle__deg"> (deg)</span>}
                      </span>
                    </span>
                  </td>
                  <td className="tavle__poeng">
                    {rad.poeng}
                    {oppgjor && rad.sistePoeng > 0 && (
                      <span className="tavle__runde"> +{rad.sistePoeng}</span>
                    )}
                  </td>
                  <td>
                    {oppgjor ? (
                      <span {...fs.badge({ color: farge(rad.stack) })}>
                        {appnavn(rad.stack)}
                      </span>
                    ) : rad.harSvart ? (
                      <span {...fs.badge({ color: "success" })}>Levert</span>
                    ) : (
                      <span {...fs.badge()}>Jobber</span>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      <p className="tavle__fot">Alle tre appene spiller på det samme brettet.</p>
    </>
  )
}

/**
 * Resultatet av runden, som en modal dialog.
 *
 * Hele skjemaet vurderes, og dialogen sier både hva du svarte og hva som var
 * riktig, felt for felt. Det holder ikke å si «feil»: da vet du fortsatt ikke
 * hva du skulle ha svart, og neste runde blir like tilfeldig.
 */
function Resultatdialog({
  tilstand,
  apen,
  settApen,
}: {
  tilstand: Tilstand
  apen: boolean
  settApen: (a: boolean) => void
}) {
  const fasit = tilstand.fasit
  const meg = tilstand.meg
  const boks = fs.dialog({ titleId: "resultat-tittel", open: apen })

  // Dialogen hører til oppgjøret, og innholdet skal ikke stå i markupen
  // ellers. Spilltjeneren sender ingen fasit i en runde, men skjermen skal
  // ikke hvile på det.
  if (tilstand.fase !== "oppgjor" || !fasit || !meg) return <fs-dialog id="resultat" />

  const svar = meg.svar
  const vurdering = meg.vurdering
  const besvart = Boolean(svar && vurdering)
  const full = tilstand.poeng.fullPott

  const utfall =
    besvart && (vurdering?.poeng ?? 0) >= full
      ? "full"
      : besvart && (vurdering?.poeng ?? 0) > 0
        ? "delvis"
        : besvart
          ? "ingen"
          : meg.medPaSaken
            ? "avvik"
            : "sent"

  const overskrift =
    utfall === "full"
      ? "Full pott"
      : utfall === "delvis"
        ? "Delvis truffet"
        : utfall === "ingen"
          ? "Ingen uttelling"
          : utfall === "sent"
            ? "Du kom inn midt i saken"
            : "Avvik registrert"

  const riktigKommune = tilstand.fasitKommune ?? "Ingen, kommunen finnes ikke lenger"
  const riktigFelle = fasit.felle ? stor(feltnavn(fasit.felle)) : "Ingen, saken var i orden"

  const rader: { navn: string; ditt: string; riktig: string; erRiktig: boolean }[] = [
    {
      navn: "Utfall",
      ditt: vedtaksord(svar?.vedtak),
      riktig: vedtaksord(fasit.vedtak),
      erRiktig: vurdering?.vedtakRiktig === true,
    },
    {
      navn: "Hjemmel",
      ditt: svar?.hjemmel || "Ikke besvart",
      riktig: fasit.hjemmel,
      erRiktig: vurdering?.hjemmelRiktig === true,
    },
    {
      navn: "Kommune",
      ditt: svar?.kommune || "Ingen",
      riktig: riktigKommune,
      erRiktig: vurdering?.kommuneRiktig === true,
    },
    {
      navn: "Feil i søknaden",
      ditt: svar?.felle ? stor(feltnavn(svar.felle)) : "Ingen",
      riktig: riktigFelle,
      erRiktig: vurdering?.felleRiktig === true,
    },
  ]

  const igjen = tilstand.grenseUtenSvar - meg.runderUtenSvar

  return (
    <fs-dialog id="resultat" {...boks.host}>
      <dialog {...med(boks.dialog, "resultat")} data-utfall={utfall}>
        <div className="resultat__topp">
          <h2 {...boks.title}>{overskrift}</h2>
          {utfall === "sent" ? (
            <p className="resultat__forklaring">
              Saken lå alt på bordet da du møtte. Ingen poeng denne runden, og du er med
              fra neste sak.
            </p>
          ) : utfall === "avvik" ? (
            <p className="resultat__forklaring">
              Saken ble ikke behandlet innen fristen. Avviket er varslet til
              statsforvalteren, og runden gir null poeng.
            </p>
          ) : (
            <p className="resultat__poeng">
              <strong>{vurdering?.poeng ?? 0}</strong> av {full} poeng
            </p>
          )}
        </div>

        <div {...boks.body}>
          <dl className="resultat__liste">
            {rader.map((rad) =>
              utfall === "sent" ? (
                <div className="resultat__rad" key={rad.navn}>
                  <dt className="resultat__felt">{rad.navn}</dt>
                  <dd className="resultat__ditt">{rad.riktig}</dd>
                </div>
              ) : (
                <div
                  className="resultat__rad"
                  data-riktig={String(rad.erRiktig)}
                  key={rad.navn}
                >
                  <dt className="resultat__felt">{rad.navn}</dt>
                  <dd className="resultat__ditt">{rad.ditt}</dd>
                  <dd className="resultat__dom">{rad.erRiktig ? "Riktig" : "Feil"}</dd>
                  {!rad.erRiktig && (
                    <dd className="resultat__riktig">Riktig svar: {rad.riktig}</dd>
                  )}
                </div>
              ),
            )}
          </dl>

          {meg.runderUtenSvar >= 1 && meg.runderUtenSvar < tilstand.grenseUtenSvar && (
            <p className="vedtakskort__ingress">
              Du har stått over{" "}
              {meg.runderUtenSvar === 1 ? "én sak" : `${meg.runderUtenSvar} saker`}.{" "}
              {igjen === 1
                ? "Står du over én til, blir du tatt av vakta."
                : `Står du over ${igjen} til, blir du tatt av vakta.`}
            </p>
          )}

          {/* Nøytral farge med vilje: toppfeltet over bærer utfallet, og to
              fargede flater oppå hverandre gjør det uklart hvilken av dem som
              betyr noe. */}
          <div {...fs.alert()}>
            <p className={fs.alert.title}>Sak {tilstand.sak.id}</p>
            <p>{tilstand.forklaring ?? ""}</p>
          </div>
        </div>

        <form method="dialog" {...boks.footer} onSubmit={() => settApen(false)}>
          <button {...fs.button()} value="lukk">
            Lukk
          </button>
        </form>
      </dialog>
    </fs-dialog>
  )
}

/**
 * Velkomsthilsenen, som forklarer hvorfor det finnes tre utgaver.
 *
 * Den vises bare før du har meldt deg på.
 */
function Velkomst({ apen, settApen }: { apen: boolean; settApen: (a: boolean) => void }) {
  const boks = fs.dialog({ titleId: "velkomst-tittel", open: apen })

  return (
    <fs-dialog id="velkomst" {...boks.host}>
      <dialog {...med(boks.dialog, "velkomst")}>
        <div className="velkomst__topp">
          <h2 {...boks.title}>Velkommen til Førstelinja</h2>
        </div>

        <div {...boks.body}>
          <p>
            Etaten for alminnelige søknader har de siste årene fått et økende antall
            henvendelser om hvilket rammeverk saksbehandlingsløsningen er skrevet i. Vi
            tar slike tilbakemeldinger på alvor.
          </p>
          <p>
            Løsningen leveres derfor i tre utgaver, skreddersydd til hvert sitt
            rammeverk: TanStack Start, Datastar og Astro. Du sitter nå i{" "}
            <strong>{APPNAVN}</strong>-utgaven.
          </p>
          <p>Vi er trygge på at du vil merke forskjellen.</p>

          <h3 {...med(fs.heading({ size: "xs" }), "velkomst__valg")}>
            Velg din foretrukne utgave
          </h3>
          <ul {...med(fs.list({ variant: "plain" }), "velkomst__liste")}>
            {UTGAVER.map((utgave) => (
              <li key={utgave.navn}>
                {utgave.navn === APPNAVN ? (
                  <span className="velkomst__her">
                    <strong>{utgave.navn}</strong>
                    <span className="velkomst__om">{utgave.rammeverk}</span>
                    <span {...fs.badge({ color: "success" })}>Du er her</span>
                  </span>
                ) : (
                  <a className="velkomst__lenke" href={utgave.adresse}>
                    <strong>{utgave.navn}</strong>
                    <span className="velkomst__om">{utgave.rammeverk}</span>
                  </a>
                )}
              </li>
            ))}
          </ul>

          <p className="velkomst__fotnote">
            Alle tre er bygget med det samme designsystemet, og det er nettopp poenget:
            du skal ikke merke forskjellen. Bytt utgave, og skjermen er den samme.
          </p>
        </div>

        <form method="dialog" {...boks.footer} onSubmit={() => settApen(false)}>
          {/* `autofocus` her, ellers tar `showModal()` den første lenken i
              lista, og et tilfeldig Enter sender deg til en annen utgave. */}
          <button {...fs.button()} value="lukk" autoFocus>
            Jeg merker nok forskjellen
          </button>
        </form>
      </dialog>
    </fs-dialog>
  )
}

/**
 * Skjemaet for å bli med.
 *
 * Et helt vanlig skjema, ikke en innsending med skript: kapselen må være satt
 * før hendelsesstrømmen åpnes, ellers vet strømmen ikke hvem du er. Det
 * virker dessuten uten JavaScript i det hele tatt.
 */
function BliMed({ tilstand }: { tilstand: Tilstand }) {
  /*
   * Id-en kommer fra React, ikke fra Fristil.
   *
   * `fs.field()` lager en selv når den ikke får en, og den kan ikke bli den
   * samme på serveren og i nettleseren. Da melder React hydreringsfeil på
   * ledeteksten, feltet og hjelpeteksten, og koblingen mellom dem er brutt
   * til React har rettet den opp. `useId()` gir den samme verdien begge
   * steder, og det er hele grunnen til at kroken finnes.
   */
  const felt = fs.field({ id: useId(), help: true })
  const sekunder = Math.round(tilstand.rundeLengdeMs / 1000)
  const tid =
    sekunder % 60 !== 0
      ? `${sekunder} sekunder`
      : sekunder === 60
        ? "ett minutt"
        : `${tallord(sekunder / 60)} minutter`

  return (
    <section {...med(fs.card(), "kort blimed")}>
      <h2 {...fs.heading({ size: "m" })}>Møt på vakt</h2>
      <p {...med(fs.paragraph(), "blimed__ingress")}>
        Du er saksbehandler i førstelinja. {tallord(tilstand.runderTotalt)} saker, {tid} på
        hver. Finn riktig utfall, riktig hjemmel, riktig kommune, og feilen søkeren håpet
        du ikke så.
      </p>

      <form method="post" action="/bli-med">
        <fs-field>
          <label {...felt.label}>Navnet ditt</label>
          <input {...fs.input()} {...felt.control} name="navn" type="text" required />
          <p {...fs.helpText()} {...felt.help}>
            Vises på tavla for alle.
          </p>
        </fs-field>

        <button {...med(fs.button(), "skjema__send")} type="submit">
          Begynn vakta
        </button>
      </form>
    </section>
  )
}

/* ------------------------------------------------------------------ */
/* Hele skjermen                                                       */
/* ------------------------------------------------------------------ */

export function Skjerm({
  forste,
  tema,
  spillerId,
  iRamme = false,
}: {
  forste: Skjermbilde
  tema: Tema
  spillerId: string | null
  iRamme?: boolean
}) {
  const [bilde, settBilde] = useState(forste)
  const [skjema, settSkjema] = useState<Skjema>(TOMT_SKJEMA)
  const [resultatApent, settResultatApent] = useState(false)
  /*
   * Hilsenen er en presentasjon av de tre utgavene, med en lenke til hver.
   * Står siden i en ramme, er skallet den presentasjonen: alle tre er
   * synlige samtidig, og en modal over hver av dem dekker nettopp det den
   * skulle fortalt om. Påmeldingen ligger utenfor dialogen, og
   * utgavevelgeren står i topplinja, så ingenting går tapt.
   */
  const [velkomstApen, settVelkomstApen] = useState(
    forste.tilstand.meg === null && !iRamme,
  )
  const forrigeSak = useRef(forste.tilstand.sak.id)
  const forrigeFase = useRef(forste.tilstand.fase)

  /*
   * Strømmen fra serveren.
   *
   * Her går det **JSON**, ikke HTML: serveren sier at noe har skjedd og
   * sender hele tilstanden, og React setter sammen skjermen på nytt. Det er
   * hele forskjellen fra Datastar-utgaven.
   *
   * To slags hendelser kommer ned. `tilstand` er spillet. `samband` kommer
   * når appserveren ikke får svar fra spilltjeneren: da står strømmen åpen
   * uten at noe skjer, og en frossen side som ser helt normal ut er verre
   * enn en feilmelding. `onerror` dekker det andre tilfellet, at strømmen
   * selv ryker.
   */
  useEffect(() => {
    const kilde = new EventSource("/hendelser")

    /*
     * Sambandslinja, men bare når den er oppgradert.
     *
     * Elementet står i HTML-en fra serveren lenge før komponenten er
     * registrert, og da har det ingen metoder. Uten denne sjekken kastet
     * første melding fra strømmen «reportSuccess is not a function», og
     * hele skjermen sto igjen utegnet. Er komponenten ikke der ennå, er det
     * heller ingen linje å melde noe til.
     */
    const status = () => {
      const linje = document.querySelector<
        HTMLElement & { reportFailure(): void; reportSuccess(): void }
      >("fs-connection-status")
      return typeof linje?.reportSuccess === "function" ? linje : null
    }

    kilde.addEventListener("tilstand", (hendelse) => {
      const nytt = JSON.parse((hendelse as MessageEvent<string>).data) as Skjermbilde
      settBilde((forrige) => ({ ...nytt, feil: forrige.feil }))
      status()?.reportSuccess()
    })
    kilde.addEventListener("samband", () => status()?.reportFailure())
    kilde.onerror = () => status()?.reportFailure()

    return () => kilde.close()
  }, [])

  // Ny sak betyr blanke felt. Oppgjøret teller ikke som ny sak: der står
  // saken fast, og svaret skal bli stående til det er talt opp.
  useEffect(() => {
    if (bilde.tilstand.sak.id !== forrigeSak.current) {
      forrigeSak.current = bilde.tilstand.sak.id
      settSkjema(TOMT_SKJEMA)
      settBilde((b) => ({ ...b, feil: [] }))
    }
  }, [bilde.tilstand.sak.id])

  // Dialogen åpnes når oppgjøret begynner, og lukkes når runden gjør det.
  useEffect(() => {
    if (bilde.tilstand.fase === forrigeFase.current) return
    forrigeFase.current = bilde.tilstand.fase
    settResultatApent(bilde.tilstand.fase === "oppgjor")
  }, [bilde.tilstand.fase])

  async function send() {
    try {
      const svar = await fetch("/svar", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(skjema),
      })
      if (!svar.ok) throw new Error(`Serveren svarte ${svar.status}`)
      settBilde((await svar.json()) as Skjermbilde)
    } catch {
      // Et vedtak som forsvinner i stillhet er det verste som kan skje her:
      // spilleren tror hun har levert, og runden går fra henne.
      const linje = document.querySelector<HTMLElement & { reportFailure(): void }>(
        "fs-connection-status",
      )
      if (typeof linje?.reportFailure === "function") linje.reportFailure()
    }
  }

  const { tilstand } = bilde

  return (
    <>
      {tilstand.meg === null && !iRamme && (
        <Velkomst apen={velkomstApen} settApen={settVelkomstApen} />
      )}

      <fs-connection-status
        {...fs.connectionStatus()}
        offline-text="Sambandet til etaten er nede"
        online-text="Sambandet er tilbake"
      />

      <Topplinje tilstand={tilstand} tema={tema} spillerId={spillerId} />

      <main className="brett">
        <div className="stamme">
          <div id="brett" className="brett__innhold">
            <div id="sak" className="brett__hoved">
              {tilstand.meg === null ? (
                <BliMed tilstand={tilstand} />
              ) : tilstand.fase === "runde" ? (
                <>
                  <Sakskort sak={tilstand.sak} />
                  {tilstand.meg.harSvart ? (
                    <Kvittering tilstand={tilstand} />
                  ) : (
                    <Vedtakskort
                      bilde={bilde}
                      skjema={skjema}
                      settSkjema={settSkjema}
                      send={send}
                    />
                  )}
                </>
              ) : tilstand.fase === "oppgjor" ? (
                <Oppgjor tilstand={tilstand} apneResultat={() => settResultatApent(true)} />
              ) : (
                <Slutt tilstand={tilstand} />
              )}
            </div>

            <aside id="tavle" {...med(fs.card(), "kort tavle")}>
              <Tavle tilstand={tilstand} />
            </aside>

            <Resultatdialog
              tilstand={tilstand}
              apen={resultatApent}
              settApen={settResultatApent}
            />
          </div>
        </div>
      </main>
    </>
  )
}
