package no.fristil.forstelinja.datastar

/**
 * Oppsettet på brettet, og ingenting mer.
 *
 * Regelen for hele demoen: alt du ser er Fristils egne komponenter og
 * tokens. Den eneste CSS-en som skrives her er hvor boksene ligger. Ellers
 * viser siden fram et spill framfor et designsystem.
 *
 * Verdiene er Fristils tokens, ikke egne tall.
 */
val BRETT_CSS =
  """
  body {
    margin: 0;
    padding: var(--size-4);
    /* Fristil arver skrift med vilje, så appen må si hvilken. Uten dette
       er det nettleserens egen, som er en serif. */
    font-family:
      system-ui,
      -apple-system,
      "Segoe UI",
      Roboto,
      sans-serif;
    color: var(--semantic-page-foreground);
    background: var(--semantic-neutral-background);
  }

  .topp {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: var(--size-3);
    margin-bottom: var(--size-4);
  }

  .topp__fase { margin: 0; font-weight: 600; }
  .topp__tid { margin: 0; color: var(--semantic-muted-foreground); }

  .brett__innhold {
    display: grid;
    grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
    gap: var(--size-4);
    align-items: start;
  }

  .kort { padding: var(--size-4); }

  /* `.fs-card` er en flex-kolonne, så en `inline-flex` merkelapp strekkes
     over hele bredden. Den skal ta plassen den trenger. */
  .kort > .fs-tag { align-self: start; }

  .poeng { font-size: var(--font-size-l); }

  .tavle .meg { font-weight: 600; }

  @media (max-width: 720px) {
    .brett__innhold { grid-template-columns: 1fr; }
  }
  """
    .trimIndent()
