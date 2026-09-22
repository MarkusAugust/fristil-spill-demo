package no.fristil.forstelinja.datastar

/**
 * Oppsettet på brettet, og ingenting mer.
 *
 * Regelen for hele demoen: alt du ser av knapper, felt, kort og tabeller er
 * Fristils egne komponenter. Det som skrives her er hvor boksene ligger, og
 * topplinja, som er spillets eget ansikt og ikke noe designsystemet skal
 * eie.
 *
 * Verdiene er Fristils tokens, ikke egne tall. De to unntakene er bredden på
 * sidestammen og lengden på en tekstlinje, som er avgjørelser om denne
 * siden.
 */
val BRETT_CSS =
  """
  body {
    margin: 0;
    /* Fristil arver skrift med vilje, så appen må si hvilken. Uten dette
       er det nettleserens egen, som er en serif. */
    font-family:
      system-ui,
      -apple-system,
      "Segoe UI",
      Roboto,
      sans-serif;
    color: var(--semantic-page-foreground);
    /* Flaten er sideflaten med et snev av tekstfargen i seg, slik at hvite
       kort løfter seg fra den. I mørkt tema snus det under. */
    background: color-mix(in oklab, var(--semantic-page-foreground) 5%, var(--semantic-page-background));
  }

  .stamme {
    max-width: 76rem;
    margin-inline: auto;
    padding-inline: var(--size-4);
  }

  /* Topplinja
     ------------------------------------------------------------------ */

  .topplinje {
    color: var(--palette-graphite-0);
    background: var(--palette-denim-100);
  }

  /* Sambandslinja legger seg øverst i vinduet når den først dukker opp.
     Uten dette dekker den navnet i topplinja. */
  body:has(.fs-connection-status__bar) .topplinje {
    padding-block-start: var(--size-10);
  }

  .topplinje__innhold {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--size-3) var(--size-5);
    padding-block: var(--size-3);
  }

  .topplinje__hoved {
    display: flex;
    flex: 1 1 auto;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--size-3) var(--size-5);
  }

  .topplinje__merke {
    display: flex;
    flex: 1 1 auto;
    align-items: center;
    gap: var(--size-3);
  }

  .topplinje__emblem {
    inline-size: var(--size-8);
    block-size: var(--size-8);
    flex: none;
  }

  .topplinje__ord {
    display: flex;
    flex-direction: column;
    line-height: 1.25;
  }

  .topplinje__navn {
    /* `<h1>` har egne marger fra nettleseren, og de skilte navnet fra
       etatsnavnet under. */
    margin: 0;
    font-size: var(--font-size-xl);
    font-weight: 600;
    letter-spacing: 0.01em;
  }

  .topplinje__etat {
    font-size: var(--font-size-xs);
    color: color-mix(in oklab, var(--palette-graphite-0) 75%, transparent);
  }

  .topplinje__fase {
    margin: 0;
    font-size: var(--font-size-m);
    font-weight: 600;
  }

  .topplinje__klokke {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    margin: 0;
    line-height: 1.1;
  }

  .topplinje__merkelapp {
    font-size: var(--font-size-xxs);
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: color-mix(in oklab, var(--palette-graphite-0) 75%, transparent);
  }

  /* Fargene nedtellingen blandes av. De står på alle nedtellinger, ikke bare
     den i toppen: skriptet skriver `color` på hver av dem, og en variabel som
     ikke finnes ville gjort erklæringen ugyldig. */
  .nedtelling {
    --spill-frist-start: var(--palette-graphite-0);
    --spill-frist-slutt: var(--palette-burgundy-30);
  }

  /* Inne i et kort står tallet på sidens egen flate, ikke på den mørkeblå. */
  .brett .nedtelling {
    --spill-frist-start: var(--semantic-page-foreground);
    --spill-frist-slutt: var(--semantic-danger-main);
  }

  /*
   * Ristingen under de siste ti sekundene.
   *
   * Utslaget er `--spill-rist`, et tall mellom 0 og 1 som skriptet setter
   * for hvert sekund. Selve bevegelsen står her, så den kan slås av for den
   * som har bedt om mindre av den.
   */
  @keyframes spill-rist {
    0%, 100% { translate: 0 0; }
    20% { translate: calc(var(--spill-rist, 0) * -3px) 0; }
    40% { translate: calc(var(--spill-rist, 0) * 3px) 0; }
    60% { translate: calc(var(--spill-rist, 0) * -2px) 0; }
    80% { translate: calc(var(--spill-rist, 0) * 2px) 0; }
  }

  .nedtelling[data-rister] {
    display: inline-block;
    animation: spill-rist 220ms linear infinite;
  }

  /* En klokke som rister er et press, ikke en opplysning. Den som har bedt
     om mindre bevegelse får fargen alene, som sier det samme. */
  @media (prefers-reduced-motion: reduce) {
    .nedtelling[data-rister] { animation: none; }
  }

  .topplinje .nedtelling {
    color: var(--spill-frist-start);
    font-size: var(--font-size-mega);
    font-weight: 600;
    line-height: 1;
    /* Tallene skal ikke hoppe sidelengs mens sekundene går. */
    font-variant-numeric: tabular-nums;
  }

  .topplinje__stack {
    padding: var(--size-0-5) var(--size-2);
    font-size: var(--font-size-xs);
    border: 1px solid color-mix(in oklab, var(--palette-graphite-0) 45%, transparent);
    border-radius: var(--size-1);
  }

  /*
   * Temavelgeren står på den mørkeblå flata og må se ut deretter.
   *
   * Størrelsen settes med komponentvariablene, som er veien Fristil peker
   * på. Fargene finnes det ingen variabler for, og skal ikke finnes: all
   * komponent-CSS ligger i `@layer fristil`, så appens egne regler vinner
   * uansett spesifisitet. Dette er den tilpasningen dokumentasjonen lover.
   */
  .temavelger {
    --fs-toggle-group-height: var(--size-8);
    --fs-toggle-group-padding: 0 var(--size-3);
    border-color: color-mix(in oklab, var(--palette-graphite-0) 45%, transparent);
  }

  .temavelger .fs-toggle-group__option {
    font-size: var(--font-size-xs);
    color: color-mix(in oklab, var(--palette-graphite-0) 85%, transparent);
    background: transparent;
    border-inline-start-color: color-mix(in oklab, var(--palette-graphite-0) 45%, transparent);
  }

  .temavelger .fs-toggle-group__option:hover {
    background: color-mix(in oklab, var(--palette-graphite-0) 12%, transparent);
  }

  .temavelger .fs-toggle-group__option:has(input:checked) {
    color: var(--palette-denim-100);
    background: var(--palette-graphite-0);
  }

  /* Brettet
     ------------------------------------------------------------------ */

  .brett {
    padding-block: var(--size-6) var(--size-10);
  }

  .brett__innhold {
    display: grid;
    grid-template-columns: minmax(0, 1.9fr) minmax(0, 1fr);
    gap: var(--size-5);
    align-items: start;
  }

  .brett__hoved {
    display: flex;
    flex-direction: column;
    gap: var(--size-5);
  }

  .kort { padding: var(--size-5); }

  /* Saken
     ------------------------------------------------------------------ */

  .sakskort__stempel {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--size-3);
    margin: 0;
  }

  .sakskort__status {
    font-size: var(--font-size-xs);
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: var(--semantic-muted-foreground);
  }

  .sakskort__tittel {
    margin-block: var(--size-1) 0;
  }

  .sakskort__ingress {
    margin: 0;
    font-size: var(--font-size-l);
    line-height: 1.5;
    color: var(--semantic-muted-foreground);
  }

  .sakskort__faner {
    display: block;
    margin-block-start: var(--size-3);
  }

  .sakskort__tekst {
    max-width: 62ch;
    margin: 0;
    font-size: var(--font-size-m);
    line-height: 1.7;
  }

  .sakskort__signatur {
    margin-block: var(--size-4) 0;
    color: var(--semantic-muted-foreground);
  }

  /* Vedtaket
     ------------------------------------------------------------------ */

  .vedtakskort__ingress {
    margin: 0;
    font-size: var(--font-size-s);
    color: var(--semantic-muted-foreground);
  }

  /* Fristil setter ingen avstand utenpå komponentene, for det er sidens
     avgjørelse. Her er avgjørelsen tatt. */
  .skjema {
    display: flex;
    flex-direction: column;
    gap: var(--size-5);
    margin-block-start: var(--size-2);
  }

  .skjema fs-field,
  .skjema fs-suggestion {
    display: block;
  }

  .skjema__send {
    align-self: start;
  }

  /* Hjelpen til hjemlene hører til feltet over, og skal stå på linje med
     feltene og ikke rykket inn av knappens eget innrykk. Innrykket er en
     komponentvariabel, altså veien Fristil peker på for slikt. */
  .hjelpelenke {
    display: block;
    margin-block-start: calc(var(--size-4) * -1);
  }

  .hjelpelenke .fs-button {
    --fs-button-padding: var(--size-1) 0;
  }

  /* Kvitteringen
     ------------------------------------------------------------------ */

  .kvittering {
    display: flex;
    flex-direction: column;
    gap: var(--size-1);
    padding: 0;
    margin: 0;
    list-style: none;
  }

  .kvittering__linje {
    display: grid;
    grid-template-columns: 1fr auto var(--size-8);
    gap: var(--size-3);
    align-items: baseline;
    padding-block: var(--size-2);
    border-block-end: 1px solid var(--semantic-divider-30);
  }

  .kvittering__linje:last-child { border-block-end: 0; }

  .kvittering__dom {
    font-weight: 600;
    color: var(--semantic-danger-main);
  }

  .kvittering__linje[data-riktig="true"] .kvittering__dom {
    color: var(--semantic-success-foreground);
  }

  .kvittering__verdt {
    font-variant-numeric: tabular-nums;
    text-align: end;
    color: var(--semantic-muted-foreground);
  }

  /* Fasiten
     ------------------------------------------------------------------ */

  .fasitkort__topp {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: var(--size-3);
  }

  .fasitkort__topp .fs-heading { margin: 0; }

  .fasit {
    display: flex;
    flex-direction: column;
    gap: var(--size-1);
    margin: 0;
  }

  .fasit__rad {
    display: grid;
    grid-template-columns: minmax(0, 12rem) minmax(0, 1fr);
    gap: var(--size-3);
    padding-block: var(--size-2);
    border-block-end: 1px solid var(--semantic-divider-30);
  }

  .fasit__rad:last-child { border-block-end: 0; }
  .fasit dt { color: var(--semantic-muted-foreground); }
  .fasit dd { margin: 0; font-weight: 600; }

  .poeng {
    margin: 0;
    font-size: var(--font-size-l);
  }

  /* Resultatdialogen
     ------------------------------------------------------------------ */

  .resultat {
    --fs-dialog-width: min(34rem, 100%, calc(100vw - var(--size-8)));
    /* Innholdet får sin egen luft, siden toppfeltet går helt ut til kanten. */
    --fs-dialog-padding: 0;
  }

  /*
   * Utfallet står som et eget felt øverst, ikke som en strek langs kanten.
   * Streken var ment som en farge, men leste som noe som hadde havnet feil:
   * en linje uten forklaring, i en farge uten noe å høre til.
   *
   * Feltet bruker de samme parene som `fs-alert`, altså bakgrunn og
   * forgrunn fra samme token, så kontrasten holder i begge temaer.
   */
  .resultat__topp {
    display: flex;
    flex-direction: column;
    gap: var(--size-1);
    padding: var(--size-4) var(--size-5);
    border-start-start-radius: var(--size-2);
    border-start-end-radius: var(--size-2);
    color: var(--semantic-page-foreground);
    background: var(--semantic-neutral-background);
  }

  .resultat[data-utfall="full"] .resultat__topp {
    color: var(--semantic-success-foreground);
    background: var(--semantic-success-background);
  }

  .resultat[data-utfall="delvis"] .resultat__topp {
    color: var(--semantic-interactive-foreground);
    background: var(--semantic-interactive-background);
  }

  .resultat[data-utfall="ingen"] .resultat__topp,
  .resultat[data-utfall="avvik"] .resultat__topp {
    color: var(--semantic-danger-foreground);
    background: var(--semantic-danger-background);
  }

  .resultat[data-utfall="sent"] .resultat__topp {
    color: var(--semantic-page-foreground);
    background: var(--semantic-neutral-background);
  }

  .resultat .fs-dialog__title {
    margin: 0;
    color: inherit;
  }

  .resultat .fs-dialog__footer {
    padding: 0 var(--size-5) var(--size-5);
    margin-block-start: 0;
  }

  .resultat .fs-dialog__body {
    display: flex;
    flex-direction: column;
    gap: var(--size-4);
    padding: var(--size-5);
  }

  .resultat__poeng {
    margin: 0;
    font-size: var(--font-size-xl);
    color: inherit;
  }

  .resultat__forklaring {
    margin: 0;
    line-height: 1.5;
  }

  .resultat__liste {
    margin: 0;
    display: flex;
    flex-direction: column;
  }

  .resultat__rad {
    display: grid;
    grid-template-columns: minmax(0, 8rem) minmax(0, 1fr) auto;
    gap: var(--size-1) var(--size-3);
    align-items: baseline;
    padding-block: var(--size-2);
    border-block-end: 1px solid var(--semantic-divider-30);
  }

  .resultat__rad:last-child { border-block-end: 0; }

  .resultat__felt {
    grid-column: 1;
    color: var(--semantic-muted-foreground);
  }

  .resultat__ditt {
    grid-column: 2;
    margin: 0;
    font-weight: 600;
  }

  .resultat__dom {
    grid-column: 3;
    margin: 0;
    font-weight: 600;
    color: var(--semantic-danger-main);
  }

  .resultat__rad[data-riktig="true"] .resultat__dom {
    color: var(--semantic-success-foreground);
  }

  .resultat__riktig {
    grid-column: 2 / -1;
    margin: 0;
    font-size: var(--font-size-s);
    color: var(--semantic-muted-foreground);
  }

  /* Tavla
     ------------------------------------------------------------------ */

  .tavle__tabell { font-size: var(--font-size-s); }
  .tavle__poeng { font-variant-numeric: tabular-nums; }
  .tavle .meg { font-weight: 600; }

  .tavle__fot {
    margin: 0;
    font-size: var(--font-size-xs);
    color: var(--semantic-muted-foreground);
  }

  /* Møt på vakt
     ------------------------------------------------------------------ */

  .blimed { max-width: 34rem; }
  .blimed__ingress { margin: 0; line-height: 1.6; }
  .blimed form { display: flex; flex-direction: column; gap: var(--size-4); }
  .blimed fs-field { display: block; }

  /* Mørkt tema
     ------------------------------------------------------------------ */

  /*
   * I mørkt tema snus forholdet: sideflaten er den mørkeste, og kortene
   * løftes. Samme regel begge veier ville gitt kort som er mørkere enn
   * siden.
   *
   * Reglene står to ganger, med de samme selektorene Fristil bruker:
   * mediespørringen gjelder når spilleren ikke har valgt noe, og
   * `[data-theme]` når hun har. Bare mediespørringen ville gjort velgeren
   * halvvirksom: et mørkt valg på en lys maskin ville snudd tokenene uten å
   * snu disse to reglene.
   */
  @media (prefers-color-scheme: dark) {
    :root:not([data-theme="light"]) body {
      background: var(--semantic-page-background);
    }

    :root:not([data-theme="light"]) .fs-card {
      background: color-mix(in oklab, var(--semantic-page-foreground) 8%, var(--semantic-page-background));
      border-color: color-mix(in oklab, var(--semantic-page-foreground) 18%, transparent);
    }
  }

  [data-theme="dark"] body {
    background: var(--semantic-page-background);
  }

  [data-theme="dark"] .fs-card {
    background: color-mix(in oklab, var(--semantic-page-foreground) 8%, var(--semantic-page-background));
    border-color: color-mix(in oklab, var(--semantic-page-foreground) 18%, transparent);
  }

  @media (max-width: 900px) {
    .brett__innhold { grid-template-columns: 1fr; }
  }

  /* På smal skjerm får navnet en linje for seg, og runde, klokke og app
     står på linja under. Ellers havner klokka alene under navnet med et
     tomrom ved siden av. */
  @media (max-width: 640px) {
    .topplinje__merke { flex-basis: 100%; }

    .topplinje__klokke {
      flex-direction: row;
      align-items: baseline;
      gap: var(--size-2);
      margin-inline-end: auto;
    }

    .topplinje .nedtelling { font-size: var(--font-size-xl); }
  }

  @media (max-width: 560px) {
    .kort { padding: var(--size-4); }

    .resultat__rad {
      grid-template-columns: minmax(0, 1fr) auto;
    }

    .resultat__felt { grid-column: 1 / -1; }
    .resultat__ditt { grid-column: 1; }
    .resultat__dom { grid-column: 2; }
    /* Den sto igjen på `2 / -1`, altså den smale kolonnen ved siden av
       «Feil», og klemte ditt eget svar sammen. */
    .resultat__riktig { grid-column: 1 / -1; }

    /* Med 12rem til ledeteksten fikk verdien rundt 95 piksler, og
       «Ingen, saken var i orden» brakk over fem linjer. */
    .fasit__rad { grid-template-columns: 1fr; gap: 0; }
  }
  """
    .trimIndent()
