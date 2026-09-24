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
 * `/panel.js`, og fila selv importerer ingenting. Den er ikke en del av noe
 * bygg, og skal kunne lastes som den er.
 *
 * Appen henter den og sier hvilken teknikk den bruker:
 *
 *     const { startPanel } = await import("/panel.js")
 *     startPanel({ teknikk: "…", forklaring: "…", utgave: "datastar", fs })
 *
 * Datastar-utgaven skriver et vanlig `import` i et modulskript. De to andre
 * bruker `import()` med adressen i en variabel: Vite leser et bokstavelig
 * `import` og prøver å slå opp `/panel.js` under bygget, og den er en rute på
 * serveren og ingen fil på disken.
 *
 * `fs` sendes inn framfor å importeres her. Fila er delt av tre apper med
 * hvert sitt spor: to bunter designsystemet og har det allerede, én henter
 * det fra CDN. Importerte panelet det selv, måtte adressen være en URL, og
 * da fikk de to buntede appene en ekstern avhengighet i drift for et
 * feilsøkingspanel. Testet: importen alene er 45 kall til jsDelivr, og de to
 * appene gikk fra null til 45. Panelet forsvant også når CDN-en ikke svarte.
 *
 * Resten finner panelet ut selv, og det er med vilje: at det samme skriptet
 * ser tre ulike ting skje er sterkere enn tre apper som forteller hver sin
 * historie.
 *
 * Avlyttingen av ledningen ligger **ikke** her, men i `felles/panel-avlytt.js`,
 * som må lastes som et vanlig skript først i `<head>`. Forklaringen står i den
 * fila, og den er verdt å lese: to av tre apper åpner strømmen sin før et
 * modulskript i det hele tatt har rukket å kjøre.
 */

/**
 * Byggefunksjonene fra Fristil. Appen sender dem inn i `startPanel`, og de to
 * funksjonene som skriver markup leser dem herfra.
 */
let fs = null

/**
 * Skriver ut alt en byggefunksjon ga, ikke bare klassen.
 *
 * `fs.button({ variant: "ghost" })` gir både `class` og `data-variant`, og
 * panelet skrev lenge av det siste for hånd ved siden av det første. Da er vi
 * tilbake til det dokumentasjonen advarer mot: endrer pakken attributtnavnet,
 * følger klassen med og attributtet blir stående.
 *
 * Appens egen klasse legges til på slutten, slik `med()` gjør i TanStack-appen.
 *
 * Boolske verdier følger `fs.setAttributes` i pakken: `true` blir et attributt
 * uten verdi, `false` og `undefined` blir ingenting. Uten det skrev hjelperen
 * `hidden="false"`, som er et sant boolsk attributt, og elementet ble skjult av
 * nettopp det som skulle vise det. Ingen byggefunksjon sender `false` i dag,
 * men `hidden` er blant de fire som sender `true`, og hjelperen er skrevet som
 * den generelle veien.
 */
function attributter({ class: klasse, ...resten }, egenKlasse) {
  const klasser = [klasse, egenKlasse].filter(Boolean).join(" ")
  const tegn = (verdi) =>
    String(verdi).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/"/g, "&quot;")

  return Object.entries({ class: klasser, ...resten })
    .filter(([, verdi]) => verdi !== false && verdi !== undefined)
    .map(([navn, verdi]) => (verdi === true ? `${navn}=""` : `${navn}="${tegn(verdi)}"`))
    .join(" ")
}

/** Hvor mange oppdateringer som huskes. Nok til å bla litt tilbake. */
const HUSKES = 12

/**
 * Områdene det er verdt å si fra om, mest spesifikke først.
 *
 * Rekkefølgen brukes to steder: `closest` går oppover og tar det første
 * treffet, og en pakke med mange endringer navngis etter den laveste
 * plasseringen i denne lista. Se `omradeFor`.
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

/** Navnet en endring får når den ikke traff noe av det over. */
const HELE_SIDEN = "siden"

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

let teller = 0

const tilstand = {
  teknikk: "",
  forklaring: "",
  hendelser: [],
  /** Om panelet står åpent. Huskes, se `MINNE`. */
  apen: false,
  /** Hendelsen brukeren har valgt i loggen, eller `null` for den nyeste. */
  valgt: null,
  /** Hvilken av de tre utgavene dette er. Markerer raden i tabellen. */
  utgave: "",
}

/** Avlyttingen, lagt på plass av `panel-avlytt.js` før alt annet. */
function ledningen() {
  return window.forstelinjaLedning ?? null
}

/** Elementet en endring hører til. En tekstendring peker på en tekstnode. */
function elementFor(node) {
  return node instanceof Element ? node : (node?.parentElement ?? null)
}

/**
 * Hvilket område en node hører til, som plassering i `OMRADER`.
 *
 * Gir `OMRADER.length` når ingenting traff, slik at «siden» alltid taper mot
 * et navngitt område når en pakke med endringer skal navngis.
 */
function omradeFor(node) {
  const element = elementFor(node)
  if (!element) return OMRADER.length
  for (let i = 0; i < OMRADER.length; i += 1) {
    if (element.closest(OMRADER[i][0])) return i
  }
  return OMRADER.length
}

function navnPaPlass(plass) {
  return OMRADER[plass]?.[1] ?? HELE_SIDEN
}

/** Om endringen er noe annet enn en oppdatering. Se `STOY`. */
function erStoy(node) {
  const element = elementFor(node)
  return Boolean(element) && STOY.some((velger) => element.closest(velger))
}

/**
 * De tre utgavene, og hvordan hver av dem henter data.
 *
 * Boksen står her og ikke i appene, av samme grunn som resten av panelet:
 * skrev hver app sin egen, ville sammenligningen vært tre påstander framfor
 * én tabell, og de ville gått fra hverandre første gang noen endret en av
 * dem.
 */
const REPO = "https://github.com/MarkusAugust/fristil-spill-demo"

const UTGAVER = [
  {
    id: "tanstack",
    navn: "TanStack Start",
    apner: "EventSource",
    ledning: "hele tilstanden som JSON",
    tegner: "i nettleseren, av React",
    kode: `${REPO}/tree/master/apper/tanstack`,
  },
  {
    id: "datastar",
    navn: "Datastar",
    apner: "fetch, lest som en strøm",
    ledning: "ferdige HTML-biter",
    tegner: "på serveren, og morfes inn",
    kode: `${REPO}/tree/master/apper/datastar`,
  },
  {
    id: "astro",
    navn: "Astro",
    apner: "EventSource",
    ledning: "en puls på noen hundre byte",
    tegner: "på serveren, som en ny side",
    kode: `${REPO}/tree/master/apper/astro`,
  },
]

const FELLES = [
  "Alle tre bruker Server-Sent Events, altså én vei: serveren skriver i en forbindelse som står åpen, og nettleseren lytter.",
  "Nettleseren snakker aldri med spilltjeneren. Hver appserver abonnerer selv, og vifter det ut til sine egne.",
  "EventSource kobler til igjen av seg selv når forbindelsen ryker. Datastar bruker fetch for å få sende med signalene sine, og må derfor passe på det selv.",
]

const tall = new Intl.NumberFormat("no-NO", { maximumFractionDigits: 1 })

function bytes(antall) {
  if (!antall) return ""
  return antall < 1024 ? `${antall} B` : `${tall.format(antall / 1024)} kB`
}

/**
 * Merkelappen over nyttelasten: hvordan det kom, og hva det var.
 *
 * Det er denne linja som svarer på spørsmålet panelet stiller. «SSE ·
 * datastar-patch-elements · HTML · 3,4 kB» og «SSE · tilstand · JSON · 1,2 kB»
 * er hele forskjellen mellom de to utgavene, sagt med det som faktisk gikk
 * over ledningen.
 */
function merkelapp(last) {
  if (!last) return ""
  return [last.transport, last.hendelse, last.format, bytes(last.bytes)]
    .filter(Boolean)
    .join(" · ")
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
  const antall = navigering?.transferSize ?? 0
  return {
    transport: "Helsidelasting",
    hendelse: null,
    format: "HTML",
    bytes: antall,
    tekst:
      "Serveren sendte hele dokumentet på nytt, og nettleseren bygget siden fra bunnen.\n" +
      (antall
        ? `${bytes(antall)} over ledningen, komprimering medregnet.`
        : "Størrelsen er ikke tilgjengelig i denne nettleseren."),
    url: location.pathname,
    nar: Date.now(),
  }
}

let husketimer = 0

/**
 * Skriver minnet, men ikke midt i en patch-storm.
 *
 * Kallet sto rett i `noter()`, altså i hver eneste mutasjonspakke. Det er en
 * synkron `JSON.stringify` og en synkron skriving av opptil 24 kB, og under
 * et faseskifte kommer pakkene tett. På en telefon spiste det hovedtråden i
 * nettopp det øyeblikket siden skulle oppdatere seg.
 *
 * Panelet er et feilsøkingsverktøy, og det skal aldri koste appen noe. Derfor
 * ett kall når det har vært stille i et halvt sekund.
 */
function husk() {
  clearTimeout(husketimer)
  husketimer = setTimeout(() => {
    try {
      sessionStorage.setItem(
        MINNE,
        JSON.stringify({ apen: tilstand.apen, hendelser: tilstand.hendelser }),
      )
    } catch {
      // Privat vindu, eller lagringen er slått av. Panelet virker uten minne.
    }
  }, 500)
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
    for (const hendelse of tilstand.hendelser) {
      teller = Math.max(teller, hendelse.id ?? 0)
    }
    if (tilstand.hendelser.length > 0) {
      teller += 1
      tilstand.hendelser.unshift({
        id: teller,
        omrade: "hele siden ble lastet på nytt",
        nar: Date.now(),
        antall: 1,
        last: sidelast(),
      })
      tilstand.hendelser.length = Math.min(tilstand.hendelser.length, HUSKES)
    }
  } catch {
    // Ødelagt eller utilgjengelig minne. Panelet begynner på nytt.
  }
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

/**
 * Noterer én pakke med endringer.
 *
 * `plass` er det mest spesifikke området i pakken, ikke området til den
 * første endringen. Forskjellen er ikke teoretisk: en streifendring på
 * `<body>` kom først i pakken og gjorde at hundre endringer inne i saken sto
 * oppført som «siden».
 */
function noter(plass, node, antallEndringer) {
  const omrade = navnPaPlass(plass)
  const forrige = tilstand.hendelser[0]
  const siste = ledningen()?.siste ?? null

  // Flere pakker rett etter hverandre hører til den samme oppdateringen. De
  // slås sammen, ellers fylles lista av tjue linjer som sier det samme.
  if (forrige && forrige.omrade === omrade && Date.now() - forrige.nar < 400) {
    forrige.antall += antallEndringer
    if (siste && siste !== forrige.last) forrige.last = siste
  } else {
    teller += 1
    tilstand.hendelser.unshift({
      id: teller,
      omrade,
      nar: Date.now(),
      antall: antallEndringer,
      last: siste,
    })
    tilstand.hendelser.length = Math.min(tilstand.hendelser.length, HUSKES)
  }

  /*
   * Markeringen og opptegningen gjøres bare når noen ser på.
   *
   * Er panelet lukket, er begge to ren kostnad: en klasse som legges på og
   * tas av på et element ute på brettet, og en full opptegning av en liste
   * ingen har åpnet. Loggen føres like fullt, så den står der når panelet
   * åpnes.
   */
  if (tilstand.apen) {
    lysOpp(elementFor(node))
    tegn()
  }
  husk()
}

/** Tabellen over de tre utgavene, med den du sitter i markert. */
function utgavetabell() {
  const rader = UTGAVER.map(
    (u) => `
        <tr${u.id === tilstand.utgave ? ' aria-current="true"' : ""}>
          <th scope="row">
            <a class="panel__lenke" href="${u.kode}" target="_blank" rel="noreferrer">${u.navn}</a>${u.id === tilstand.utgave ? ' <span class="panel__her">du er her</span>' : ""}
          </th>
          <td>${u.apner}</td>
          <td>${u.ledning}</td>
          <td>${u.tegner}</td>
        </tr>`,
  ).join("")

  return `
    <div class="${fs.table.scroll}" tabindex="0">
      <table ${attributter(fs.table(), "panel__tabell")}>
        <thead>
          <tr>
            <th>Utgave</th>
            <th>Åpner strømmen med</th>
            <th>Over ledningen</th>
            <th>Tegner</th>
          </tr>
        </thead>
        <tbody>${rader}</tbody>
      </table>
    </div>
    <ul ${attributter(fs.list(), "panel__felles")}>
      ${FELLES.map((linje) => `<li>${linje}</li>`).join("")}
    </ul>
    <p class="panel__felles">
      Trykk på navnet for å se koden bak hver utgave, eller
      <a class="panel__lenke" href="${REPO}" target="_blank" rel="noreferrer">hele demoen på GitHub</a>.
      Det samme spillet, tre ganger, med det samme designsystemet.
    </p>`
}

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
    <button type="button" ${attributter(fs.button(), "panelknapp")} aria-expanded="false"
            aria-controls="panel">Hva skjedde?</button>
    <section id="panel" ${attributter(fs.card(), "panel")} hidden aria-label="Hva skjedde">
      <div class="panel__del panel__del--hva">
        <h2 ${attributter(fs.heading({ size: "xs" }), "panel__tittel")}>
          Med hva
          <button type="button" ${attributter(fs.button({ variant: "ghost" }), "panel__hjelp")}
                  aria-expanded="false" aria-controls="panel-utgaver"
                  aria-label="Hvordan de tre utgavene henter data">?</button>
        </h2>
        <div id="panel-utgaver" class="panel__utgaver" hidden>
          ${utgavetabell()}
        </div>
        <p class="panel__merkelapp" hidden></p>
        <pre class="panel__nyttelast" tabindex="0"></pre>
      </div>
      <div class="panel__del panel__del--logg">
        <h2 ${attributter(fs.heading({ size: "xs" }), "panel__tittel")}>Hva som ble oppdatert</h2>
        <ol ${attributter(fs.list({ variant: "plain" }), "panel__logg")}></ol>
      </div>
      <div class="panel__del panel__del--hvordan">
        <h2 ${attributter(fs.heading({ size: "xs" }), "panel__tittel")}>Hvordan</h2>
        <p ${attributter(fs.paragraph(), "panel__teknikk")}></p>
        <p ${attributter(fs.paragraph({ size: "small" }), "panel__forklaring")}></p>
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
    // Loggen tegnes ikke mens panelet er lukket, så den tegnes her.
    if (tilstand.apen) tegn()
    husk()
  })

  const hjelp = vert.querySelector(".panel__hjelp")
  const utgaver = vert.querySelector("#panel-utgaver")

  hjelp.addEventListener("click", () => {
    const apen = utgaver.hidden
    utgaver.hidden = !apen
    hjelp.setAttribute("aria-expanded", String(apen))
  })

  // Klikk i loggen velger hvilken oppdatering nyttelasten hører til. Uten
  // dette kunne man bare se den siste, og en demo handler like ofte om den
  // forrige.
  panel.querySelector(".panel__logg").addEventListener("click", (e) => {
    const linje = e.target.closest?.("[data-hendelse]")
    if (!linje) return
    const id = Number(linje.dataset.hendelse)
    tilstand.valgt = tilstand.valgt === id ? null : id
    tegn()
  })

  return vert
}

let vertselement = null

/** Hendelsen nyttelasten vises for: den valgte, ellers den nyeste som har en. */
function vist() {
  if (tilstand.valgt !== null) {
    const valgt = tilstand.hendelser.find((h) => h.id === tilstand.valgt)
    if (valgt) return valgt
  }
  return tilstand.hendelser.find((h) => h.last) ?? null
}

function tegn() {
  if (!vertselement) return

  const valgt = vist()

  const logg = vertselement.querySelector(".panel__logg")
  logg.replaceChildren(
    ...tilstand.hendelser.map((h) => {
      const linje = document.createElement("li")
      linje.className = "panel__linje"
      /*
       * Bare linjer med en nyttelast er klikkbare.
       *
       * Sto `data-hendelse` på alle, kunne et klikk på en linje uten
       * nyttelast velge den, og ruta skrev «Ingenting har kommet over
       * ledningen ennå» selv om andre linjer hadde noe. Det er nøyaktig den
       * løgnen panelet er skrevet om for å bli kvitt.
       */
      if (h.last) {
        linje.dataset.hendelse = String(h.id)
        linje.setAttribute("aria-current", h === valgt ? "true" : "false")
      }

      const hode = document.createElement("span")
      hode.className = "panel__nar"
      const antall = h.antall > 1 ? ` · ${h.antall} endringer` : ""
      hode.textContent = `${new Date(h.nar).toLocaleTimeString("no-NO")} · ${h.omrade}${antall}`
      linje.append(hode)

      if (h.last) {
        const lapp = document.createElement("span")
        lapp.className = "panel__linjelapp"
        lapp.textContent = merkelapp(h.last)
        linje.append(lapp)
      }
      return linje
    }),
  )

  const lapp = vertselement.querySelector(".panel__merkelapp")
  const nyttelast = vertselement.querySelector(".panel__nyttelast")

  if (valgt?.last) {
    lapp.textContent = merkelapp(valgt.last)
    lapp.hidden = false
    nyttelast.textContent = valgt.last.tekst
    return
  }

  lapp.hidden = true
  nyttelast.textContent = ledningen()
    ? "Ingenting har kommet over ledningen ennå."
    : "Avlyttingen er ikke lastet. «/panel-avlytt.js» skal stå først i <head>."
}

/**
 * Starter panelet.
 *
 * `teknikk` er én setning om hvordan denne utgaven oppdaterer seg, og
 * `forklaring` er linja under, som sier hva det betyr i praksis. `fs` er
 * Fristils byggefunksjoner, hentet slik appen ellers henter dem. Alt annet
 * finner panelet ut selv.
 */
export function startPanel({ teknikk, forklaring, utgave, fs: byggere }) {
  if (vertselement) return

  /*
   * Panelet bygges ikke uten byggefunksjonene. Alternativet var markup uten en
   * eneste klasse, og det ser ut som CSS som ikke virker framfor som en
   * parameter som mangler.
   */
  if (!byggere) {
    console.warn(
      "Panelet ble ikke bygget: `fs` mangler i kallet til startPanel(). " +
        "Appen skal sende byggefunksjonene: startPanel({ …, fs }).",
    )
    return
  }
  fs = byggere

  tilstand.teknikk = teknikk
  tilstand.forklaring = forklaring
  tilstand.utgave = utgave ?? ""

  /*
   * Panelet skal si fra når avlyttingen mangler, framfor å påstå at ingenting
   * kom. Det var nettopp den stille varianten som gjorde at to av tre apper
   * sto og løy om sin egen trafikk.
   */
  if (!ledningen()) {
    console.warn(
      "Panelet: /panel-avlytt.js er ikke lastet, så «Med hva» kan ikke vise noe. " +
        "Skriptet skal stå først i <head>.",
    )
  }

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
      let beste = OMRADER.length
      let node = null
      let antall = 0

      for (const post of poster) {
        // Panelet skal ikke se seg selv, verken der det tegner eller der det
        // lyser opp.
        const mal = post.target
        if (mal instanceof Element && mal.closest(".panelvert")) continue
        if (mal.parentElement?.closest?.(".panelvert")) continue
        if (egenMarkering(post)) continue
        if (erStoy(mal)) continue

        antall += 1
        const plass = omradeFor(mal)
        if (node === null || plass < beste) {
          beste = plass
          node = mal
        }
      }

      if (antall > 0) noter(beste, node, antall)

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
