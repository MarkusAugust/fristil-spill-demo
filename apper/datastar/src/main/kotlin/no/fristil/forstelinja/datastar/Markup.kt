package no.fristil.forstelinja.datastar

/**
 * HTML-en, skrevet for hånd i Kotlin.
 *
 * Med vilje strengmaler og ikke kotlinx.html: poenget med demoen er at en
 * leser skal kunne sammenligne markupen med den samme skjermen i React og i
 * Astro. Da må den stå slik den havner i nettleseren, med klassene synlige.
 *
 * Her kalles ingen `fs.field()`. Kotlin kan ikke kalle en TypeScript-
 * funksjon, så serveren skriver klassene selv og lar komponentene gjøre
 * resten i nettleseren. Fra Fristil 0.10.0 står det ingen bevaringslister
 * her i det hele tatt: komponentene setter selv tilbake det en patch river
 * bort, og malen trenger ikke kjenne til attributtene.
 */

private fun String.trygg(): String =
  replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/**
 * Siden du får når spilltjeneren ikke svarer.
 *
 * Den henter seg selv på nytt, så den som står og venter under en utrulling
 * slipper å trykke. Ingen Fristil-komponenter her: poenget er at den virker
 * også når ingenting annet gjør det.
 */
fun venteside(): String =
  """
  <!doctype html>
  <html lang="nb">
  <head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta http-equiv="refresh" content="3">
  <title>Førstelinja</title>
  </head>
  <body style="font-family: system-ui, sans-serif; margin: 4rem auto; max-width: 32rem; padding: 0 1rem">
    <h1>Førstelinja</h1>
    <p>Sambandet til etaten er nede. Siden prøver igjen om tre sekunder.</p>
  </body>
  </html>
  """
    .trimIndent()

/**
 * Velkomsthilsenen, som forklarer hvorfor det finnes tre utgaver.
 *
 * Den står i `side()` og ikke i brettet, altså utenfor det serveren patcher.
 * Lå den inne i `#sak`, ville `open` kommet tilbake ved hver patch, og
 * hilsenen spratt opp igjen hvert par minutt for den som sitter og leser
 * innmeldingsskjemaet.
 */
fun velkomst(tema: String? = null): String =
  """
  <fs-dialog id="velkomst" open>
    <dialog class="fs-dialog velkomst" aria-labelledby="velkomst-tittel"
            open>
      <div class="velkomst__topp">
        <h2 class="fs-dialog__title" id="velkomst-tittel">Velkommen til Førstelinja</h2>
      </div>

      <div class="fs-dialog__body">
        <p>
          Etaten for alminnelige søknader har de siste årene fått et økende
          antall henvendelser om hvilket rammeverk saksbehandlingsløsningen er
          skrevet i. Vi tar slike tilbakemeldinger på alvor.
        </p>
        <p>
          Løsningen leveres derfor i tre utgaver, skreddersydd til hvert sitt
          rammeverk: TanStack Start, Datastar og Astro. Du sitter nå i
          <strong>$APPNAVN</strong>-utgaven.
        </p>
        <p>Vi er trygge på at du vil merke forskjellen.</p>

        <h3 class="fs-heading velkomst__valg" data-size="xs">Velg din foretrukne utgave</h3>
        <ul class="fs-list velkomst__liste" data-variant="plain">
          ${UTGAVER.joinToString("\n") { utgave ->
            if (utgave.navn == APPNAVN)
              """
              <li>
                <span class="velkomst__her">
                  <strong>${utgave.navn}</strong>
                  <span class="velkomst__om">${utgave.rammeverk}</span>
                  <span class="fs-badge" data-color="success">Du er her</span>
                </span>
              </li>"""
            else
              """
              <li>
                <a class="velkomst__lenke" href="${lenkeTilUtgave(utgave.adresse, null, tema)}">
                  <strong>${utgave.navn}</strong>
                  <span class="velkomst__om">${utgave.rammeverk}</span>
                </a>
              </li>"""
          }}
        </ul>

        <p class="velkomst__fotnote">
          Alle tre er bygget med det samme designsystemet, og det er nettopp
          poenget: du skal ikke merke forskjellen. Bytt utgave, og skjermen er
          den samme.
        </p>
      </div>

      <form method="dialog" class="fs-dialog__footer">
        <!-- `autofocus` her, ellers tar `showModal()` den første lenken i
             lista, og et tilfeldig Enter sender deg til en annen utgave. -->
        <button class="fs-button" value="lukk" autofocus>Jeg merker nok forskjellen</button>
      </form>
    </dialog>
  </fs-dialog>
  """
    .trimIndent()

/** Hele siden, første gang. */
fun side(
  tilstand: Tilstand,
  spillerNavn: String?,
  kommuner: List<String> = emptyList(),
  tema: String? = null,
  spillerId: String? = null,
): String {
  val stilark = STILARK.joinToString("\n    ") { """<link rel="stylesheet" href="$it">""" }
  val imports = KOMPONENTER.joinToString("\n      ") { (fil, fn) -> """import { $fn } from "$fil"; $fn();""" }
  // Temaet kommer fra kapselen, så serveren kan skrive det selv. Da blinker
  // ikke siden lyst for den som har valgt mørkt, og valget følger med til de
  // to andre utgavene, som leser den samme kapselen.
  val temaAttributt = if (tema == "light" || tema == "dark") """ data-theme="$tema"""" else ""

  return """
    <!doctype html>
    <html lang="nb"$temaAttributt>
    <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Førstelinja · Datastar og Kotlin</title>
    $stilark
    <!-- Temaet ligger i sitt eget lag, `fristil-tema`, erklært etter
         `fristil`, så det vinner over komponentenes standardverdier uansett
         rekkefølge. brett.css er usortert og vinner over begge. -->
    <link rel="stylesheet" href="/tema.css">
    <link rel="stylesheet" href="/brett.css">
    <!-- Avlyttingen, som må kjøre før Datastars bundle under. Et vanlig
         skript kjører mens dokumentet parses, et modulskript først etterpå,
         så rekkefølgen her er hele forskjellen på om panelet ser strømmen. -->
    <script src="/panel-avlytt.js"></script>
    <script type="module" src="$DATASTAR_CDN"></script>

    <script type="module">
      $imports
    </script>
    </head>
    <!-- Strømmen fra serveren, åpnet med `data-init`.
         Uten den finnes endepunktet, men ingen abonnerer på det, og
         skjermen oppdaterer seg bare når du selv gjør noe. `data-on:load`
         er ikke et attributt i Datastar 1.0.4, og ble ignorert i stillhet,
         uten en eneste feil i konsollen. -->
    <body data-init="@get('/hendelser')" data-server-na="${tilstand.naMs}">
    <!-- Sambandslinja eier sitt eget innhold, og serveren har ingenting å
         sende for den. `data-ignore-morph` hindrer at en patch river bort en
         melding midt i visningen. -->
    <fs-connection-status class="fs-connection-status"
      offline-text="Sambandet til etaten er nede"
      online-text="Sambandet er tilbake"
      data-ignore-morph></fs-connection-status>

    ${if (spillerNavn == null) velkomst(tema) else ""}

    <!-- Merket serveren skriver når sambandet ryker. Det er skjult, og
         finnes bare for at skriptet skal ha noe å lytte på: en komponent
         som eier sitt eget innhold kan ikke patches. -->
    <div id="samband" hidden data-nede="false"></div>

    <header class="topplinje">
      <div class="topplinje__innhold stamme">
        ${topp(tilstand)}
        ${utgavevelger(spillerId, tema)}
        ${temavelger()}
      </div>
    </header>

    <main class="brett">
      <div class="stamme">
        ${brett(tilstand, spillerNavn, kommuner)}
      </div>
    </main>

    <script>
      // Nedtellingen regnes ut i nettleseren fra et absolutt tidspunkt.
      // Serveren sender aldri «30 sekunder igjen», for da ville et forsinket
      // bud flyttet fristen, og de tre appene kommet i utakt.
      // Temavelgeren. Fristil bytter tema av seg selv etter
      // `prefers-color-scheme`, og `data-theme` på rota overstyrer. Ingen
      // attributt betyr «det maskinen sier», som er standarden.
      //
      // Valget ligger i en kapsel, ikke i `localStorage`, så serveren kan
      // skrive attributtet når den tegner siden. Da er det ingenting å rette
      // opp etterpå, og ingen lysglimt. Kapselen deles med de to andre
      // utgavene, så valget følger med når du bytter.
      /*
       * Lenkene til de to andre utgavene bærer valget ditt videre.
       *
       * Serveren skriver dem med `?tema=` ved sidelasting, og det holder
       * helt til du bytter tema etterpå: da står lenkene igjen med det
       * gamle valget, og du fikk systemtemaet i den andre utgaven.
       * Utgavevelgeren ligger dessuten utenfor området serveren patcher, så
       * den blir stående til neste fulle sidelasting. Kapselen gjelder bare
       * for sitt eget domene i drift, så lenka er det eneste som kan bære
       * valget over.
       */
      const merkLenkene = (verdi) => {
        for (const lenke of document.querySelectorAll(".utgavevelger__lenke, .velkomst__lenke")) {
          const adresse = new URL(lenke.href)
          if (verdi === "system") adresse.searchParams.delete("tema")
          else adresse.searchParams.set("tema", verdi)
          lenke.href = adresse.toString()
        }
      }

      const settTema = (verdi) => {
        // `SameSite=None` på https: i skallet står utgavene i hver sin ramme,
        // på hvert sitt domene, og en `Lax`-kapsel sendes ikke derfra.
        const tvers =
          location.protocol === "https:" ? "SameSite=None; Secure" : "SameSite=Lax"
        document.cookie = "forstelinja-tema=" + verdi + "; Path=/; Max-Age=31536000; " + tvers
        if (verdi === "system") document.documentElement.removeAttribute("data-theme")
        else document.documentElement.setAttribute("data-theme", verdi)
        merkLenkene(verdi)
      }
      const valgtTema =
        document.cookie.match(/(?:^|;\s*)forstelinja-tema=([^;]*)/)?.[1] ?? "system"
      for (const knapp of document.querySelectorAll('input[name="tema"]')) {
        knapp.checked = knapp.value === valgtTema
        knapp.addEventListener("change", () => settTema(knapp.value))
      }

      // Sambandslinja. Datastar sier fra om sine egne kall på
      // `datastar-fetch`, og det er den forbindelsen spillet lever av. Uten
      // dette ville komponenten bare sett `navigator.onLine`, som ikke
      // merker at spilltjeneren er nede.
      //
      // Elementet slås opp ved hver hendelse, ikke én gang: komponentene
      // registreres i en modul, og moduler kjører etter denne blokka. Ved
      // oppstart er `<fs-connection-status>` derfor et vanlig element uten
      // metodene ennå.
      // Serveren sier fra over strømmen når spilltjeneren bak er borte.
      // Merket morfes i stedet for å byttes ut, så noden består og
      // observatøren henger med.
      const merke = document.getElementById("samband")
      if (merke) {
        new MutationObserver(() => {
          const samband = document.querySelector("fs-connection-status")
          if (typeof samband?.reportFailure !== "function") return
          if (merke.dataset.nede === "true") samband.reportFailure()
          else samband.reportSuccess()
        }).observe(merke, { attributes: true, attributeFilter: ["data-nede"] })
      }

      document.addEventListener("datastar-fetch", (e) => {
        const samband = document.querySelector("fs-connection-status")
        const type = e.detail?.type
        if (typeof samband?.reportFailure !== "function") return

        if (type === "retrying" || type === "retries-failed" || type === "error") {
          samband.reportFailure()
        } else if (type === "started") {
          // Og ikke `finished`: en SSE-strøm som står åpen blir aldri
          // ferdig, så `finished` ville bare kommet når noe var galt.
          samband.reportSuccess()
        }
      })

      // Klassen, ikke en id: nedtellingen står både i toppen og i panelet
      // når omgangen er over. Med samme id på begge fant getElementById
      // bare den første, og panelet ble stående med « … sekunder».
      // Fristen er et absolutt tidspunkt fra serveren, og maskinen her kan gå
      // feil. Avviket regnes ut én gang, mot serverens egen klokke slik den
      // sto da siden ble tegnet, og legges til i hver avlesning.
      const serverNa = Number(document.body.dataset.serverNa ?? 0)
      const avvik = serverNa > 0 ? serverNa - Date.now() : 0

      /*
       * Vakthunden: en strøm kan dø uten at noen får vite det.
       *
       * Datastar henter hendelsesstrømmen med `fetch` og leser den som en
       * strøm, og en lesing som aldri kaster gir aldri en gjenoppkobling.
       * Sover telefonen, bytter nettet, eller kutter en mellomtjener
       * forbindelsen, står siden igjen med en klokke som teller til null og
       * ingenting som skjer. Det er den ene feilen som ser ut som at spillet
       * har stoppet, og det var nettopp slik den viste seg.
       *
       * Vi kan ikke spørre strømmen om den lever. Men vi vet noe annet:
       * serveren flytter fristen ved hvert faseskifte, og en frist som ikke
       * har flyttet seg lenge etter at den gikk ut, betyr at ingen patch har
       * kommet. Da henter vi siden på nytt, som er den samme veien inn som
       * en vanlig lasting.
       *
       * De to andre utgavene trenger den ikke: `EventSource` kobler til
       * igjen av seg selv, og Astro henter en ny side når runden er en annen
       * enn den siden ble tegnet med.
       */
      const NAADE_SEKUNDER = 15
      let sisteFrist = 0
      let overtid = 0

      const vakthund = (frist, igjen) => {
        if (frist !== sisteFrist) {
          sisteFrist = frist
          overtid = 0
          return
        }
        if (igjen > 0 || document.visibilityState !== "visible") return

        overtid += 1
        if (overtid === NAADE_SEKUNDER) {
          // Si fra før vi henter siden, så det ikke ser ut som et tilfeldig
          // hopp. Linja er den samme som brukes når spilltjeneren er borte.
          const samband = document.querySelector("fs-connection-status")
          if (typeof samband?.reportFailure === "function") samband.reportFailure()
        }
        if (overtid >= NAADE_SEKUNDER + 2) location.reload()
      }

      setInterval(() => {
        for (const el of document.querySelectorAll(".nedtelling")) {
          const igjen = Math.max(0, Math.round((Number(el.dataset.frist) - (Date.now() + avvik)) / 1000))
          // Klokka i toppen står som minutter og sekunder. Inne i en setning
          // («starter om 12 sekunder») er tallet alene det som leses, og da
          // må enheten bøyes: «1 sekunder» er ikke norsk.
          el.textContent =
            "klokke" in el.dataset
              ? Math.floor(igjen / 60) + ":" + String(igjen % 60).padStart(2, "0")
              : igjen

          // Bare klokka i toppen styrer vakthunden. Den står der i hver fase,
          // og teksten inne i en setning finnes bare i noen av dem.
          if ("klokke" in el.dataset) vakthund(Number(el.dataset.frist), igjen)

          const enhet = el.parentElement?.querySelector(".nedtelling-enhet")
          if (enhet) enhet.textContent = igjen === 1 ? "sekund" : "sekunder"

          // Fargen går gradvis mot rødt. Den holder seg hvit til halve fristen
          // er gått, og blir så mer og mer rød. Et fast omslagspunkt ville
          // gitt et sjokk i stedet for et press.
          const lengde = Number(el.dataset.lengde)
          if (lengde > 0) {
            const igjenDel = Math.min(1, Math.max(0, (igjen * 1000) / lengde))
            const roedt = Math.round(Math.max(0, 1 - igjenDel * 2) * 100)
            el.style.color =
              "color-mix(in oklab, var(--spill-frist-slutt) " +
              roedt +
              "%, var(--spill-frist-start))"
          }

          // Under ti sekunder begynner klokka å riste, og rister mer for
          // hvert sekund. Styrken er et tall skriptet setter; utslaget og
          // farten står i CSS-en, sammen med sperren for den som har bedt om
          // mindre bevegelse.
          const rist = igjen > 0 && igjen <= 10 ? (10 - igjen) / 9 : 0
          el.style.setProperty("--spill-rist", rist.toFixed(2))
          el.toggleAttribute("data-rister", rist > 0)
        }
      }, 250)
    </script>

    <!-- Panelet som viser hva som ble oppdatert. Felles fil, egen teknikk. -->
    <script type="module">
      import { startPanel } from "/panel.js"

      startPanel({
        teknikk: "Serveren sendte en ferdig HTML-bit, og Datastar morfet den inn.",
        forklaring:
          "Ingen JSON og ingen komponenter i nettleseren: serveren har alt tegnet skjermen, og Datastar bytter ut bare de attributtene og nodene som ble annerledes.",
        utgave: "datastar",
      })
    </script>
    </body>
    </html>
  """
    .trimIndent()
}

/**
 * Topplinja: hvem som eier tjenesten, hvor i omgangen vi er, og hvor lenge
 * det er igjen. Patches sammen med brettet.
 *
 * Temavelgeren står utenfor, i `side()`. Den er brukerens valg og har ingen
 * plass i noe serveren sender på nytt.
 */
fun topp(tilstand: Tilstand): String {
  val sisteRunde = tilstand.rundeNr >= tilstand.runderTotalt
  val fase =
    when (tilstand.fase) {
      "runde" -> "Runde ${tilstand.rundeNr} av ${tilstand.runderTotalt}"
      "oppgjor" -> "Oppgjør"
      else -> "Omgangen er slutt"
    }
  val merkelapp =
    when {
      tilstand.fase == "runde" -> "Frist"
      tilstand.fase == "oppgjor" && sisteRunde -> "Sluttstilling"
      tilstand.fase == "oppgjor" -> "Neste sak"
      else -> "Ny omgang"
    }

  return """
    <div id="topp" class="topplinje__hoved">
      <div class="topplinje__merke">
        <svg class="topplinje__emblem" viewBox="0 0 24 24" aria-hidden="true" fill="none"
             stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M6 2.75h6.5l5 5v13.5a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1V3.75a1 1 0 0 1 1-1Z"/>
          <path d="M12.5 2.75v5.5h5"/>
          <path d="M8.5 13.5h7M8.5 17h4.5"/>
        </svg>
        <div class="topplinje__ord">
          <h1 class="topplinje__navn">Førstelinja</h1>
          <span class="topplinje__etat">Etaten for alminnelige søknader</span>
        </div>
      </div>

      <p class="topplinje__fase">$fase</p>

      <p class="topplinje__klokke">
        <span class="topplinje__merkelapp">$merkelapp</span>
        <span class="nedtelling" data-klokke data-frist="${tilstand.fristMs}"
              data-lengde="${tilstand.faseLengdeMs}" data-preserve-attr="style data-rister">–</span>
      </p>

      <span class="topplinje__stack">Kotlin · $APPNAVN</span>

      ${if (tilstand.meg == null) "" else """
      <p class="topplinje__meg">
        <span class="fs-avatar" data-size="small" aria-hidden="true">${initialer(tilstand.meg.navn)}</span>
        <span class="topplinje__navnet">${tilstand.meg.navn.trygg()}</span>
      </p>"""}
    </div>
  """
    .trimIndent()
}

/**
 * Initialene i avataren.
 *
 * Avataren er `aria-hidden`: navnet står ved siden av, og to bokstaver lest
 * opp foran det samme navnet er støy.
 */
private fun initialer(navn: String): String =
  navn
    .trim()
    .split(Regex("\\s+"))
    .filter { it.isNotBlank() }
    .take(2)
    .joinToString("") { it.first().uppercase() }
    .ifBlank { "?" }
    .trygg()

/**
 * Bytt utgave underveis.
 *
 * Står utenfor det serveren patcher, som temavelgeren. Lenker og ikke
 * knapper: det er tre adresser, og en lenke er det HTML har for det.
 */
fun utgavevelger(spillerId: String? = null, tema: String? = null): String =
  """
  <nav class="utgavevelger" aria-label="Utgave">
    ${UTGAVER.joinToString("\n") { utgave ->
      if (utgave.navn == APPNAVN)
        """<span class="utgavevelger__her" aria-current="page">${utgave.navn}</span>"""
      else
        """<a class="utgavevelger__lenke" href="${lenkeTilUtgave(utgave.adresse, spillerId, tema)}">${utgave.navn}</a>"""
    }}
  </nav>
  """
    .trimIndent()

/**
 * Lyst, mørkt eller det maskinen sier.
 *
 * Fristil bytter tema av seg selv etter `prefers-color-scheme`, og
 * `data-theme` på rota overstyrer. Valget er brukerens og lagres i
 * nettleseren; serveren vet ingenting om det, og rører det aldri.
 */
fun temavelger(): String =
  """
  <fieldset class="fs-toggle-group temavelger">
    <legend class="fs-sr-only">Fargetema</legend>
    <label class="fs-toggle-group__option">
      <input type="radio" name="tema" value="light"> Lyst
    </label>
    <label class="fs-toggle-group__option">
      <input type="radio" name="tema" value="system" checked> System
    </label>
    <label class="fs-toggle-group__option">
      <input type="radio" name="tema" value="dark"> Mørkt
    </label>
  </fieldset>
  """
    .trimIndent()

/**
 * Saken, eller det som står i stedet for den.
 *
 * Eget område med egen id, slik at serveren kan sende tavla for seg uten å
 * røre skjemaet brukeren står i.
 */
fun saksomrade(
  tilstand: Tilstand,
  spillerNavn: String?,
  kommuner: List<String>,
  feil: List<Feil>,
): String =
  if (spillerNavn == null) blimed(tilstand)
  else
    when (tilstand.fase) {
      "runde" -> runde(tilstand, kommuner, feil)
      "oppgjor" -> oppgjor(tilstand)
      else -> slutt(tilstand)
    }

/** Hele brettet. Sendes bare når runden skifter. */
fun brett(
  tilstand: Tilstand,
  spillerNavn: String?,
  kommuner: List<String> = emptyList(),
  feil: List<Feil> = emptyList(),
): String =
  """
  <div id="brett" class="brett__innhold">
    <div id="sak" class="brett__hoved">${saksomrade(tilstand, spillerNavn, kommuner, feil)}</div>
    <aside id="tavle" class="fs-card kort tavle">${tavle(tilstand)}</aside>
    ${resultatdialog(tilstand)}
  </div>
  """
    .trimIndent()

/**
 * Skjemaet for å bli med.
 *
 * Her står hele kontrakten `fs.field()` ville skrevet, for hånd: klassene,
 * `for` og `id` som binder ledetekst til felt, og bevaringslistene.
 */
fun blimed(tilstand: Tilstand? = null): String {
  val runder = tallord(tilstand?.runderTotalt ?: 4)
  val sekunder = ((tilstand?.rundeLengdeMs ?: 120_000) / 1000).toInt()
  val tid =
    when {
      sekunder % 60 != 0 -> "$sekunder sekunder"
      sekunder == 60 -> "ett minutt"
      sekunder >= 120 -> "${tallord(sekunder / 60)} minutter"
      else -> "$sekunder sekunder"
    }

  return """
  <section class="fs-card kort blimed">
    <h2 class="fs-heading" data-size="m">Møt på vakt</h2>
    <p class="fs-paragraph blimed__ingress">
      Du er saksbehandler i førstelinja. $runder saker, $tid på hver.
      Finn riktig utfall, riktig hjemmel, riktig kommune, og feilen søkeren
      håpet du ikke så.
    </p>

    <!-- Et helt vanlig skjema, ikke en Datastar-innsending.
         Å møte på vakt er en navigering: kapselen må være satt før
         hendelsesstrømmen åpnes, ellers vet strømmen ikke hvem du er og
         dytter dette skjemaet tilbake over spillet. Det virker dessuten
         uten JavaScript i det hele tatt. -->
    <form method="post" action="/bli-med">
      <!-- Bare struktur. Ingen id-er, ingen `for`, ingen
           `aria-describedby`, og ingen bevaringsliste:
           komponenten setter koblingen i nettleseren, ser at en patch har
           revet den bort, og setter den tilbake. Malen slipper dermed å
           kjenne til attributtene i det hele tatt, og den kan ikke gå stille
           i stykker av at Fristil endrer hva komponenten setter. -->
      <fs-field>
        <label class="fs-label">Navnet ditt</label>
        <input class="fs-input" name="navn" type="text" required>
        <p class="fs-help-text">Vises på tavla for alle.</p>
      </fs-field>

      <button class="fs-button skjema__send" type="submit">Begynn vakta</button>
    </form>
  </section>
  """
    .trimIndent()
}

/** Små tall skrives med bokstaver i norsk brødtekst. */
private fun tallord(n: Int) =
  when (n) {
    1 -> "én"
    2 -> "to"
    3 -> "tre"
    4 -> "fire"
    5 -> "fem"
    else -> n.toString()
  }

/** Selve saksbehandlingen. */
fun runde(tilstand: Tilstand, kommuner: List<String>, feil: List<Feil> = emptyList()): String {
  val sak = tilstand.sak
  val harSvart = tilstand.meg?.harSvart == true

  return """
    ${sakskort(sak)}
    ${if (harSvart) kvittering(tilstand) else vedtakskort(tilstand, kommuner, feil)}
  """
    .trimIndent()
}

/**
 * Saken slik den ligger på bordet: nummer, tittel, ingress og selve
 * søknaden, med opplysningene om søkeren i en egen fane.
 *
 * Fella ligger i opplysningene, ikke i teksten, så begge fanene må leses.
 * Det er hele oppgaven.
 */
private fun sakskort(sak: SakUt): String =
  """
  <section class="fs-card kort sakskort">
    <p class="sakskort__stempel">
      <span class="fs-tag">Sak ${sak.id.trygg()}</span>
      <span class="sakskort__status">Til behandling</span>
    </p>

    <h2 class="fs-heading sakskort__tittel" data-size="m">${sak.tittel.trygg()}</h2>
    <p class="fs-paragraph sakskort__ingress">${sak.sammendrag.trygg()}</p>

    <!-- Fanene: serveren skriver roller, kobling og hvilken som er valgt.
         Komponenten flytter valget når brukeren blar, og derfor står
         `aria-selected` og `tabindex` i bevaringslista. -->
    <fs-tabs class="sakskort__faner">
      <div class="fs-tabs__list" role="tablist" aria-label="Saken">
        <button id="f-0" role="tab" type="button" aria-selected="true" aria-controls="p-0"
                tabindex="0">Søknaden</button>
        <button id="f-1" role="tab" type="button" aria-selected="false" aria-controls="p-1"
                tabindex="-1">Søkeren</button>
      </div>

      <div id="p-0" class="fs-tabs__panel" role="tabpanel" aria-labelledby="f-0" tabindex="0">
        <p class="fs-paragraph sakskort__tekst">${sak.tekst.trygg()}</p>
        <p class="sakskort__signatur">Med vennlig hilsen<br>${sak.soker.navn.trygg()}</p>
      </div>

      <div id="p-1" class="fs-tabs__panel" role="tabpanel" aria-labelledby="f-1" tabindex="0"
           hidden>
        <table class="fs-table">
          <tbody>
            <tr><th scope="row">Navn</th><td>${sak.soker.navn.trygg()}</td></tr>
            <tr><th scope="row">Fødselsdato</th><td>${sak.soker.fodselsdato.trygg()}</td></tr>
            <tr><th scope="row">Kommune</th><td>${sak.soker.kommune.trygg()}</td></tr>
            <tr><th scope="row">E-post</th><td>${sak.soker.epost.trygg()}</td></tr>
          </tbody>
        </table>
      </div>
    </fs-tabs>
  </section>
  """
    .trimIndent()

/**
 * Feilmeldingen som står ved feltet.
 *
 * Feiloppsummeringen sier hva som er galt i en liste på toppen, men den som
 * har fulgt en lenke ned til feltet skal se hvorfor rammen er rød. `id`-en
 * kobles inn i `aria-describedby` av `<fs-field>` når serveren ikke skriver
 * den selv; her skriver den den.
 */
private fun beskrivesAv(feil: List<Feil>, felt: String, id: String) =
  if (feil.any { it.felt == felt }) """aria-describedby="$id"""" else ""

private fun feilmelding(feil: List<Feil>, felt: String, id: String): String {
  val melding = feil.firstOrNull { it.felt == felt } ?: return ""
  return """<p class="fs-error-text" id="$id">${melding.melding.trygg()}</p>"""
}

/**
 * Hvor mange som har levert på saken som ligger på bordet.
 *
 * Uten den vet du ikke om det er deg de andre venter på, og en runde som
 * brått tar slutt fordi de var ferdige, ser ut som en feil.
 *
 * Den står på tavla og ikke i skjemakortet, fordi det er tavla serveren
 * patcher når noen leverer. I skjemakortet ville tallet stått stille til
 * runden skiftet.
 *
 * Tallene leses av tavla, ikke av tellingen fra spilltjeneren: den teller
 * bare dem som var med da saken kom på bordet, og i den første runden etter
 * at du meldte deg på er det ingen. Alle på tavla kan svare, og det er dem
 * du ser.
 */
private fun svartSaLangt(tilstand: Tilstand): String {
  val paVakt = tilstand.tavle.size
  if (paVakt <= 1) return ""

  val levert = tilstand.tavle.count { it.harSvart }
  val alle = levert >= paVakt

  return """
    <p class="vedtakskort__svart" data-alle="$alle">
      ${if (alle) "Alle har levert. Oppgjøret kommer straks."
        else "$levert av $paVakt saksbehandlere har levert."}
    </p>
  """
    .trimIndent()
}

/** Skjemaet saksbehandleren fyller ut. */
private fun vedtakskort(tilstand: Tilstand, kommuner: List<String>, feil: List<Feil>): String {
  val hjemler =
    tilstand.hjemler.joinToString("\n") {
      """<option value="${it.kode.trygg()}">${it.kode.trygg()} ${it.tekst.trygg()}</option>"""
    }
  val ugyldigHjemmel =
    if (feil.any { it.felt == "hjemmel" }) "aria-invalid=\"true\" data-state=\"invalid\"" else ""
  val ugyldigKommune =
    if (feil.any { it.felt == "kommune" }) "aria-invalid=\"true\" data-state=\"invalid\"" else ""
  val ugyldigFelle =
    if (feil.any { it.felt == "felle" }) "aria-invalid=\"true\" data-state=\"invalid\"" else ""
  // Et feltsett er ikke en kontroll, så det er `data-state` alene som gir
  // ledeteksten farge. `aria-invalid` hører på selve radioknappene.
  val ugyldigUtfall = if (feil.any { it.felt == "v-innvilget" }) "data-state=\"invalid\"" else ""
  val ugyldigRadio = if (feil.any { it.felt == "v-innvilget" }) "aria-invalid=\"true\"" else ""

  val hjemmelforklaringer =
    tilstand.hjemler.joinToString("\n") {
      "<li><strong>${it.kode.trygg()}</strong> ${it.tekst.trygg()}</li>"
    }

  val kommunevalg =
    kommuner.withIndex().joinToString("\n") { (nr, navn) ->
      "<li class=\"fs-suggestion__option\" id=\"kommune-option-$nr\" role=\"option\" " +
        "aria-selected=\"false\">${navn.trygg()}</li>"
    }

  val feiloppsummering =
    if (feil.isEmpty()) {
      """<fs-error-summary class="fs-error-summary" role="alert" tabindex="-1" id="feilboks" hidden></fs-error-summary>"""
    } else {
      """
      <!-- Serveren skriver hele boksen, også overskriften og lista.
           Komponenten flytter bare fokus hit og tar klikkene på lenkene. -->
      <fs-error-summary class="fs-error-summary" role="alert" tabindex="-1" id="feilboks">
        <h3 class="fs-error-summary__title">Du må rette ${feil.size} feil</h3>
        <ul class="fs-list">
          ${feil.joinToString("\n") { """<li><a href="#${it.felt}">${it.melding.trygg()}</a></li>""" }}
        </ul>
      </fs-error-summary>"""
    }

  return """
    <section class="fs-card kort vedtakskort">
      <h2 class="fs-heading" data-size="s">Ditt vedtak</h2>
      <p class="vedtakskort__ingress">
        Fire spørsmål, ${tilstand.poeng.fullPott} poeng. Du kan svare én gang.
      </p>

      $feiloppsummering

      <form class="skjema" data-on:submit="@post('/svar')">
        <!-- `.fs-radio-row` er raden som holder knappen og teksten ved
             siden av hverandre. Uten den ligger de to inntil hverandre uten
             luft, for `.fs-label` er et vanlig blokkelement. -->
        <fieldset class="fs-fieldset" $ugyldigUtfall>
          <legend class="fs-legend">Utfall</legend>
          <div class="fs-radio-row">
            <input class="fs-radio" type="radio" id="v-innvilget" name="vedtak" value="innvilget"
                   data-bind:vedtak $ugyldigRadio>
            <label class="fs-label" for="v-innvilget">Innvilget</label>
          </div>
          <div class="fs-radio-row">
            <input class="fs-radio" type="radio" id="v-avslatt" name="vedtak" value="avslatt"
                   data-bind:vedtak $ugyldigRadio>
            <label class="fs-label" for="v-avslatt">Avslått</label>
          </div>
        </fieldset>

        <!-- Id-en står her fordi feiloppsummeringen lenker til feltet.
             Skal noe annet peke på et element, må serveren navngi det.
             Ellers lar vi komponenten finne på id-en. -->
        <fs-field>
          <label class="fs-label" for="hjemmel">Hjemmel</label>
          <!-- `data-picker="styled"` ber nettleseren tegne lista inne i
               siden, med systemets egne farger. Uten den er lista et vindu
               fra operativsystemet: ingen CSS når inn i den, den følger
               maskinens tema og ikke sidens, og i Chromes mobilemulering
               havner den på feil sted og i feil størrelse. Firefox har det
               ikke ennå, og får da sin egen liste, altså det feltet hadde
               før. -->
          <select class="fs-select" data-picker="styled" id="hjemmel" name="hjemmel"
                  data-bind:hjemmel
                  ${beskrivesAv(feil, "hjemmel", "hjemmel-feil")} $ugyldigHjemmel>
            <option value="">Velg hjemmel</option>
            $hjemler
          </select>
          ${feilmelding(feil, "hjemmel", "hjemmel-feil")}
        </fs-field>

        <!-- Hjelpen til hjemlene. Serveren skriver koblingen mellom
             knappen og panelet; komponenten plasserer det, lukker det, og
             setter tilbake at det er åpent når en patch river det bort.
             Serveren vet jo ikke om vinduet er åpent. -->
        <fs-popover class="hjelpelenke" placement="bottom-start">
          <button type="button" class="fs-button" data-variant="ghost"
                  aria-expanded="false" aria-controls="hjemmelhjelp">Hva betyr hjemlene?</button>
          <div class="fs-popover" id="hjemmelhjelp" popover="manual">
            <ul class="fs-list">
              $hjemmelforklaringer
            </ul>
          </div>
        </fs-popover>

        <!-- Kommunefeltet. Serveren sender hele lista, komponenten
             filtrerer mens du skriver og tar piltastene. Finner du ikke
             kommunen, er det fordi den ikke finnes lenger. -->
        <fs-suggestion>
          <label class="fs-label" for="kommune">Bekreft kommunen søkeren hører til</label>
          <div class="fs-suggestion__field">
            <input class="fs-input" id="kommune" name="kommune" type="text" role="combobox"
                   autocomplete="off" aria-autocomplete="list" aria-expanded="false"
                   aria-controls="kommune-list"
                   aria-describedby="kommune-hjelp kommune-status${if (feil.any { it.felt == "kommune" }) " kommune-feil" else ""}"
                   data-bind:kommune
                   $ugyldigKommune>
            <ul class="fs-suggestion__list" id="kommune-list" role="listbox" hidden>
              $kommunevalg
            </ul>
            <p class="fs-suggestion__empty" hidden>Ingen treff. Finnes kommunen fortsatt?</p>
            <span class="fs-sr-only" id="kommune-status" role="status" aria-live="polite"
                  data-ignore-morph></span>
          </div>
          <p class="fs-help-text" id="kommune-hjelp">Begynn å skrive, så kommer forslagene. Finner du den ikke, la feltet stå tomt.</p>
          ${feilmelding(feil, "kommune", "kommune-feil")}
        </fs-suggestion>

        <!-- Feiloppsummeringen lenker hit, så serveren navngir feltet, og
             skriver da hele koblingen selv. Da har komponenten ingenting å
             legge til, og morfingen ingenting å ta bort. -->
        <fs-field>
          <label class="fs-label" for="felle">Er noe feil i søknaden?</label>
          <select class="fs-select" data-picker="styled" id="felle" name="felle" data-bind:felle
                  aria-describedby="felle-hjelp${if (feil.any { it.felt == "felle" }) " felle-feil" else ""}"
                  $ugyldigFelle>
            <option value="">Velg</option>
            <option value="nei">Nei, saken er i orden</option>
            <option value="fodselsdato">Fødselsdatoen</option>
            <option value="kommune">Kommunen</option>
            <option value="epost">E-postadressen</option>
          </select>
          <p class="fs-help-text" id="felle-hjelp">Å se at alt er i orden teller like mye.</p>
          ${feilmelding(feil, "felle", "felle-feil")}
        </fs-field>

        <button class="fs-button skjema__send" type="submit">Fatt vedtak</button>
      </form>
    </section>
  """
    .trimIndent()
}

/**
 * Svaret er levert, og spilleren får vite hvordan det gikk med én gang.
 *
 * Fasiten står ikke her. Den kommer når runden er over og alle har levert.
 * Det som står her er bare om ditt eget svar traff, og det er trygt fordi
 * et svar ikke kan gjøres om.
 */
private fun kvittering(tilstand: Tilstand): String {
  val vurdering = tilstand.meg?.vurdering
  val poeng = vurdering?.poeng ?: 0

  fun linje(navn: String, riktig: Boolean, verdt: Int) =
    """
    <li class="kvittering__linje" data-riktig="$riktig">
      <span class="kvittering__felt">$navn</span>
      <span class="kvittering__dom">${if (riktig) "Riktig" else "Feil"}</span>
      <span class="kvittering__verdt">${if (riktig) "+$verdt" else "0"}</span>
    </li>
    """
      .trimIndent()

  val farge =
    when {
      poeng >= tilstand.poeng.fullPott -> "success"
      poeng > 0 -> "info"
      else -> "warning"
    }
  val overskrift =
    when {
      poeng >= tilstand.poeng.fullPott -> "Full pott"
      poeng > 0 -> "Delvis truffet"
      else -> "Ingen uttelling"
    }

  return """
    <section class="fs-card kort vedtakskort">
      <h2 class="fs-heading" data-size="s">Vedtaket er fattet</h2>

      <div class="fs-alert" data-color="$farge">
        <p class="fs-alert__title">$overskrift</p>
        <p>Du fikk <strong>$poeng av ${tilstand.poeng.fullPott} poeng</strong> på denne saken.</p>
      </div>

      ${if (vurdering == null) "" else """
      <ul class="fs-list kvittering" data-variant="plain">
        ${linje("Utfall", vurdering.vedtakRiktig, tilstand.poeng.vedtak)}
        ${linje("Hjemmel", vurdering.hjemmelRiktig, tilstand.poeng.hjemmel)}
        ${linje("Kommune", vurdering.kommuneRiktig, tilstand.poeng.kommune)}
        ${linje("Feil i søknaden", vurdering.felleRiktig, tilstand.poeng.felle)}
      </ul>"""}

      <p class="vedtakskort__ingress">Fasiten og begrunnelsen kommer når runden er over, og alle har levert.</p>
    </section>
  """
    .trimIndent()
}

/**
 * Hvem som kom best ut av saken.
 *
 * Tavla viser totalen, og den sier ingenting om hvem som faktisk leste denne
 * saken best. Med bare én på vakt er det ingen å sammenligne med, og da står
 * det ingenting.
 */
private fun besteIRunden(tilstand: Tilstand): String {
  if (tilstand.tavle.size < 2) return ""
  val beste = tilstand.tavle.maxByOrNull { it.sistePoeng } ?: return ""
  if (beste.sistePoeng <= 0) return ""

  val delt = tilstand.tavle.filter { it.sistePoeng == beste.sistePoeng }
  val hvem =
    when {
      delt.size == 1 && beste.erMeg -> "Du kom best ut av saken"
      delt.size == 1 -> "${beste.navn.trygg()} kom best ut av saken"
      delt.size == tilstand.tavle.size -> "Alle kom likt ut av saken"
      else -> "${delt.joinToString(" og ") { it.navn.trygg() }} kom likt best ut"
    }

  return """
    <p class="fs-alert beste" data-color="success">
      <strong>$hvem</strong> med ${beste.sistePoeng} av ${tilstand.poeng.fullPott} poeng.
    </p>
  """
    .trimIndent()
}

/**
 * Resultatet av runden, som en modal dialog.
 *
 * Hele skjemaet vurderes, og dialogen sier både hva du svarte og hva som var
 * riktig, felt for felt. Det holder ikke å si «feil»: da vet du fortsatt ikke
 * hva du skulle ha svart, og neste runde blir like tilfeldig.
 *
 * `open` står begge steder. På verten er det serverens beskjed til
 * komponenten. På selve `<dialog>` er det reserven for den som ikke har
 * JavaScript: uten attributtet er dialogen skjult, og innholdet finnes ikke.
 * Nettleseren setter det samme attributtet når `showModal()` kalles, og en
 * patch river det bort igjen. Komponenten setter det tilbake så lenge
 * dialogen står i topplaget, så malen trenger ingen bevaringsliste.
 */
fun resultatdialog(tilstand: Tilstand): String {
  val fasit = tilstand.fasit
  val meg = tilstand.meg

  // Dialogen hører til oppgjøret, og innholdet skal ikke stå i markupen
  // ellers. Spilltjeneren sender ingen fasit i en runde, men markupen skal
  // ikke hvile på det: sto fasiten her skjult, kunne hvem som helst lest den
  // i kildekoden mens runden gikk.
  if (tilstand.fase != "oppgjor" || fasit == null || meg == null) {
    return """<fs-dialog id="resultat"></fs-dialog>"""
  }

  val svar = meg.svar
  val vurdering = meg.vurdering
  val besvart = svar != null && vurdering != null

  val utfall =
    when {
      besvart && vurdering!!.poeng >= tilstand.poeng.fullPott -> "full"
      besvart && vurdering!!.poeng > 0 -> "delvis"
      besvart -> "ingen"
      // Den som meldte seg på midt i saken har aldri sett den, og skal ikke
      // få et avvik for noe hun ikke kunne gjort noe med.
      !meg.medPaSaken -> "sent"
      else -> "avvik"
    }
  val overskrift =
    when (utfall) {
      "full" -> "Full pott"
      "delvis" -> "Delvis truffet"
      "ingen" -> "Ingen uttelling"
      "sent" -> "Du kom inn midt i saken"
      else -> "Avvik registrert"
    }

  // Den som sitter over flere saker på rad tas til slutt av vakta. Da skal
  // hun ha hørt det først.
  val igjen = tilstand.grenseUtenSvar - meg.runderUtenSvar
  val advarsel =
    if (meg.runderUtenSvar in 1 until tilstand.grenseUtenSvar)
      """
      <p class="vedtakskort__ingress">
        Du har stått over ${if (meg.runderUtenSvar == 1) "én sak" else "${meg.runderUtenSvar} saker"}.
        ${if (igjen == 1) "Står du over én til, blir du tatt av vakta." else "Står du over $igjen til, blir du tatt av vakta."}
      </p>"""
    else ""

  // Radene vises også til den som ikke svarte. Uten dem fikk hun aldri vite
  // hva som var riktig, og neste sak ble like tilfeldig. Den som kom inn
  // midt i saken får bare fasiten: en rad med «Feil» i rødt for noe hun
  // aldri fikk se, er en dom over feil person.
  val rader =
    if (utfall == "sent")
      listOf(
          fasitrad("Utfall", vedtaksord(fasit.vedtak)),
          fasitrad("Hjemmel", fasit.hjemmel),
          fasitrad("Kommune", tilstand.fasitKommune ?: "Ingen, kommunen finnes ikke lenger"),
          fasitrad(
            "Feil i søknaden",
            fasit.felle?.let { feltnavn(it).replaceFirstChar { t -> t.uppercase() } }
              ?: "Ingen, saken var i orden",
          ),
        )
        .joinToString("\n")
    else
      listOf(
          resultatrad(
            "Utfall",
            vedtaksord(svar?.vedtak),
            vedtaksord(fasit.vedtak),
            vurdering?.vedtakRiktig == true,
          ),
          resultatrad(
            "Hjemmel",
            svar?.hjemmel?.ifBlank { null } ?: "Ikke besvart",
            fasit.hjemmel,
            vurdering?.hjemmelRiktig == true,
          ),
          resultatrad(
            "Kommune",
            svar?.kommune?.ifBlank { null } ?: "Ingen",
            tilstand.fasitKommune ?: "Ingen, kommunen finnes ikke lenger",
            vurdering?.kommuneRiktig == true,
          ),
          resultatrad(
            "Feil i søknaden",
            svar?.felle?.let { feltnavn(it).replaceFirstChar { t -> t.uppercase() } } ?: "Ingen",
            fasit.felle?.let { feltnavn(it).replaceFirstChar { t -> t.uppercase() } }
              ?: "Ingen, saken var i orden",
            vurdering?.felleRiktig == true,
          ),
        )
        .joinToString("\n")

  val innledning =
    when (utfall) {
      "sent" ->
        """
        <p class="resultat__forklaring">
          Saken lå alt på bordet da du møtte. Ingen poeng denne runden, og du
          er med fra neste sak.
        </p>"""
      "avvik" ->
        """
        <p class="resultat__forklaring">
          Saken ble ikke behandlet innen fristen. Avviket er varslet til
          statsforvalteren, og runden gir null poeng.
        </p>"""
      else ->
        """
        <p class="resultat__poeng">
          <strong>${vurdering?.poeng ?: 0}</strong> av ${tilstand.poeng.fullPott} poeng
        </p>"""
    }

  return """
    <fs-dialog id="resultat" open>
      <dialog class="fs-dialog resultat" aria-labelledby="resultat-tittel"
              data-utfall="$utfall" open>
        <div class="resultat__topp">
          <h2 class="fs-dialog__title" id="resultat-tittel">${overskrift.trygg()}</h2>
          $innledning
        </div>

        <div class="fs-dialog__body">

          <dl class="resultat__liste">$rader</dl>

          $advarsel

          <!-- Nøytral farge med vilje: toppfeltet over bærer utfallet, og to
               fargede flater oppå hverandre gjør det uklart hvilken av dem
               som betyr noe. -->
          <div class="fs-alert">
            <p class="fs-alert__title">Sak ${tilstand.sak.id.trygg()}</p>
            <p>${(tilstand.forklaring ?: "").trygg()}</p>
          </div>
        </div>

        <form method="dialog" class="fs-dialog__footer">
          <button class="fs-button" value="lukk">Lukk</button>
        </form>
      </dialog>
    </fs-dialog>
  """
    .trimIndent()
}

private fun vedtaksord(vedtak: String?) =
  when (vedtak) {
    "innvilget" -> "Innvilget"
    "avslatt" -> "Avslått"
    else -> "Ikke besvart"
  }

/** En rad uten dom: bare hva som var riktig. */
private fun fasitrad(navn: String, riktig: String) =
  """
  <div class="resultat__rad">
    <dt class="resultat__felt">${navn.trygg()}</dt>
    <dd class="resultat__ditt">${riktig.trygg()}</dd>
  </div>
  """
    .trimIndent()

private fun resultatrad(navn: String, ditt: String, riktig: String, erRiktig: Boolean) =
  """
  <div class="resultat__rad" data-riktig="$erRiktig">
    <dt class="resultat__felt">${navn.trygg()}</dt>
    <dd class="resultat__ditt">${ditt.trygg()}</dd>
    <dd class="resultat__dom">${if (erRiktig) "Riktig" else "Feil"}</dd>
    ${if (erRiktig) "" else """<dd class="resultat__riktig">Riktig svar: ${riktig.trygg()}</dd>"""}
  </div>
  """
    .trimIndent()

/** Fasit, poeng og plassering. Saken står igjen over, så svaret har noe å vise til. */
fun oppgjor(tilstand: Tilstand): String {
  val fasit = tilstand.fasit ?: return ""
  val meg = tilstand.meg
  val flytting =
    when {
      meg == null || meg.forrigePlass == 0 -> ""
      meg.plass < meg.forrigePlass -> "Du gikk fra ${meg.forrigePlass}. til ${meg.plass}. plass."
      meg.plass > meg.forrigePlass -> "Du falt fra ${meg.forrigePlass}. til ${meg.plass}. plass."
      else -> "Du holder ${meg.plass}. plass."
    }

  return """
    ${sakskort(tilstand.sak)}

    ${besteIRunden(tilstand)}

    <section class="fs-card kort fasitkort">
      <div class="fasitkort__topp">
        <h2 class="fs-heading" data-size="s">Fasit</h2>

        <!-- Lukker du dialogen mens du venter på neste sak, skal du komme
             til den igjen. Datastar setter `open` på verten, og komponenten
             gjør kallet; ingen av delene trenger et skript ved siden av. -->
        <button class="fs-button" data-variant="secondary" type="button"
                data-on:click="document.getElementById('resultat')?.setAttribute('open', '')">
          Se resultatet
        </button>
      </div>

      <dl class="fasit">
        <div class="fasit__rad">
          <dt>Utfall</dt>
          <dd>${if (fasit.vedtak == "innvilget") "Innvilget" else "Avslått"}</dd>
        </div>
        <div class="fasit__rad">
          <dt>Hjemmel</dt>
          <dd>${fasit.hjemmel.trygg()}</dd>
        </div>
        <div class="fasit__rad">
          <dt>Kommune</dt>
          <dd>${tilstand.fasitKommune ?: "Ingen, kommunen finnes ikke lenger"}</dd>
        </div>
        <div class="fasit__rad">
          <dt>Feil i søknaden</dt>
          <dd>${if (fasit.felle == null) "Ingen, saken var i orden" else "${feltnavn(fasit.felle).replaceFirstChar { it.uppercase() }}"}</dd>
        </div>
      </dl>

      <div class="fs-alert" data-color="info">
        <p>${(tilstand.forklaring ?: "").trygg()}</p>
      </div>

      ${if (meg == null) "" else """
      <p class="poeng">
        <strong>+${meg.sistePoeng}</strong> denne runden · ${meg.poeng} til sammen
      </p>
      <p class="vedtakskort__ingress">$flytting</p>"""}
    </section>
  """
    .trimIndent()
}

private fun feltnavn(felle: String) =
  when (felle) {
    "fodselsdato" -> "fødselsdatoen"
    "kommune" -> "kommunen"
    "epost" -> "e-postadressen"
    else -> felle
  }

/** Sluttstilling og den evige topplista. */
fun slutt(tilstand: Tilstand): String {
  val evig =
    tilstand.evigToppliste.withIndex().joinToString("\n") { (nr, it) ->
      """
      <tr>
        <td>${nr + 1}</td>
        <td>${it.navn.trygg()}</td>
        <td>${it.poeng}</td>
        <td><span class="fs-badge" data-color="${farge(it.stack)}">${appnavn(it.stack)}</span></td>
      </tr>
      """
        .trimIndent()
    }

  val meg = tilstand.meg

  return """
    <section class="fs-card kort sluttkort">
      <h2 class="fs-heading" data-size="m">Vakta er over</h2>
      ${if (meg == null) "" else """
      <p class="poeng"><strong>${meg.poeng} poeng</strong> · ${meg.plass}. plass</p>"""}
      <p class="fs-paragraph">Ny omgang starter om
        <span class="nedtelling" data-frist="${tilstand.fristMs}"
          data-lengde="${tilstand.faseLengdeMs}" data-preserve-attr="style">…</span>
        <span class="nedtelling-enhet">sekunder</span>.</p>

      <h3 class="fs-heading" data-size="xs">Evig toppliste</h3>
      <div class="fs-table-scroll" tabindex="0">
        <table class="fs-table">
          <thead><tr><th>#</th><th>Navn</th><th>Poeng</th><th>App</th></tr></thead>
          <tbody>${evig.ifBlank { "<tr><td colspan=\"4\">Ingen ennå.</td></tr>" }}</tbody>
        </table>
      </div>
    </section>
  """
    .trimIndent()
}

/** Tavla. Den samme i alle tre appene, og det er hele poenget. */
fun tavle(tilstand: Tilstand): String {
  // I runden sier den fjerde kolonnen hvem som har levert; det er det du
  // lurer på da. I oppgjøret sier den hvilken app hver spiller sitter i, som
  // er hele poenget med demoen.
  val oppgjor = tilstand.fase != "runde"

  val rader =
    tilstand.tavle.joinToString("\n") {
      """
      <tr${if (it.erMeg) " class=\"meg\"" else ""}>
        <td>${it.plass}</td>
        <td>
          <span class="tavle__navn">
            <span class="fs-avatar" data-size="small" aria-hidden="true">${initialer(it.navn)}</span>
            <span>${it.navn.trygg()}${if (it.erMeg) """ <span class="tavle__deg">(deg)</span>""" else ""}</span>
          </span>
        </td>
        <td class="tavle__poeng">
          ${it.poeng}${if (oppgjor && it.sistePoeng > 0) """ <span class="tavle__runde">+${it.sistePoeng}</span>""" else ""}
        </td>
        <td>${if (oppgjor) """<span class="fs-badge" data-color="${farge(it.stack)}">${appnavn(it.stack)}</span>"""
             else if (it.harSvart) """<span class="fs-badge" data-color="success">Levert</span>"""
             else """<span class="fs-badge">Jobber</span>"""}</td>
      </tr>
      """
        .trimIndent()
    }

  return """
      <h2 class="fs-heading" data-size="s">På vakt nå</h2>
      ${if (oppgjor) "" else svartSaLangt(tilstand)}
      <div class="fs-table-scroll" tabindex="0">
        <table class="fs-table tavle__tabell">
        <thead>
          <tr><th>#</th><th>Navn</th><th>Poeng</th><th>${if (oppgjor) "App" else "Status"}</th></tr>
        </thead>
        <tbody>${rader.ifBlank { "<tr><td colspan=\"4\">Ingen på vakt.</td></tr>" }}</tbody>
        </table>
      </div>
      <p class="tavle__fot">Alle tre appene spiller på det samme brettet.</p>
  """
    .trimIndent()
}

/**
 * Fargen på merket per app.
 *
 * `fs-badge` har `success`, `warning`, `danger` og `neutral`. Et
 * `data-color="info"` ville falt tilbake på standarden uten at noe sa fra,
 * og da er det ærligere å be om standarden.
 */
/** Navnet appen har utad. Enumverdien er en maskinverdi. */
private fun appnavn(stack: String) =
  when (stack) {
    "tanstack" -> "TanStack"
    "datastar" -> "Datastar"
    "astro" -> "Astro"
    else -> "Ukjent"
  }

private fun farge(stack: String) =
  when (stack) {
    "datastar" -> "success"
    "astro" -> "warning"
    else -> "neutral"
  }
