package no.fristil.forstelinja.datastar

/**
 * HTML-en, skrevet for hånd i Kotlin.
 *
 * Med vilje strengmaler og ikke kotlinx.html: poenget med demoen er at en
 * leser skal kunne sammenligne markupen med den samme skjermen i React og i
 * Astro. Da må den stå slik den havner i nettleseren, med klassene og
 * bevaringslistene synlige.
 *
 * Her kalles ingen `fs.field()`. Kotlin kan ikke kalle en TypeScript-
 * funksjon, så serveren skriver klassene og koblingen selv, og `<fs-field>`
 * gjør resten i nettleseren. Derfor står `data-preserve-attr` på hvert
 * element komponenten rører.
 */

private fun String.trygg(): String =
  replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

/** Hele siden, første gang. */
fun side(tilstand: Tilstand, spillerNavn: String?, kommuner: List<String> = emptyList()): String {
  val stilark = STILARK.joinToString("\n    ") { """<link rel="stylesheet" href="$it">""" }
  val imports = KOMPONENTER.joinToString("\n      ") { (fil, fn) -> """import { $fn } from "$fil"; $fn();""" }

  return """
    <!doctype html>
    <html lang="nb">
    <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Førstelinja · Datastar og Kotlin</title>
    $stilark
    <link rel="stylesheet" href="/brett.css">
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
    <body data-init="@get('/hendelser')">
    <!-- Sambandslinja eier sitt eget innhold, og serveren har ingenting å
         sende for den. `data-ignore-morph` hindrer at en patch river bort en
         melding midt i visningen. -->
    <!-- Vakta. Komponenten teller sekunder uten aktivitet, så en spiller
         som går fra maskinen får beskjed framfor å bli stående på tavla.
         Den eier sin egen dialog, derfor `data-ignore-morph`.
         Tallene må være større enn en runde pluss et oppgjør: å lese en sak
         er verken klikk, tastetrykk eller rulling, og med et minutt gikk
         vakta av midt i lesingen. -->
    <fs-session-timeout class="fs-session-timeout"
      warn-at="420" expires-at="480" data-ignore-morph></fs-session-timeout>
    <fs-connection-status class="fs-connection-status"
      offline-text="Sambandet til etaten er nede"
      online-text="Sambandet er tilbake"
      data-ignore-morph></fs-connection-status>

    <header class="topplinje">
      <div class="topplinje__innhold stamme">
        ${topp(tilstand)}
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
      // Klassen, ikke en id: nedtellingen står både i toppen og i panelet
      // når omgangen er over. Med samme id på begge fant getElementById
      // bare den første, og panelet ble stående med « … sekunder».
      // Temaet. Fristil bytter av seg selv etter `prefers-color-scheme`, og
      // `data-theme` på rota overstyrer. Ingen attributt betyr «det maskinen
      // sier», som er standarden.
      const temaValg = localStorage.getItem("forstelinja-tema") ?? "system"
      const settTema = (verdi) => {
        if (verdi === "system") document.documentElement.removeAttribute("data-theme")
        else document.documentElement.setAttribute("data-theme", verdi)
        localStorage.setItem("forstelinja-tema", verdi)
      }
      settTema(temaValg)
      for (const knapp of document.querySelectorAll('input[name="tema"]')) {
        knapp.checked = knapp.value === temaValg
        knapp.addEventListener("change", () => settTema(knapp.value))
      }

      setInterval(() => {
        for (const el of document.querySelectorAll(".nedtelling")) {
          const igjen = Math.max(0, Math.round((Number(el.dataset.frist) - Date.now()) / 1000))
          // Klokka i toppen står som minutter og sekunder. Inne i en setning
          // («starter om 12 sekunder») er tallet alene det som leses.
          el.textContent =
            "klokke" in el.dataset
              ? Math.floor(igjen / 60) + ":" + String(igjen % 60).padStart(2, "0")
              : igjen

          // Fargen går gradvis mot rødt. Den holder seg hvit til halve fristen
          // er gått, og blir så mer og mer rød. Et fast omslagspunkt ville
          // gitt et sjokk i stedet for et press.
          const lengde = Number(el.dataset.lengde)
          if (lengde > 0) {
            const igjenDel = Math.min(1, Math.max(0, (igjen * 1000) / lengde))
            const roedt = Math.round(Math.max(0, 1 - igjenDel * 2) * 100)
            el.style.color =
              "color-mix(in oklab, var(--fs-spill-frist-slutt) " +
              roedt +
              "%, var(--fs-spill-frist-start))"
          }
        }
      }, 250)
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
        <span class="topplinje__ord">
          <h1 class="topplinje__navn">Førstelinja</h1>
          <span class="topplinje__etat">Etaten for alminnelige søknader</span>
        </span>
      </div>

      <p class="topplinje__fase">$fase</p>

      <p class="topplinje__klokke">
        <span class="topplinje__merkelapp">$merkelapp</span>
        <span class="nedtelling" data-klokke data-frist="${tilstand.fristMs}"
              data-lengde="${tilstand.faseLengdeMs}" data-preserve-attr="style">–</span>
      </p>

      <span class="topplinje__stack">Kotlin · Datastar</span>
    </div>
  """
    .trimIndent()
}

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
  val sekunder = ((tilstand?.faseLengdeMs ?: 120_000) / 1000).toInt()
  val minutter =
    if (sekunder >= 60) "${tallord(sekunder / 60)} ${if (sekunder / 60 == 1) "minutt" else "minutter"}"
    else "$sekunder sekunder"

  return """
  <section class="fs-card kort blimed">
    <h2 class="fs-heading" data-size="m">Møt på vakt</h2>
    <p class="fs-paragraph blimed__ingress">
      Du er saksbehandler i førstelinja. $runder saker, $minutter på hver.
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
           `aria-describedby`: komponenten setter koblingen i nettleseren, og
           gjør det likt uansett hvilket språk serveren er skrevet i. -->
      <fs-field>
        <label class="fs-label" data-preserve-attr="${Bevar.KOBLING_LEDETEKST}">Navnet ditt</label>
        <input class="fs-input" name="navn" type="text" required
               data-preserve-attr="${Bevar.KOBLING_KONTROLL}">
        <p class="fs-help-text" data-preserve-attr="${Bevar.KOBLING_HJELPETEKST}">Vises på tavla for alle.</p>
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
                tabindex="0" data-preserve-attr="${Bevar.FANE}">Søknaden</button>
        <button id="f-1" role="tab" type="button" aria-selected="false" aria-controls="p-1"
                tabindex="-1" data-preserve-attr="${Bevar.FANE}">Søkeren</button>
      </div>

      <div id="p-0" class="fs-tabs__panel" role="tabpanel" aria-labelledby="f-0" tabindex="0"
           data-preserve-attr="${Bevar.FANEPANEL}">
        <p class="fs-paragraph sakskort__tekst">${sak.tekst.trygg()}</p>
        <p class="sakskort__signatur">Med vennlig hilsen<br>${sak.soker.navn.trygg()}</p>
      </div>

      <div id="p-1" class="fs-tabs__panel" role="tabpanel" aria-labelledby="f-1" tabindex="0"
           hidden data-preserve-attr="${Bevar.FANEPANEL}">
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

  val hjemmelforklaringer =
    tilstand.hjemler.joinToString("\n") {
      "<li><strong>${it.kode.trygg()}</strong> ${it.tekst.trygg()}</li>"
    }

  val kommunevalg =
    kommuner.withIndex().joinToString("\n") { (nr, navn) ->
      "<li class=\"fs-suggestion__option\" id=\"kommune-option-$nr\" role=\"option\" " +
        "aria-selected=\"false\" data-preserve-attr=\"${Bevar.FORSLAG_VALG}\">${navn.trygg()}</li>"
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
      <p class="vedtakskort__ingress">Fire spørsmål, $POENG_FULL_POTT poeng. Du kan svare én gang.</p>

      $feiloppsummering

      <form class="skjema" data-on:submit="@post('/svar')">
        <!-- `.fs-radio-row` er raden som holder knappen og teksten ved
             siden av hverandre. Uten den ligger de to inntil hverandre uten
             luft, for `.fs-label` er et vanlig blokkelement. -->
        <fieldset class="fs-fieldset" $ugyldigUtfall>
          <legend class="fs-legend">Utfall</legend>
          <div class="fs-radio-row">
            <input class="fs-radio" type="radio" id="v-innvilget" name="vedtak" value="innvilget"
                   data-bind:vedtak>
            <label class="fs-label" for="v-innvilget">Innvilget</label>
          </div>
          <div class="fs-radio-row">
            <input class="fs-radio" type="radio" id="v-avslatt" name="vedtak" value="avslatt"
                   data-bind:vedtak>
            <label class="fs-label" for="v-avslatt">Avslått</label>
          </div>
        </fieldset>

        <!-- Id-en står her fordi feiloppsummeringen lenker til feltet.
             Skal noe annet peke på et element, må serveren navngi det.
             Ellers lar vi komponenten finne på id-en. -->
        <fs-field>
          <label class="fs-label" for="hjemmel">Hjemmel</label>
          <select class="fs-select" id="hjemmel" name="hjemmel" data-bind:hjemmel
                  $ugyldigHjemmel>
            <option value="">Velg hjemmel</option>
            $hjemler
          </select>
        </fs-field>

        <!-- Hjelpen til hjemlene. Serveren skriver koblingen mellom
             knappen og panelet; komponenten plasserer det og lukker det.
             Det brukeren gjør, står i data-preserve-attr, for serveren
             vet ikke om vinduet er åpent. -->
        <fs-popover class="hjelpelenke" placement="bottom-start"
                    data-preserve-attr="${Bevar.SPRETTOPP_VERT}">
          <button type="button" class="fs-button" data-variant="ghost"
                  aria-expanded="false" aria-controls="hjemmelhjelp"
                  data-preserve-attr="${Bevar.SPRETTOPP_KNAPP}">Hva betyr hjemlene?</button>
          <div class="fs-popover" id="hjemmelhjelp" popover="manual"
               data-preserve-attr="${Bevar.SPRETTOPP_PANEL}">
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
                   aria-controls="kommune-list" aria-describedby="kommune-hjelp kommune-status"
                   data-bind:kommune
                   $ugyldigKommune
                   data-preserve-attr="${Bevar.FORSLAG_KONTROLL}">
            <ul class="fs-suggestion__list" id="kommune-list" role="listbox" hidden
                data-preserve-attr="${Bevar.FORSLAG_LISTE}">
              $kommunevalg
            </ul>
            <p class="fs-suggestion__empty" hidden
               data-preserve-attr="${Bevar.FORSLAG_TOM}">Ingen treff. Finnes kommunen fortsatt?</p>
            <span class="fs-sr-only" id="kommune-status" aria-live="polite" data-ignore-morph></span>
          </div>
          <p class="fs-help-text" id="kommune-hjelp">Begynn å skrive, så kommer forslagene. Finner du den ikke, la feltet stå tomt.</p>
        </fs-suggestion>

        <!-- Feiloppsummeringen lenker hit, så serveren navngir feltet, og
             skriver da hele koblingen selv. Da har komponenten ingenting å
             legge til, og morfingen ingenting å ta bort. -->
        <fs-field>
          <label class="fs-label" for="felle">Er noe feil i søknaden?</label>
          <select class="fs-select" id="felle" name="felle" data-bind:felle
                  aria-describedby="felle-hjelp" $ugyldigFelle>
            <option value="">Velg</option>
            <option value="nei">Nei, saken er i orden</option>
            <option value="fodselsdato">Fødselsdatoen</option>
            <option value="kommune">Kommunen</option>
            <option value="epost">E-postadressen</option>
          </select>
          <p class="fs-help-text" id="felle-hjelp">Å se at alt er i orden teller like mye.</p>
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
      poeng >= POENG_FULL_POTT -> "success"
      poeng > 0 -> "info"
      else -> "warning"
    }
  val overskrift =
    when {
      poeng >= POENG_FULL_POTT -> "Full pott"
      poeng > 0 -> "Delvis truffet"
      else -> "Ingen uttelling"
    }

  return """
    <section class="fs-card kort vedtakskort">
      <h2 class="fs-heading" data-size="s">Vedtaket er fattet</h2>

      <div class="fs-alert" data-color="$farge">
        <p class="fs-alert__title">$overskrift</p>
        <p>Du fikk <strong>$poeng av $POENG_FULL_POTT poeng</strong> på denne saken.</p>
      </div>

      ${if (vurdering == null) "" else """
      <ul class="fs-list kvittering" data-variant="plain">
        ${linje("Utfall", vurdering.vedtakRiktig, 10)}
        ${linje("Hjemmel", vurdering.hjemmelRiktig, 5)}
        ${linje("Kommune", vurdering.kommuneRiktig, 5)}
        ${linje("Feil i søknaden", vurdering.felleRiktig, 5)}
      </ul>"""}

      <p class="vedtakskort__ingress">Fasiten og begrunnelsen kommer når runden er over, og alle har levert.</p>
    </section>
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
 * `open` står på verten og er serverens beskjed. `data-preserve-attr="open"`
 * står på selve `<dialog>`, fordi det er nettleseren som setter det når
 * `showModal()` kalles.
 */
fun resultatdialog(tilstand: Tilstand): String {
  val fasit = tilstand.fasit
  val meg = tilstand.meg
  val apen = tilstand.fase == "oppgjor" && fasit != null && meg != null

  if (fasit == null || meg == null) {
    return """<fs-dialog id="resultat"></fs-dialog>"""
  }

  val svar = meg.svar
  val vurdering = meg.vurdering
  val besvart = svar != null && vurdering != null

  val utfall =
    when {
      besvart && vurdering!!.poeng >= POENG_FULL_POTT -> "full"
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

  // Radene vises også til den som ikke svarte. Uten dem fikk hun aldri vite
  // hva som var riktig, og neste sak ble like tilfeldig.
  val rader =
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
        <div class="fs-alert" data-color="info">
          <p class="fs-alert__title">Saken lå alt på bordet da du møtte</p>
          <p>Ingen poeng denne runden. Du er med fra neste sak.</p>
        </div>"""
      "avvik" ->
        """
        <div class="fs-alert" data-color="danger">
          <p class="fs-alert__title">Saken ble ikke behandlet innen fristen</p>
          <p>Avviket er varslet til fylkesmannen. Null poeng for runden.</p>
        </div>"""
      else ->
        """
        <p class="resultat__poeng">
          <strong>${vurdering?.poeng ?: 0}</strong> av $POENG_FULL_POTT poeng
        </p>"""
    }

  return """
    <fs-dialog id="resultat"${if (apen) " open" else ""}>
      <dialog class="fs-dialog resultat" aria-labelledby="resultat-tittel"
              data-utfall="$utfall" data-preserve-attr="open">
        <h2 class="fs-dialog__title" id="resultat-tittel">${overskrift.trygg()}</h2>

        <div class="fs-dialog__body">
          $innledning

          <dl class="resultat__liste">$rader</dl>

          <div class="fs-alert" data-color="info">
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

    <section class="fs-card kort fasitkort">
      <h2 class="fs-heading" data-size="s">Fasit</h2>

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
        <td><span class="fs-badge" data-color="${farge(it.stack)}">${it.stack.trygg()}</span></td>
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
          data-lengde="${tilstand.faseLengdeMs}">…</span> sekunder.</p>

      <h3 class="fs-heading" data-size="xs">Evig toppliste</h3>
      <table class="fs-table">
        <thead><tr><th>#</th><th>Navn</th><th>Poeng</th><th>App</th></tr></thead>
        <tbody>${evig.ifBlank { "<tr><td colspan=\"4\">Ingen ennå.</td></tr>" }}</tbody>
      </table>
    </section>
  """
    .trimIndent()
}

/** Tavla. Den samme i alle tre appene, og det er hele poenget. */
fun tavle(tilstand: Tilstand): String {
  val rader =
    tilstand.tavle.joinToString("\n") {
      """
      <tr${if (it.erMeg) " class=\"meg\"" else ""}>
        <td>${it.plass}</td>
        <td>${it.navn.trygg()}</td>
        <td class="tavle__poeng">${it.poeng}</td>
        <td><span class="fs-badge" data-color="${farge(it.stack)}">${it.stack.trygg()}</span></td>
      </tr>
      """
        .trimIndent()
    }

  return """
      <h2 class="fs-heading" data-size="s">På vakt nå</h2>
      <table class="fs-table tavle__tabell">
        <thead><tr><th>#</th><th>Navn</th><th>Poeng</th><th>App</th></tr></thead>
        <tbody>${rader.ifBlank { "<tr><td colspan=\"4\">Ingen på vakt.</td></tr>" }}</tbody>
      </table>
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
private fun farge(stack: String) =
  when (stack) {
    "datastar" -> "success"
    "astro" -> "warning"
    else -> "neutral"
  }
