import { chromium } from "playwright"

/**
 * At de tre utgavene oppfører seg likt.
 *
 * Dette er hele påstanden til demoen, og den eneste prøven som kan si om den
 * holder. Den kjører det samme løpet i hver app: melde seg på, fylle ut
 * vedtaket, se kvitteringen, og sjekke at komponentene gjør jobben sin.
 *
 * Prøven vet ingenting om hvordan appene er bygget, og skal ikke vite det.
 * Den ser bare på det brukeren ser: roller, ledetekster og synlige
 * elementer. En prøve som lette etter appens egne id-er ville sagt mer om
 * hvordan de er skrevet enn om de gjør det samme.
 *
 * Appene må kjøre først:
 *
 *     ./kjor.sh rask
 *     cd prover && bun install && bun run paritet
 */

const APPER = [
  { navn: "datastar", url: process.env.DATASTAR_URL ?? "http://127.0.0.1:8081" },
  { navn: "tanstack", url: process.env.TANSTACK_URL ?? "http://localhost:8082" },
  { navn: "astro", url: process.env.ASTRO_URL ?? "http://127.0.0.1:8083" },
]

const funn: string[] = []

const nettleser = await chromium.launch()

for (const app of APPER) {
  const si = (melding: string) => funn.push(`${app.navn}: ${melding}`)
  const kontekst = await nettleser.newContext()
  const side = await kontekst.newPage()

  side.on("pageerror", (e) => si(`sidefeil: ${e.message}`))
  side.on("console", (m) => {
    if (m.type() === "error") si(`konsollfeil: ${m.text().slice(0, 200)}`)
  })

  // `networkidle` går aldri i mål her: alle tre holder en åpen
  // hendelsesstrøm. Vi venter på det som faktisk skal stå der i stedet.
  await side.goto(app.url, { waitUntil: "domcontentloaded" })
  await side.locator("#velkomst dialog").waitFor({ state: "visible", timeout: 15000 })

  // `:modal` og ikke `.open`: forskjellen på en ekte modal og en boks på
  // siden er at resten av siden blir utilgjengelig.
  const modal = await side.evaluate(
    () => document.querySelector("#velkomst dialog")?.matches(":modal") ?? false,
  )
  if (!modal) si("velkomsthilsenen er ikke en modal dialog")

  await side.getByRole("button", { name: "Jeg merker nok forskjellen" }).click()
  await side.getByLabel("Navnet ditt").fill(`Prøve ${app.navn}`)
  await side.getByRole("button", { name: "Begynn vakta" }).click()
  await side.locator("#tavle").waitFor({ timeout: 15000 })

  // Temaet er brukerens valg, og skal overleve at siden lastes på nytt.
  // Selve radioknappen ligger under merkelappen, som i en ekte knapperad.
  await side.locator(".temavelger label", { hasText: "Mørkt" }).click()
  if ((await side.evaluate(() => document.documentElement.dataset.theme)) !== "dark") {
    si("temavelgeren satte ikke data-theme")
  }
  await side.reload({ waitUntil: "domcontentloaded" })
  await side.locator("#tavle").waitFor({ timeout: 15000 })
  if ((await side.evaluate(() => document.documentElement.dataset.theme)) !== "dark") {
    si("temaet overlevde ikke en ny side")
  }

  // Nedtellingen skal telle, ikke bare stå der.
  const forst = await side.locator(".nedtelling").first().textContent()
  await side.waitForTimeout(2500)
  if ((await side.locator(".nedtelling").first().textContent()) === forst) {
    si(`nedtellingen står stille på ${forst}`)
  }

  /*
   * Vent på at en runde er i gang, ikke på klokka.
   *
   * Prøven kan lande midt i et oppgjør, og da finnes ikke skjemaet. En
   * prøve som bare hopper over skjemaet da, sier ingenting halve tiden.
   * Appene henter en ny side eller tegner om av seg selv når runden
   * skifter, så det holder å vente på at feltet dukker opp.
   */
  await side
    .locator("#hjemmel")
    .waitFor({ timeout: 90_000 })
    .catch(() => si("ingen runde begynte på halvannet minutt"))

  if ((await side.locator("#hjemmel").count()) > 0) {
    // Forslagslista filtrerer selv, av komponenten, i alle tre appene.
    await side.locator("#kommune").click()
    await side.locator("#kommune").fill("Inder")
    await side.waitForTimeout(300)
    const treff = await side.locator("#kommune-list li:visible").count()
    if (treff === 0 || treff > 5) si(`forslagslista viste ${treff} treff på «Inder»`)

    // Fanene byttes med piltastene, av komponenten.
    await side.locator('[role="tab"]').first().focus()
    await side.keyboard.press("ArrowRight")
    const valgt = await side.locator('[role="tab"][aria-selected="true"]').textContent()
    if (valgt?.trim() !== "Søkeren") si(`piltast ga fanen «${valgt?.trim()}»`)

    // Og så et helt vedtak, som skal gi en kvittering.
    await side.locator("#v-innvilget").check()
    await side.selectOption("#hjemmel", { index: 1 })
    await side.locator("#kommune").fill("Inderøy")
    await side.selectOption("#felle", "nei")
    await side.getByRole("button", { name: "Fatt vedtak" }).click()

    await side
      .getByText("Vedtaket er fattet")
      .waitFor({ timeout: 15000 })
      .catch(() => si("ingen kvittering etter innsending"))
  }

  if ((await side.locator(".tavle__deg").count()) === 0) {
    si("fant ikke spilleren selv på tavla")
  }

  await kontekst.close()
}

await nettleser.close()

if (funn.length > 0) {
  console.error(`Fant ${funn.length} avvik:\n${funn.map((f) => `  - ${f}`).join("\n")}`)
  process.exit(1)
}
console.log("De tre utgavene oppfører seg likt.")
