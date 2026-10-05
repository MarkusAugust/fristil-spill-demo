import { chromium, type Page } from "playwright"

/**
 * At tre spillere i hver sin utgave ser den samme tavla, samtidig.
 *
 * `paritet.ts` kjører utgavene etter hverandre, med én spiller om gangen,
 * og kan derfor ikke se det som bare skjer når de deler tavle. Det var der
 * Astro hang igjen: øya talte leveringene med spilltjenerens `harSvart`, som
 * bare teller dem som var med da saken kom på bordet. De to andre, og Astros
 * egen sidetegning, teller radene på tavla. Den som meldte seg på midt i
 * runden, kunne levere og sto som «Levert», men i skallet sto Astro på
 * «0 av 5» mens de to andre viste «2 av 5», helt til fasen skiftet.
 *
 * Og telleren fantes bare når to eller flere var på vakt da siden ble
 * tegnet. Den som sto alene i Astro og fikk selskap, så den aldri.
 *
 * Løpet er derfor: Astro-spilleren møter først, de to andre midt i runden,
 * og så leverer de én om gangen. Etter hvert steg skal alle tre vise den
 * samme teksten, og tallet skal ha gått opp med én.
 *
 * De som møter sent, skriver navnet sitt før runden skifter, og trykker
 * først etterpå. Navnet skal stå der fortsatt. I Datastar og Astro var det
 * borte etter et faseskifte, mens TanStack beholdt det, og det var også
 * grunnen til at denne testen en stund feilet av og til.
 *
 *     ./kjor.sh rask
 *     cd tester && bun run samtidig
 */

const APPER = {
  astro: process.env.ASTRO_URL ?? "http://127.0.0.1:8083",
  datastar: process.env.DATASTAR_URL ?? "http://127.0.0.1:8081",
  tanstack: process.env.TANSTACK_URL ?? "http://localhost:8082",
}
const SPILLTJENER = process.env.SPILLTJENER ?? "http://127.0.0.1:8080"

/** Hvor lenge en endring får bruke på å nå alle tre. */
const FRIST_MS = 3000

const funn: string[] = []
const nettleser = await chromium.launch()
const sider: Partial<Record<keyof typeof APPER, Page>> = {}
// Fast rekkefølge, så meldingene er lette å lese.
const NAVN = ["astro", "datastar", "tanstack"] as const
const alle = () => NAVN.filter((n) => sider[n]).map((n) => sider[n]!)
const beskriv = (tekster: string[]) =>
  NAVN.map((n, i) => `${n} «${tekster[i]}»`).join(", ")

/**
 * Åpner siden, lukker hilsenen og skriver navnet, men melder ikke på.
 *
 * Delt fra påmeldingen fordi lastingen og hilsenen tok tjue sekunder for to
 * utgaver, og med runder på 30 sekunder var runden over før løpet kom i gang.
 */
const gjorKlar = async (navn: keyof typeof APPER, spillernavn = `Samtidig ${navn}`) => {
  const url = APPER[navn]
  const kontekst = await nettleser.newContext()
  // Kapselen holder testspilleren ute av den evige topplista, som i
  // `paritet.ts`.
  await kontekst.addCookies([{ name: "forstelinja-test", value: "1", url }])
  const side = await kontekst.newPage()
  side.setDefaultTimeout(20_000)
  // Vite svarer 504 på første lasting etter oppstart, se `paritet.ts`.
  for (let forsok = 0; forsok < 5; forsok++) {
    const ok = await side
      .goto(url, { waitUntil: "domcontentloaded" })
      .then((s) => s?.ok() ?? false)
      .catch(() => false)
    if (ok) break
    await side.waitForTimeout(1000)
  }
  await side.waitForFunction(
    () => document.querySelector("#velkomst dialog")?.matches(":modal") === true,
  )
  await side.getByRole("button", { name: "Jeg merker nok forskjellen" }).click()
  // Med fokus i feltet, slik en som skriver har det.
  await side.getByLabel("Navnet ditt").click()
  await side.getByLabel("Navnet ditt").pressSequentially(spillernavn)
  return side
}

/**
 * Sjekker at navnet fortsatt står i feltet, og fyller det inn igjen om
 * ikke, så resten av løpet kommer fram.
 */
const navnetStar = async (navn: string, side: Page, ventet: string) => {
  // Litt tålmodighet: i Astro settes navnet tilbake av øya, som kjører
  // først når hele siden er tolket, og topplinja kan synes før det.
  const felt = side.getByLabel("Navnet ditt")
  const slutt = Date.now() + 3000
  let verdi = await felt.inputValue()
  while (verdi !== ventet && Date.now() < slutt) {
    await side.waitForTimeout(100)
    verdi = await felt.inputValue()
  }
  if (verdi !== ventet) {
    funn.push(`${navn}: navnet i påmeldingen var «${verdi}» etter faseskiftet, ventet «${ventet}»`)
    await side.getByLabel("Navnet ditt").fill(ventet)
  }
}

/*
 * Påmeldingen er først tatt imot når spilleren står på tavla som «(deg)».
 * Tavla står der uansett, så å vente på den alene sa ingenting: en
 * påmelding som feilet, så ut som en kvittering.
 */
const meldPa = async (navn: keyof typeof APPER, side: Page) => {
  await side.getByRole("button", { name: "Begynn vakta" }).click()
  await side.locator("#tavle").getByText(`Samtidig ${navn}`).waitFor()
  await side.locator("#tavle").getByText("(deg)").waitFor()
  sider[navn] = side
}

/**
 * Telleren slik brukeren ser den, eller `null` når den ikke vises.
 *
 * Med en kort frist: en lokator venter ellers i 20 sekunder på et element
 * som ikke finnes, og det var nettopp det Astro manglet.
 */
const teller = (side: Page) =>
  side
    .getByText(/saksbehandlere har levert|Alle har levert/)
    .first()
    .evaluate(
      (el) => ((el as HTMLElement).checkVisibility() ? el.textContent!.trim() : null),
      undefined,
      { timeout: 250 },
    )
    .catch(() => null)

/**
 * Venter til alle tre viser det samme, og sier hva de viste.
 *
 * Med `godta` venter den også på at teksten er den ventede. Uten det kunne
 * den godta øyeblikket før en levering hadde nådd fram, der alle tre ennå
 * viste det forrige tallet.
 */
const likeTellere = async (godta: (t: string) => boolean = () => true): Promise<string[]> => {
  const slutt = Date.now() + FRIST_MS
  let siste: (string | null)[] = []
  while (Date.now() < slutt) {
    siste = await Promise.all(alle().map((s) => teller(s)))
    if (siste[0] && siste.every((t) => t === siste[0]) && godta(siste[0]))
      return siste as string[]
    await new Promise((f) => setTimeout(f, 150))
  }
  return siste.map((t) => t ?? "(ingen teller)")
}

const levertTall = (tekst: string, paVakt: number) =>
  tekst.startsWith("Alle") ? paVakt : Number(tekst.match(/^(\d+) av/)?.[1])

/** Det spilltjeneren selv har, til feilmeldingene. */
const spilltjenersTavle = async () => {
  const t = (await fetch(`${SPILLTJENER}/api/tilstand`).then((r) => r.json())) as {
    tavle: { navn: string; harSvart: boolean }[]
  }
  return t.tavle.map((r) => `${r.navn}${r.harSvart ? " levert" : ""}`).join(", ")
}

const klokka = async () =>
  (await fetch(`${SPILLTJENER}/api/tilstand`).then((r) => r.json())) as {
    fase: string
    rundeNr: number
    sak: { id: string }
    fristMs: number
    naMs: number
  }

/*
 * Null også når fristen er passert. Spilltjeneren flytter fasen på neste
 * tikk, så et øyeblikk står fasen som «runde» med frist i fortiden. Det
 * negative tallet ble lest som starten på en ny runde, og løpet begynte i
 * det runden tok slutt.
 */
const gjenstar = async () => {
  const t = await klokka()
  return t.fase === "runde" ? Math.max(0, t.fristMs - t.naMs) : 0
}

/*
 * Et rundeskifte nullstiller alle svar, og da teller utgavene riktig ned
 * igjen. Uten denne vakten ble det til et funn om at en levering forsvant.
 * Et løp som ikke fikk plass i én runde, er ikke et funn om appene.
 */
let runden = ""
const sammeRunde = async () => {
  const t = await klokka()
  const naa = `${t.rundeNr}:${t.sak.id}:${t.fase}`
  if (runden && naa !== runden)
    throw new Error(
      `runden tok slutt midt i løpet (${runden} ble ${naa}), så resten sier ingenting. Kjør igjen`,
    )
  runden = naa
}

try {
  // `venter` er en Astro-side som bare skriver navnet og aldri melder seg
  // på, så Astro også har noen som står i feltet når runden skifter.
  const [astro, datastar, tanstack, venter] = await Promise.all([
    gjorKlar("astro"),
    gjorKlar("datastar"),
    gjorKlar("tanstack"),
    gjorKlar("astro", "Venter i Astro"),
  ])
  await meldPa("astro", astro)

  /*
   * Resten av løpet må få plass i én runde, også med `./kjor.sh rask`, der
   * runden er 30 sekunder. Testen venter derfor på at en ny runde begynner,
   * og de to andre møter rett etter. Da er de fortsatt sene: de var ikke på
   * vakt da saken kom på bordet. Spilltjeneren spørres bare om klokka, ikke
   * om noe testen påstår.
   */
  // Fristen regnes av rundelengden, ikke av et fast antall forsøk: med
  // vanlige runder på to minutter ga hundre sekunder opp midt i runden, og
  // navnesjekken under ble grønn uten at noe faseskifte hadde skjedd.
  const ventTil = async (ferdig: (ms: number) => boolean, hva: string) => {
    const slutt = Date.now() + (await gjenstar()) + 90_000
    while (!ferdig(await gjenstar())) {
      if (Date.now() > slutt) throw new Error(`ventet forgjeves på ${hva}`)
      await astro.waitForTimeout(250)
    }
  }
  await ventTil((ms) => ms === 0, "at runden skulle ta slutt")
  await ventTil((ms) => ms > 0, "en ny runde")

  // Vent til sidene har tegnet den nye runden, og se at navnet overlevde.
  for (const side of [datastar, tanstack, venter])
    await side.locator(".topplinje__fase").getByText(/^Runde/).waitFor()
  await navnetStar("datastar", datastar, "Samtidig datastar")
  await navnetStar("tanstack", tanstack, "Samtidig tanstack")
  await navnetStar("astro", venter, "Venter i Astro")
  await sammeRunde()
  // De to andre møter midt i runden.
  await Promise.all([meldPa("datastar", datastar), meldPa("tanstack", tanstack)])

  /*
   * Grunnlinjen leses av Datastar, ikke av den første i lista: viser én
   * utgave ingen teller, skal resten av løpet fortsatt ha et tall å telle
   * fra. Andre som tilfeldigvis er på vakt, er med i tallet, og det er
   * derfor testen teller oppover framfor å vente et bestemt tall.
   */
  const forst = await likeTellere()
  await sammeRunde()
  if (new Set(forst).size !== 1)
    funn.push(`etter påmeldingen viste utgavene ulikt: ${beskriv(forst)}`)
  const referanse = (await teller(sider.datastar!)) ?? ""
  const paVakt = Number(referanse.match(/av (\d+)/)?.[1])
  if (!paVakt) throw new Error(`Datastar viste ingen teller å telle fra: «${referanse}»`)
  let levert = levertTall(referanse, paVakt)

  for (const navn of ["datastar", "tanstack"] as const) {
    const side = sider[navn]!
    await side.locator("#v-innvilget").check()
    await side.selectOption("#hjemmel", { index: 1 })
    await side.selectOption("#felle", "nei")
    await side.getByRole("button", { name: "Fatt vedtak" }).click()
    // Kvitteringen først: telleren skal leses etter en levering som er tatt
    // imot, ikke fra et klikk som kanskje ikke nådde fram.
    await side.getByText("Vedtaket er fattet").waitFor({ timeout: 10_000 })
    levert += 1

    const naa = await likeTellere((t) => levertTall(t, paVakt) === levert)
    await sammeRunde()
    if (new Set(naa).size !== 1)
      funn.push(`etter at ${navn} leverte, viste utgavene ulikt: ${beskriv(naa)}`)
    else if (levertTall(naa[0], paVakt) !== levert)
      funn.push(
        `etter at ${navn} leverte, sto det «${naa[0]}» i alle tre, ventet ${levert} levert. Spilltjeneren: ${await spilltjenersTavle()}`,
      )
  }
} catch (e) {
  // Fire linjer, så en lokator som gikk ut på tid sier hvilken den var.
  funn.push(
    `kom ikke gjennom løpet: ${(e as Error).message
      .split("\n")
      .map((l) => l.trim())
      .filter(Boolean)
      .slice(0, 4)
      .join(" | ")}`,
  )
} finally {
  for (const side of Object.values(sider))
    await side!
      .evaluate(() => fetch("/ga-av", { method: "POST" }).then(() => undefined))
      .catch(() => {})
  await nettleser.close()
}

if (funn.length > 0) {
  console.error(`Fant ${funn.length} avvik:\n${funn.map((f) => `  - ${f}`).join("\n")}`)
  process.exit(1)
}
console.log("Tre spillere i tre utgaver ser den samme telleren, steg for steg.")
