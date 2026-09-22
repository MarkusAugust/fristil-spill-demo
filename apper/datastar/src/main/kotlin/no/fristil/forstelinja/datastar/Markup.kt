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
    <!-- Meldinger og sambandslinja eier sitt eget innhold, og serveren har
         ingenting å sende for dem. `data-ignore-morph` hindrer at en patch
         river bort en melding midt i visningen. -->
    <fs-toast class="fs-toast" label="Meldinger" data-ignore-morph></fs-toast>
    <!-- Vakta. Komponenten teller sekunder uten aktivitet, så en spiller
         som går fra maskinen får beskjed framfor å bli stående på tavla.
         Den eier sin egen dialog, derfor `data-ignore-morph`. -->
    <fs-session-timeout class="fs-session-timeout"
      warn-at="60" expires-at="90" data-ignore-morph></fs-session-timeout>
    <fs-connection-status class="fs-connection-status"
      offline-text="Sambandet til etaten er nede"
      online-text="Sambandet er tilbake"
      data-ignore-morph></fs-connection-status>

    ${topp(tilstand)}
    <main class="brett">
      ${brett(tilstand, spillerNavn, kommuner)}
    </main>

    <script>
      // Nedtellingen regnes ut i nettleseren fra et absolutt tidspunkt.
      // Serveren sender aldri «30 sekunder igjen», for da ville et forsinket
      // bud flyttet fristen, og de tre appene kommet i utakt.
      setInterval(() => {
        const el = document.getElementById("nedtelling")
        if (!el) return
        const igjen = Math.max(0, Math.round((Number(el.dataset.frist) - Date.now()) / 1000))
        el.textContent = igjen
      }, 250)
    </script>
    </body>
    </html>
  """
    .trimIndent()
}

/** Toppen: runde, nedtelling og hvem du er. Patches sammen med brettet. */
fun topp(tilstand: Tilstand): String {
  val fase =
    when (tilstand.fase) {
      "runde" -> "Runde ${tilstand.rundeNr} av ${tilstand.runderTotalt}"
      "oppgjor" -> "Oppgjør"
      else -> "Omgangen er slutt"
    }

  return """
    <header id="topp" class="topp">
      <h1 class="fs-heading" data-size="m">Førstelinja</h1>
      <p class="fs-paragraph topp__fase">$fase</p>
      <p class="fs-paragraph topp__tid">
        <span id="nedtelling" data-frist="${tilstand.fristMs}">…</span> sekunder
      </p>
      <span class="fs-badge" data-color="info">Kotlin · Datastar</span>
    </header>
  """
    .trimIndent()
}

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
  if (spillerNavn == null) blimed()
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
    <div id="sak">${saksomrade(tilstand, spillerNavn, kommuner, feil)}</div>
    <aside id="tavle" class="fs-card kort tavle">${tavle(tilstand)}</aside>
  </div>
  """
    .trimIndent()

/**
 * Skjemaet for å bli med.
 *
 * Her står hele kontrakten `fs.field()` ville skrevet, for hånd: klassene,
 * `for` og `id` som binder ledetekst til felt, og bevaringslistene.
 */
fun blimed(): String =
  """
  <section class="fs-card kort">
    <h2 class="fs-heading" data-size="s">Møt på vakt</h2>
    <p class="fs-paragraph">Du er saksbehandler i førstelinja. Sakene kommer uansett.</p>

    <!-- Et helt vanlig skjema, ikke en Datastar-innsending.
         Å møte på vakt er en navigering: kapselen må være satt før
         hendelsesstrømmen åpnes, ellers vet strømmen ikke hvem du er og
         dytter dette skjemaet tilbake over spillet. Det virker dessuten
         uten JavaScript i det hele tatt. -->
    <form method="post" action="/bli-med">
      <fs-field>
        <label class="fs-label" for="navn">Navnet ditt</label>
        <input class="fs-input" id="navn" name="navn" type="text" required>
        <p class="fs-help-text" id="navn-hjelp">Vises på tavla for alle.</p>
      </fs-field>

      <button class="fs-button" type="submit">Begynn vakta</button>
    </form>
  </section>
  """
    .trimIndent()

/** Selve saksbehandlingen. */
fun runde(tilstand: Tilstand, kommuner: List<String>, feil: List<Feil> = emptyList()): String {
  val sak = tilstand.sak
  val hjemler =
    tilstand.hjemler.joinToString("\n") {
      """<option value="${it.kode.trygg()}">${it.kode.trygg()} ${it.tekst.trygg()}</option>"""
    }
  val harSvart = tilstand.meg?.harSvart == true
  val ugyldigHjemmel =
    if (feil.any { it.felt == "hjemmel" }) "aria-invalid=\"true\" data-state=\"invalid\"" else ""
  val ugyldigKommune =
    if (feil.any { it.felt == "kommune" }) "aria-invalid=\"true\" data-state=\"invalid\"" else ""

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
        <h2 class="fs-error-summary__title">Du må rette ${feil.size} feil</h2>
        <ul class="fs-list">
          ${feil.joinToString("\n") { """<li><a href="#${it.felt}">${it.melding.trygg()}</a></li>""" }}
        </ul>
      </fs-error-summary>"""
    }

  return """
    <section class="fs-card kort">
      $feiloppsummering
      <span class="fs-tag">${sak.id.trygg()}</span>
      <h2 class="fs-heading" data-size="s">${sak.tittel.trygg()}</h2>
      <p class="fs-paragraph">${sak.sammendrag.trygg()}</p>

      <!-- Fanene: serveren skriver roller, kobling og hvilken som er valgt.
           Komponenten flytter valget når brukeren blar, og derfor står
           `aria-selected` og `tabindex` i bevaringslista. -->
      <fs-tabs>
        <div class="fs-tabs__list" role="tablist" aria-label="Saken">
          <button id="f-0" role="tab" type="button" aria-selected="true" aria-controls="p-0"
                  tabindex="0" data-preserve-attr="${Bevar.FANE}">Søkeren</button>
          <button id="f-1" role="tab" type="button" aria-selected="false" aria-controls="p-1"
                  tabindex="-1" data-preserve-attr="${Bevar.FANE}">Vedtak</button>
        </div>

        <div id="p-0" class="fs-tabs__panel" role="tabpanel" aria-labelledby="f-0" tabindex="0"
             data-preserve-attr="${Bevar.FANEPANEL}">
          <table class="fs-table">
            <tbody>
              <tr><th scope="row">Navn</th><td>${sak.soker.navn.trygg()}</td></tr>
              <tr><th scope="row">Fødselsdato</th><td>${sak.soker.fodselsdato.trygg()}</td></tr>
              <tr><th scope="row">Kommune</th><td>${sak.soker.kommune.trygg()}</td></tr>
              <tr><th scope="row">E-post</th><td>${sak.soker.epost.trygg()}</td></tr>
            </tbody>
          </table>
        </div>

        <div id="p-1" class="fs-tabs__panel" role="tabpanel" aria-labelledby="f-1" tabindex="0"
             hidden data-preserve-attr="${Bevar.FANEPANEL}">
          <form data-on:submit="@post('/svar')">
            <fieldset class="fs-fieldset">
              <legend class="fs-legend">Vedtak</legend>
              <label class="fs-label" for="v-innvilget">
                <input class="fs-radio" type="radio" id="v-innvilget" name="vedtak" value="innvilget"
                       data-bind:vedtak> Innvilget
              </label>
              <label class="fs-label" for="v-avslatt">
                <input class="fs-radio" type="radio" id="v-avslatt" name="vedtak" value="avslatt"
                       data-bind:vedtak> Avslått
              </label>
            </fieldset>

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
            <fs-popover placement="bottom-start" data-preserve-attr="${Bevar.SPRETTOPP_VERT}">
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
              <p class="fs-help-text" id="kommune-hjelp">Begynn å skrive, så kommer forslagene.</p>
            </fs-suggestion>

            <fs-field>
              <label class="fs-label" for="felle">Er noe feil i søknaden?</label>
              <select class="fs-select" id="felle" name="felle" data-bind:felle>
                <option value="">Nei, saken er i orden</option>
                <option value="fodselsdato">Fødselsdatoen</option>
                <option value="kommune">Kommunen</option>
                <option value="epost">E-postadressen</option>
              </select>
              <p class="fs-help-text" id="felle-hjelp">Å se at alt er i orden teller like mye.</p>
            </fs-field>

            <button class="fs-button" type="submit" ${if (harSvart) "disabled" else ""}>
              ${if (harSvart) "Vedtaket er fattet" else "Fatt vedtak"}
            </button>
          </form>
        </div>
      </fs-tabs>
    </section>
  """
    .trimIndent()
}

/** Fasit, poeng og plassering. */
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
    <section class="fs-card kort">
      <h2 class="fs-heading" data-size="s">Fasit</h2>

      <div class="fs-alert" data-color="info">
        <p class="fs-paragraph">
          Riktig: <strong>${if (fasit.vedtak == "innvilget") "innvilget" else "avslått"}</strong>,
          ${fasit.hjemmel.trygg()}.
          ${if (fasit.felle == null) "Saken var i orden." else "Fella lå i ${feltnavn(fasit.felle)}."}
        </p>
        <p class="fs-paragraph">${(tilstand.forklaring ?: "").trygg()}</p>
      </div>

      ${if (meg == null) "" else """
      <p class="fs-paragraph poeng">
        <strong>+${meg.sistePoeng} poeng</strong> · ${meg.poeng} totalt. $flytting
      </p>"""}
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
    tilstand.evigToppliste.joinToString("\n") {
      """<tr><td>${it.navn.trygg()}</td><td>${it.poeng}</td><td>${it.stack.trygg()}</td></tr>"""
    }

  return """
    <section class="fs-card kort">
      <h2 class="fs-heading" data-size="s">Vakta er over</h2>
      <p class="fs-paragraph">Ny omgang starter om <span id="nedtelling" data-frist="${tilstand.fristMs}">…</span> sekunder.</p>

      <h3 class="fs-heading" data-size="xs">Evig toppliste</h3>
      <table class="fs-table">
        <thead><tr><th>Navn</th><th>Poeng</th><th>Stack</th></tr></thead>
        <tbody>${evig.ifBlank { "<tr><td colspan=\"3\">Ingen ennå.</td></tr>" }}</tbody>
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
        <td>${it.poeng}</td>
        <td><span class="fs-badge" data-color="${farge(it.stack)}">${it.stack.trygg()}</span></td>
      </tr>
      """
        .trimIndent()
    }

  return """
      <h2 class="fs-heading" data-size="s">Tavle</h2>
      <table class="fs-table">
        <thead><tr><th>#</th><th>Navn</th><th>Poeng</th><th>App</th></tr></thead>
        <tbody>${rader.ifBlank { "<tr><td colspan=\"4\">Ingen på vakt.</td></tr>" }}</tbody>
      </table>
  """
    .trimIndent()
}

private fun farge(stack: String) =
  when (stack) {
    "tanstack" -> "info"
    "datastar" -> "success"
    "astro" -> "warning"
    else -> "neutral"
  }
