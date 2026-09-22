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
  :root {
    /* Uten denne tegner nettleseren sine egne kontroller, rullefelt og
       nedtrekkslister i lyst utseende selv når siden er mørk. */
    color-scheme: light dark;
  }

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

  .topplinje__innhold {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--size-3) var(--size-5);
    padding-block: var(--size-3);
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

  .topplinje .nedtelling {
    font-size: var(--font-size-xxl);
    font-weight: 600;
    /* Tallene skal ikke hoppe sidelengs mens sekundene går. */
    font-variant-numeric: tabular-nums;
  }

  .topplinje .nedtelling--knapt {
    color: var(--palette-ochre-30);
  }

  .topplinje__stack {
    padding: var(--size-0-5) var(--size-2);
    font-size: var(--font-size-xs);
    border: 1px solid color-mix(in oklab, var(--palette-graphite-0) 45%, transparent);
    border-radius: var(--size-1);
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

  /* `.fs-card` er en flex-kolonne, så en `inline-flex` merkelapp strekkes
     over hele bredden. Den skal ta plassen den trenger. */
  .kort > .fs-tag { align-self: start; }

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

  @media (prefers-color-scheme: dark) {
    /* Her snus forholdet: sideflaten er den mørkeste, og kortene løftes.
       Samme regel begge veier ville gitt kort som er mørkere enn siden. */
    body { background: var(--semantic-page-background); }

    .fs-card {
      background: color-mix(in oklab, var(--semantic-page-foreground) 8%, var(--semantic-page-background));
      border-color: color-mix(in oklab, var(--semantic-page-foreground) 18%, transparent);
    }
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
  }
  """
    .trimIndent()
