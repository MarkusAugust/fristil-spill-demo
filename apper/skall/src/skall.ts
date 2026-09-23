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
const FRISTIL = "0.11.0"

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
<style>
  /* Skallet eier bare rammene rundt. Fargene og avstandene er Fristils egne
     tokens, slik at kanten rundt rammene ikke sier noe annet enn innholdet. */
  * { box-sizing: border-box; }

  body {
    margin: 0;
    min-block-size: 100vh;
    display: flex;
    flex-direction: column;
    font-family: var(--font-family-base, system-ui, sans-serif);
    color: var(--semantic-page-foreground);
    background: var(--semantic-page-subtle);
  }

  header {
    display: flex;
    flex-wrap: wrap;
    gap: var(--size-3);
    align-items: baseline;
    justify-content: space-between;
    padding: var(--size-3) var(--size-5);
    border-block-end: 1px solid var(--semantic-divider-30);
    background: var(--semantic-page-background);
  }

  h1 {
    margin: 0;
    font-size: var(--font-size-m);
    font-weight: 600;
    letter-spacing: 0.01em;
  }

  header p {
    margin: 0;
    max-inline-size: 60ch;
    font-size: var(--font-size-s);
    color: var(--semantic-page-muted);
  }

  .rammer {
    flex: 1;
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 1px;
    background: var(--semantic-divider-30);
    min-block-size: 0;
  }

  .ramme {
    display: flex;
    flex-direction: column;
    min-inline-size: 0;
    background: var(--semantic-page-background);
  }

  .ramme__topp {
    display: flex;
    gap: var(--size-2);
    align-items: baseline;
    justify-content: space-between;
    padding: var(--size-2) var(--size-3);
    border-block-end: 1px solid var(--semantic-divider-30);
  }

  .ramme__navn {
    font-size: var(--font-size-s);
    font-weight: 600;
  }

  .ramme__om {
    font-size: var(--font-size-xs);
    color: var(--semantic-page-muted);
  }

  .ramme__lenke {
    font-size: var(--font-size-xs);
    color: var(--semantic-interactive-main);
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
    if (url.pathname !== "/") return new Response("Ikke funnet", { status: 404 })
    return new Response(SIDE, { headers: { "content-type": "text/html; charset=utf-8" } })
  },
})

console.log(`Skallet kjører på http://localhost:${PORT}`)
