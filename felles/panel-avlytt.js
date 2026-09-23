/**
 * Avlyttingen av ledningen, altså det som svarer på «med hva».
 *
 * Dette er en egen fil, og et vanlig skript uten `type="module"`. Grunnen er
 * rekkefølge, og den er hele forklaringen på hvorfor panelet løy før:
 *
 * Et modulskript kjører først når dokumentet er ferdig parset, og de kjører i
 * dokumentrekkefølge. Panelet lå sist i `<body>`, mens Datastars bundle lå i
 * `<head>` og Astros øy rett over panelet. Begge åpner strømmen sin i det de
 * kjører, altså før panelet rakk å legge seg utenpå `fetch` og `EventSource`.
 * Panelet så da aldri den ene strømmen som bærer alt, og skrev «Ingenting har
 * kommet over ledningen ennå» i den ene utgaven som sender mest.
 *
 * Testet i alle tre appene, med en sonde som noterer hvem som kom først:
 * Datastar og Astro åpnet strømmen før innpakningen var på plass, React kom
 * etter fordi den kobler seg opp fra en effekt etter hydreringen.
 *
 * Et vanlig skript i `<head>` kjører derimot mens dokumentet parses, altså før
 * hvert eneste modulskript uansett hvor de står. Derfor denne fila, og derfor
 * skal taggen stå først i `<head>`:
 *
 *     <script src="/panel-avlytt.js"></script>
 *
 * Fila skriver ingenting på skjermen og rører ingen DOM. Den noterer bare det
 * siste som kom, på `window.forstelinjaLedning`, og `panel.js` leser derfra.
 */
;(() => {
  /** Hvor mye av nyttelasten som vises. Resten kuttes med en ellipse. */
  const MAKS_TEGN = 2000

  const ledning = {
    /** Det siste som kom over ledningen, eller `null`. */
    siste: null,
    /** Hvor mange ganger noe har kommet. Panelet bruker det ikke, men en test gjør. */
    antall: 0,
    /** Satt av denne fila, slik at panelet vet at avlyttingen er på plass. */
    avlyttet: true,
  }
  window.forstelinjaLedning = ledning

  const koder = new TextEncoder()

  /**
   * Hva slags innhold dette er, lest av innholdet og ikke av hva noen påstår.
   *
   * Rekkefølgen er ikke tilfeldig. Datastars nyttelast begynner med sine egne
   * nøkkelord, «elements» eller «selector», og har HTML etter dem, så HTML må
   * sjekkes før vi gir opp og kaller det tekst. JSON prøves først, siden en
   * JSON-streng aldri kan forveksles med noe annet.
   */
  /**
   * Datastars egne nøkkelord, som står foran innholdet på hver datalinje.
   *
   * «elements <div …>» og «signals {…}» er HTML og JSON med et ord foran.
   * Uten dette ble signalene meldt som «tekst», og merkelappen sa mindre enn
   * den kunne. Innholdet vises som det kom, nøkkelordene medregnet.
   */
  const DATASTAR_ORD =
    /^(elements|signals|selector|mode|useViewTransition|onlyIfMissing) /

  function utenNokkelord(tekst) {
    const linjer = tekst.split("\n")
    if (!linjer.some((linje) => DATASTAR_ORD.test(linje))) return tekst
    return linjer.map((linje) => linje.replace(DATASTAR_ORD, "")).join("\n")
  }

  function formatet(raa) {
    const tekst = utenNokkelord(raa)
    const ren = tekst.trim()
    if (!ren) return "tomt"
    if (/^[[{]/.test(ren)) {
      try {
        JSON.parse(ren)
        return "JSON"
      } catch {
        // Avkuttet JSON er fortsatt JSON, og en halv streng skal ikke bli «tekst».
        return "JSON"
      }
    }
    if (/<[a-z!/][^>]*>/i.test(ren)) return "HTML"
    return "tekst"
  }

  function kort(tekst) {
    const ren = String(tekst ?? "").trim()
    return ren.length > MAKS_TEGN ? `${ren.slice(0, MAKS_TEGN)}\n…` : ren
  }

  /**
   * Noterer at noe kom.
   *
   * `transport` er hvordan det kom: «SSE», «Svar på forespørsel» eller
   * «Helsidelasting». `hendelse` er navnet på SSE-hendelsen når det finnes,
   * og det er nettopp det navnet som skiller de to utgavene fra hverandre:
   * `datastar-patch-elements` mot `tilstand`.
   */
  function noter({ transport, hendelse, tekst, url }) {
    const innhold = kort(tekst)
    if (!innhold) return
    ledning.siste = {
      transport,
      hendelse: hendelse ?? null,
      format: formatet(innhold),
      bytes: koder.encode(String(tekst ?? "")).length,
      tekst: innhold,
      url: url ?? null,
      nar: Date.now(),
    }
    ledning.antall += 1
  }

  ledning.noter = noter

  /*
   * Strømmen som `EventSource`.
   *
   * React-utgaven bruker denne. Navngitte hendelser ses ikke av en lytter på
   * «message», så innpakningen legger seg også utenpå `addEventListener`, og
   * fanger navnet på hendelsen samtidig.
   */
  const OpprinneligEventSource = window.EventSource
  if (OpprinneligEventSource) {
    const Innpakket = function (url, valg) {
      const strom = new OpprinneligEventSource(url, valg)
      const opprinneligLytt = strom.addEventListener.bind(strom)

      opprinneligLytt("message", (e) => {
        noter({ transport: "SSE", hendelse: "message", tekst: e.data, url: String(url) })
      })

      strom.addEventListener = (navn, lytter, valg2) => {
        if (navn === "message") return opprinneligLytt(navn, lytter, valg2)
        return opprinneligLytt(
          navn,
          (e) => {
            if (typeof e.data === "string") {
              noter({ transport: "SSE", hendelse: navn, tekst: e.data, url: String(url) })
            }
            if (typeof lytter === "function") lytter(e)
            else lytter?.handleEvent?.(e)
          },
          valg2,
        )
      }
      return strom
    }
    Innpakket.prototype = OpprinneligEventSource.prototype
    Innpakket.CONNECTING = OpprinneligEventSource.CONNECTING
    Innpakket.OPEN = OpprinneligEventSource.OPEN
    Innpakket.CLOSED = OpprinneligEventSource.CLOSED
    window.EventSource = Innpakket
  }

  /**
   * Én ramme fra en hendelsesstrøm, delt i navn og innhold.
   *
   * Formatet er linjer: `event:` gir navnet, `data:` gir innholdet, og flere
   * `data:`-linjer i samme ramme hører sammen med linjeskift imellom. Alt
   * annet, som `id:` og `retry:`, er ikke nyttelast.
   *
   * Linjeskiftet er normalisert før vi kommer hit, se `normaliser`. Det var
   * den ene grunnen til at Datastar-utgaven aldri viste noe: Ktor avslutter
   * hver linje med CR LF, parseren delte bare på to linjeskift av typen LF,
   * og fant dermed aldri en eneste ramme. Spesifikasjonen tillater CR LF, LF
   * og CR alene, så en parser som bare kjenner den ene er feil.
   */
  function lesRamme(ramme) {
    let navn = null
    const data = []
    for (const linje of ramme.split("\n")) {
      if (linje.startsWith("event:")) navn = linje.slice(6).trim()
      else if (linje.startsWith("data:")) data.push(linje.slice(5).replace(/^ /, ""))
    }
    return { navn, data: data.join("\n") }
  }

  /*
   * Strømmen som `fetch`, og svar på vanlige forespørsler.
   *
   * Datastar henter hendelsesstrømmen med `fetch` og leser den som en strøm.
   * De to andre sender vedtaket med `fetch` og får JSON tilbake. Begge deler
   * er «noe som kom over ledningen», og begge skal vises.
   */
  const opprinneligFetch = window.fetch
  window.fetch = async function (...argumenter) {
    const svar = await opprinneligFetch.apply(this, argumenter)
    const type = svar.headers.get("content-type") ?? ""
    const url = svar.url || String(argumenter[0])

    if (type.includes("text/event-stream") && svar.body) {
      /*
       * Strømmen deles i to: én til appen, én hit. Uten delingen ville
       * panelet spist bytene appen venter på, og skjermen sluttet å
       * oppdatere seg.
       */
      const [tilAppen, tilPanelet] = svar.body.tee()
      void (async () => {
        const leser = tilPanelet.pipeThrough(new TextDecoderStream()).getReader()
        let rest = ""
        /*
         * En CR som kom sist i en bit kan være første halvdel av et CR LF som
         * kommer i neste. Den holdes igjen, ellers blir ett linjeskift til to.
         */
        let venterCR = false
        const normaliser = (bit) => {
          let tekst = venterCR ? `\r${bit}` : bit
          venterCR = tekst.endsWith("\r")
          if (venterCR) tekst = tekst.slice(0, -1)
          return tekst.replace(/\r\n/g, "\n").replace(/\r/g, "\n")
        }

        for (;;) {
          const { done, value } = await leser.read()
          if (done) break
          rest += normaliser(value)
          const rammer = rest.split("\n\n")
          rest = rammer.pop() ?? ""
          for (const ramme of rammer) {
            const { navn, data } = lesRamme(ramme)
            if (data) noter({ transport: "SSE", hendelse: navn, tekst: data, url })
          }
        }
      })()

      return new Response(tilAppen, {
        status: svar.status,
        statusText: svar.statusText,
        headers: svar.headers,
      })
    }

    // Et vanlig svar leses fra en kopi, så appen får sitt eget urørt.
    if (type.includes("json") || type.includes("text/html")) {
      void svar
        .clone()
        .text()
        .then((tekst) => noter({ transport: "Svar på forespørsel", tekst, url }))
        .catch(() => {})
    }

    return svar
  }
})()
