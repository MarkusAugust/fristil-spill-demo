/**
 * Panelet som viser hva som ble oppdatert, hvordan, og med hva.
 *
 * Hele poenget med demoen er at de tre utgavene ser og oppfører seg likt,
 * mens de gjør noe helt forskjellig under. Det er vanskelig å vise ved å se
 * på skjermen, for det er jo nettopp det som er likt. Panelet viser
 * forskjellen: hvilken del av siden som faktisk ble byttet ut, hvilken
 * teknikk som gjorde det, og hva som kom over ledningen.
 *
 * Fila ligger i `felles/`, av samme grunn som `brett.css`: den er det som gjør
 * at de tre panelene er det samme panelet. Hver app serverer den som
 * `/panel.js` og laster den med `<script type="module">`. Ingen bundling,
 * ingen import fra npm.
 *
 * Appen forteller bare hvilken teknikk den bruker:
 *
 *     import { startPanel } from "/panel.js"
 *     startPanel({ teknikk: "…", forklaring: "…" })
 *
 * Resten finner panelet ut selv, og det er med vilje: at det samme skriptet
 * ser tre ulike ting skje er sterkere enn tre apper som forteller hver sin
 * historie.
 */

/** Hvor mange oppdateringer som huskes. Nok til å bla litt tilbake. */
const HUSKES = 12

/**
 * Områdene det er verdt å si fra om, innerst først.
 *
 * `closest` går oppover, så den første velgeren som treffer en forfar vinner.
 * Står `#brett` før `#sak`, heter alt «brettet».
 */
const OMRADER = [
  ["#sak", "saken"],
  ["#oppgjor", "oppgjøret"],
  ["#resultat", "kvitteringen"],
  ["#velkomst", "velkomsthilsenen"],
  ["#tavle", "tavla"],
  ["form", "skjemaet"],
  ["#brett", "brettet"],
  ["#topp", "topplinja"],
]

/**
 * Endringer panelet ikke skal si fra om.
 *
 * Nedtellingen regnes ut i nettleseren og tikker hvert sekund. Den kommer
 * ikke fra serveren i det hele tatt, så den er ikke en oppdatering. Uten
 * dette fylte den hele loggen med den samme linja, ett sekund om gangen, og
 * de ekte oppdateringene forsvant i den.
 */
const STOY = [".nedtelling", "[data-nedtelling]", "fs-connection-status"]

/**
 * Nøkkelen panelet husker seg selv under.
 *
 * Astro sender hele siden på nytt ved hver innsending, og da bygges panelet
 * fra bunnen: lukket, med tom logg. Brukeren som nettopp åpnet det, og så
 * fattet et vedtak, satt igjen uten å se hva som skjedde. Minnet ligger i
 * `sessionStorage`, altså per fane og ikke lenger enn økten.
 */
const MINNE = "forstelinja-panel"

const tilstand = {
  teknikk: "",
  forklaring: "",
  hendelser: [],
  /** Om panelet står åpent. Huskes, se `MINNE`. */
  apen: false,
  /** Siste nyttelast fra ledningen, satt av lytterne under. */
  sisteNyttelast: null,
}

/** Elementet en endring hører til. En tekstendring peker på en tekstnode. */
function elementFor(node) {
  return node instanceof Element ? node : (node?.parentElement ?? null)
}

function navnPa(node) {
  const element = elementFor(node)
  if (!element) return "siden"
  for (const [velger, navn] of OMRADER) {
    if (element.closest(velger)) return navn
  }
  return "siden"
}

/** Om endringen er noe annet enn en oppdatering. Se `STOY`. */
function erStoy(node) {
  const element = elementFor(node)
  return Boolean(element) && STOY.some((velger) => element.closest(velger))
}

/**
 * Hva en helsidelasting kostet.
 *
 * Det er nyttelasten i den utgaven som sender hele dokumentet, og tallet er
 * hele poenget ved siden av en JSON-bit på et par kilobyte. `transferSize` er
 * det som faktisk gikk over ledningen, komprimering medregnet.
 */
function sidelast() {
  const [navigering] = performance.getEntriesByType("navigation")
  const bytes = navigering?.transferSize
  if (!bytes) return "Hele dokumentet."
  return `Hele dokumentet, ${Math.max(1, Math.round(bytes / 1024))} kB over ledningen.`
}

function husk() {
  try {
    sessionStorage.setItem(
      MINNE,
      JSON.stringify({ apen: tilstand.apen, hendelser: tilstand.hendelser }),
    )
  } catch {
    // Privat vindu, eller lagringen er slått av. Panelet virker uten minne.
  }
}

/**
 * Henter det panelet husket, og noterer at siden ble lastet.
 *
 * Lastingen er ikke støy her, den er selve poenget: i Astro er det den som
 * er oppdateringen, mens de to andre laster én gang og bytter ut biter
 * etterpå. Den noteres bare når det står noe i loggen fra før, ellers ville
 * hver første lasting fått en linje som ikke sier noe.
 */
function hentMinne() {
  try {
    const lagret = JSON.parse(sessionStorage.getItem(MINNE) ?? "null")
    if (!lagret || !Array.isArray(lagret.hendelser)) return

    tilstand.apen = Boolean(lagret.apen)
    tilstand.hendelser = lagret.hendelser.slice(0, HUSKES)
    if (tilstand.hendelser.length > 0) {
      tilstand.hendelser.unshift({
        omrade: "hele siden ble lastet på nytt",
        nar: Date.now(),
        antall: 1,
        nyttelast: sidelast(),
      })
      tilstand.hendelser.length = Math.min(tilstand.hendelser.length, HUSKES)
    }
  } catch {
    // Ødelagt eller utilgjengelig minne. Panelet begynner på nytt.
  }
}

function kort(tekst, tegn = 600) {
  if (typeof tekst !== "string") return ""
  const ren = tekst.trim()
  return ren.length > tegn ? `${ren.slice(0, tegn)}\n…` : ren
}

/**
 * Lyser opp det som nettopp ble byttet ut.
 *
 * Klassen fjernes og settes på nytt, med en tvunget omtegning imellom, ellers
 * starter ikke animasjonen på nytt når det samme området oppdateres to ganger
 * etter hverandre.
 */
const LYSER = "panel-lyser"

function lysOpp(element) {
  if (!(element instanceof Element)) return
  element.classList.remove(LYSER)
  void element.getBoundingClientRect()
  element.classList.add(LYSER)
  window.setTimeout(() => element.classList.remove(LYSER), 1200)
}

/**
 * Om en endring er panelets egen markering.
 *
 * `lysOpp()` skriver `class` på et element ute på siden, altså på et element
 * observatøren ser. Uten denne sjekken noterte panelet sin egen markering,
 * lyste opp på nytt, og siden hang i en løkke uten en eneste feilmelding:
 * velkomsthilsenen ble aldri en modal, og en ny lasting ble aldri ferdig.
 *
 * Sammenligningen ser på klasselista før og etter, uten `panel-lyser`. Er de
 * like, er det bare markeringen som har endret seg, og den er vår.
 */
function egenMarkering(post) {
  if (post.type !== "attributes" || post.attributeName !== "class") return false
  if (!(post.target instanceof Element)) return false

  const for_ = new Set((post.oldValue ?? "").split(/\s+/).filter(Boolean))
  const na = new Set(post.target.classList)
  for_.delete(LYSER)
  na.delete(LYSER)

  return for_.size === na.size && [...for_].every((klasse) => na.has(klasse))
}

function noter(node) {
  const omrade = navnPa(node)
  const forrige = tilstand.hendelser[0]

  // Én patch gir mange mutasjoner. De som hører sammen slås sammen, ellers
  // fylles lista av tjue linjer som sier det samme.
  if (forrige && forrige.omrade === omrade && Date.now() - forrige.nar < 400) {
    forrige.antall += 1
  } else {
    tilstand.hendelser.unshift({
      omrade,
      nar: Date.now(),
      antall: 1,
      nyttelast: tilstand.sisteNyttelast,
    })
    tilstand.hendelser.length = Math.min(tilstand.hendelser.length, HUSKES)
  }

  lysOpp(elementFor(node))
  husk()
  tegn()
}

/**
 * Trafikken appen selv henter, lest med.
 *
 * De tre appene henter det samme på tre måter: Datastar med `fetch` og en
 * lesestrøm, React og Astro med `EventSource`. Panelet legger seg utenpå
 * begge, så «med hva» blir det som faktisk kom over ledningen, ikke noe appen
 * forteller oss. Det er den ærlige versjonen: står det HTML her i én app og
 * JSON i en annen, er det fordi det er slik.
 */
function lyttPaLedningen() {
  const EchoSource = window.EventSource
  if (EchoSource) {
    window.EventSource = function (url, valg) {
      const strom = new EchoSource(url, valg)
      strom.addEventListener("message", (e) => {
        tilstand.sisteNyttelast = kort(e.data)
      })
      // Datastar og andre sender navngitte hendelser. `message` ser dem ikke.
      const opprinnelig = strom.addEventListener.bind(strom)
      strom.addEventListener = (navn, lytter, valg2) => {
        if (navn === "message") return opprinnelig(navn, lytter, valg2)
        return opprinnelig(
          navn,
          (e) => {
            if (typeof e.data === "string") tilstand.sisteNyttelast = kort(e.data)
            lytter(e)
          },
          valg2,
        )
      }
      return strom
    }
    window.EventSource.prototype = EchoSource.prototype
  }

  const opprinneligFetch = window.fetch
  window.fetch = async (...argumenter) => {
    const svar = await opprinneligFetch(...argumenter)
    const type = svar.headers.get("content-type") ?? ""
    if (!type.includes("text/event-stream") || !svar.body) return svar

    // Strømmen deles i to: én til appen, én hit. Uten delingen ville
    // panelet spist bytene appen venter på.
    const [tilAppen, tilPanelet] = svar.body.tee()
    void (async () => {
      const leser = tilPanelet.pipeThrough(new TextDecoderStream()).getReader()
      let rest = ""
      for (;;) {
        const { done, value } = await leser.read()
        if (done) break
        rest += value
        const biter = rest.split("\n\n")
        rest = biter.pop() ?? ""
        for (const bit of biter) {
          const data = bit
            .split("\n")
            .filter((linje) => linje.startsWith("data:"))
            .map((linje) => linje.slice(5).trimStart())
            .join("\n")
          if (data) tilstand.sisteNyttelast = kort(data)
        }
      }
    })()

    return new Response(tilAppen, {
      status: svar.status,
      statusText: svar.statusText,
      headers: svar.headers,
    })
  }
}

/*
 * Ledningen avlyttes i det modulen lastes, ikke når panelet bygges.
 *
 * Appen kobler seg opp med en gang, og i React skjer det fra en effekt, altså
 * før panelet rekker å bli bygget. Ventet vi på `DOMContentLoaded`, sto «Med
 * hva» tom i alle tre utgavene: strømmen var alt åpnet, og en innpakning av
 * `EventSource` etterpå ser ingenting.
 *
 * Derfor laster React-utgaven modulen fra en skripttagg og kaller
 * `startPanel` fra en effekt: det første gir avlyttingen tidlig nok, det
 * andre gir panelet en vert React ikke kaster bort.
 */
lyttPaLedningen()

/**
 * Panelets egen markup. Fristils klasser, ingen egne farger.
 *
 * Verten kan finnes fra før. I React er `document.body` en del av
 * komponenttreet, og en strukturell rendring kaster bort noder React ikke vet
 * om: panelet sto der ved innlasting og var borte i det brukeren meldte seg
 * på. Rendrer appen verten selv, beholder React den, og panelet fyller den
 * ut. Det er den samme regelen komponentene i Fristil følger.
 */
function byggPanel() {
  const vert =
    document.querySelector(".panelvert") ?? document.createElement("div")
  vert.className = "panelvert"
  vert.innerHTML = `
    <button type="button" class="fs-button panelknapp" aria-expanded="false"
            aria-controls="panel">Hva skjedde?</button>
    <section id="panel" class="fs-card panel" hidden aria-label="Hva skjedde">
      <div class="panel__del">
        <h2 class="fs-heading panel__tittel" data-size="xs">Hva som ble oppdatert</h2>
        <ol class="fs-list panel__logg" data-variant="plain"></ol>
      </div>
      <div class="panel__del">
        <h2 class="fs-heading panel__tittel" data-size="xs">Hvordan</h2>
        <p class="fs-paragraph panel__teknikk"></p>
        <p class="fs-paragraph panel__forklaring" data-size="small"></p>
      </div>
      <div class="panel__del panel__del--bred">
        <h2 class="fs-heading panel__tittel" data-size="xs">Med hva</h2>
        <pre class="panel__nyttelast" tabindex="0"></pre>
      </div>
    </section>`
  if (!vert.isConnected) document.body.append(vert)

  const knapp = vert.querySelector(".panelknapp")
  const panel = vert.querySelector("#panel")

  const vis = (apen) => {
    tilstand.apen = apen
    panel.hidden = !apen
    knapp.setAttribute("aria-expanded", String(apen))
    knapp.textContent = apen ? "Skjul" : "Hva skjedde?"
  }

  vis(tilstand.apen)
  knapp.addEventListener("click", () => {
    vis(panel.hidden)
    husk()
  })

  return vert
}

let vertselement = null

function tegn() {
  if (!vertselement) return

  const logg = vertselement.querySelector(".panel__logg")
  logg.replaceChildren(
    ...tilstand.hendelser.map((h) => {
      const linje = document.createElement("li")
      linje.className = "panel__linje"
      const klokke = new Date(h.nar).toLocaleTimeString("no-NO")
      const antall = h.antall > 1 ? ` · ${h.antall} endringer` : ""
      linje.textContent = `${klokke} · ${h.omrade}${antall}`
      return linje
    }),
  )

  /*
   * Den nyeste nyttelasten, ikke nyttelasten til den nyeste hendelsen. En
   * patch gir gjerne flere endringer, og bare den første av dem har noe over
   * ledningen knyttet til seg. Leste vi bare hendelse null, sto det
   * «ingenting har kommet» rett etter en patch som nettopp hadde kommet.
   */
  const nyttelast = vertselement.querySelector(".panel__nyttelast")
  const tekst = tilstand.hendelser.find((h) => h.nyttelast)?.nyttelast ?? ""
  nyttelast.textContent = tekst || "Ingenting har kommet over ledningen ennå."
}

/**
 * Starter panelet.
 *
 * `teknikk` er én setning om hvordan denne utgaven oppdaterer seg, og
 * `forklaring` er linja under, som sier hva det betyr i praksis. Alt annet
 * finner panelet ut selv.
 */
export function startPanel({ teknikk, forklaring }) {
  if (vertselement) return

  tilstand.teknikk = teknikk
  tilstand.forklaring = forklaring

  const start = () => {
    hentMinne()
    vertselement = byggPanel()
    vertselement.querySelector(".panel__teknikk").textContent = teknikk
    vertselement.querySelector(".panel__forklaring").textContent = forklaring
    tegn()

    /*
     * Hele siden observeres, ikke bare brettet. Astro bytter ut hele
     * dokumentet ved en innsending, og da finnes ikke `#brett` fra før.
     * `characterData` er med fordi nedtellingen og poengsummen bare endrer
     * tekst.
     */
    const observator = new MutationObserver((poster) => {
      for (const post of poster) {
        // Panelet skal ikke se seg selv, verken der det tegner eller der det
        // lyser opp.
        const mal = post.target
        if (mal instanceof Element && mal.closest(".panelvert")) continue
        if (mal.parentElement?.closest?.(".panelvert")) continue
        if (egenMarkering(post)) continue
        if (erStoy(mal)) continue
        noter(mal)
        break
      }

      // `noter` lyste nettopp opp et element, og den endringen står alt i
      // køen. `egenMarkering` fanger den, men å kaste den her sparer en
      // runde, og gjør at en markering aldri kan telle som en oppdatering.
      observator.takeRecords()
    })

    observator.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeOldValue: true,
      characterData: true,
    })
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start, { once: true })
  } else {
    start()
  }
}
