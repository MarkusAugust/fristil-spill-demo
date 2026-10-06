/**
 * Skallet.
 *
 * Tre rammer side om side, én per utgave, og ingenting annet. Ingen
 * avhengigheter og ingen byggesteg: en side som skal vise fram tre rammeverk
 * kan ikke være reklame for et fjerde.
 *
 * Skallet snakker ikke med spilltjeneren. Det vet ikke hva en sak er, og har
 * ingen tilstand. Hver ramme er en helt vanlig adresse du også kan åpne
 * alene, og det er poenget: rammene er ikke en spesialutgave av appene.
 */

/** Versjonen av Fristil skallet henter tokens fra. Samme som appene bruker. */
const FRISTIL = "0.30.0"

/**
 * Temaet, den samme fila som de tre appene serverer.
 *
 * Skallet er rammen rundt de tre, og uten temaet sto rammen i system-ui mens
 * alt inni sto i Helvetica. Det er nettopp den siden som skal vise at de tre
 * er like, så den kan ikke være den ene som skiller seg ut.
 */
const TEMA_CSS = await Bun.file(
  process.env.TEMA_CSS_FIL ?? "../../felles/tema.css",
).text()

const PORT = Number(process.env.PORT ?? 8084)
const HOST = process.env.HOST ?? "::"

type Utgave = { navn: string; rammeverk: string; adresse: string }

const UTGAVER: Utgave[] = [
  {
    navn: "TanStack Start",
    rammeverk: "React. Serveren sender JSON, nettleseren tegner.",
    adresse: process.env.TANSTACK_URL ?? "http://localhost:8082",
  },
  {
    navn: "Datastar",
    rammeverk: "Kotlin. Serveren sender ferdige HTML-biter.",
    adresse: process.env.DATASTAR_URL ?? "http://localhost:8081",
  },
  {
    navn: "Astro",
    rammeverk: "Hele sider fra serveren, med én liten øy.",
    adresse: process.env.ASTRO_URL ?? "http://localhost:8083",
  },
]

/** HTML-koder tekst som kommer utenfra, altså fra miljøvariablene. */
function trygg(tekst: string): string {
  return tekst
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
}

const SIDE = `<!doctype html>
<html lang="nb">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Førstelinja · tre utgaver side om side</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/@fristil/designsystem@${FRISTIL}/src/tokens/tokens.css">
<link rel="stylesheet" href="/tema.css">
<style>
  /* Skallet eier bare rammene rundt. Fargene og avstandene er Fristils egne
     tokens, slik at kanten rundt rammene ikke sier noe annet enn innholdet. */
  * { box-sizing: border-box; }

  /* Skallet har ingen temavelger og følger systemet. Fristil setter ikke
     lenger \`color-scheme\` på rota, så uten denne fikk en mørk maskin lyse
     rullefelt rundt rammene. */
  :root { color-scheme: light dark; }

  body {
    margin: 0;
    min-block-size: 100vh;
    display: flex;
    flex-direction: column;
    font-family: var(--fs-font-family-base, system-ui, sans-serif);
    color: var(--fs-color-neutral-text-strong);
    /* Sideflaten med et snev av tekstfargen i seg, som i brett.css. Her sto
       det en gang semantic-page-subtle, et token som ikke finnes, og da er
       hele regelen ugyldig og flaten gjennomsiktig. */
    background: color-mix(
      in oklab,
      var(--fs-color-neutral-text-strong) 5%,
      var(--fs-color-neutral-canvas)
    );
  }

  header {
    display: flex;
    flex-wrap: wrap;
    gap: var(--fs-spacing-3);
    align-items: baseline;
    justify-content: space-between;
    padding: var(--fs-spacing-3) var(--fs-spacing-5);
    border-block-end: 1px solid var(--fs-color-neutral-border-subtle);
    background: var(--fs-color-neutral-canvas);
  }

  h1 {
    margin: 0;
    font-size: var(--fs-font-size-m);
    font-weight: 600;
    letter-spacing: 0.01em;
  }

  header p {
    margin: 0;
    max-inline-size: 60ch;
    font-size: var(--fs-font-size-s);
    color: var(--fs-color-neutral-text-subtle);
  }

  .rammer {
    flex: 1;
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 1px;
    background: var(--fs-color-neutral-border-subtle);
    min-block-size: 0;
  }

  .ramme {
    display: flex;
    flex-direction: column;
    min-inline-size: 0;
    background: var(--fs-color-neutral-canvas);
  }

  .ramme__topp {
    display: flex;
    gap: var(--fs-spacing-2);
    align-items: baseline;
    justify-content: space-between;
    padding: var(--fs-spacing-2) var(--fs-spacing-3);
    border-block-end: 1px solid var(--fs-color-neutral-border-subtle);
  }

  .ramme__navn {
    font-size: var(--fs-font-size-s);
    font-weight: 600;
  }

  .ramme__om {
    font-size: var(--fs-font-size-xs);
    color: var(--fs-color-neutral-text-subtle);
  }

  .ramme__lenke {
    font-size: var(--fs-font-size-xs);
    color: var(--fs-color-accent-text);
  }

  iframe {
    flex: 1;
    inline-size: 100%;
    border: 0;
    min-block-size: 32rem;
  }

  /* Under 64rem er tre kolonner ikke tre kolonner lenger, bare tre striper.
     Da står de heller under hverandre, og hver ramme får plass til å være
     en ekte side. */
  @media (max-width: 64rem) {
    .rammer { grid-template-columns: 1fr; }
  }
</style>
</head>
<body>
  <header>
    <h1>Førstelinja, tre utgaver samtidig</h1>
    <p>
      Den samme saken, det samme skjemaet og det samme designsystemet, tegnet
      av tre teknologier som ikke har noe til felles. Svar i én ramme, så ser
      du det skje i de to andre.
    </p>
  </header>

  <main class="rammer">
    ${UTGAVER.map(
      (u) => `<section class="ramme">
      <div class="ramme__topp">
        <span class="ramme__navn">${trygg(u.navn)}</span>
        <span class="ramme__om">${trygg(u.rammeverk)}</span>
        <a class="ramme__lenke" href="${trygg(u.adresse)}" target="_blank" rel="noreferrer">Åpne alene</a>
      </div>
      <iframe src="${trygg(u.adresse)}" title="Førstelinja i ${trygg(u.navn)}" loading="lazy"></iframe>
    </section>`,
    ).join("\n    ")}
  </main>
</body>
</html>
`

Bun.serve({
  port: PORT,
  hostname: HOST,
  fetch(request) {
    const url = new URL(request.url)
    if (url.pathname === "/helse") return new Response("ok")
    if (url.pathname === "/tema.css") {
      return new Response(TEMA_CSS, { headers: { "content-type": "text/css" } })
    }
    if (url.pathname !== "/") return new Response("Ikke funnet", { status: 404 })
    return new Response(SIDE, { headers: { "content-type": "text/html; charset=utf-8" } })
  },
})

console.log(`Skallet kjører på http://localhost:${PORT}`)
