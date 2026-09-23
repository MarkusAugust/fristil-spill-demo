import { chromium } from "playwright"

/**
 * At de tre utgavene oppfører seg likt.
 *
 * Dette er hele påstanden til demoen, og den eneste testen som kan si om den
 * holder. Den kjører det samme løpet i hver app: melde seg på, fylle ut
 * vedtaket, se kvitteringen, og sjekke at komponentene gjør jobben sin.
 *
 * Testen vet ingenting om hvordan appene er bygget, og skal ikke vite det.
 * Den ser bare på det brukeren ser: roller, ledetekster og synlige
 * elementer. En test som lette etter appens egne id-er ville sagt mer om
 * hvordan de er skrevet enn om de gjør det samme.
 *
 * Appene må kjøre først:
 *
 *     ./kjor.sh rask
 *     cd tester && bun install && bun run paritet
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

  /*
   * Testen melder seg på som en test, ikke som en saksbehandler.
   *
   * Uten dette ble «Test datastar» stående i den evige topplista: `/ga-av`
   * tar spilleren av tavla, men tok vakta slutt mens testen fortsatt sto der,
   * var poengene alt lagret, og den lista er det eneste som overlever en
   * omstart.
   *
   * En kapsel, og ikke en overskrift. To forsøk feilet før dette: en
   * overskrift på hele konteksten fulgte også med til CDN-en, og en
   * egendefinert overskrift gjør en forespørsel over domenegrensen til en som
   * krever forhåndssjekk, så web-komponentene ble blokkert og Kotlin-utgaven
   * ble felt for noe appen ikke gjør. Å sette overskriften bare på
   * påmeldingen krevde at testen avskar selve navigeringen, og det ødela
   * kapselhåndteringen i React-utgaven, som mistet temaet sitt. Kapselen går
   * bare til appens eget domene og rører ingen av delene.
   */
  await kontekst.addCookies([
    { name: "forstelinja-test", value: "1", url: app.url },
  ])

  /*
   * En oppvarming først, og så tas feilene.
   *
   * Vite optimaliserer avhengighetene sine ved første forespørsel, og svarer
   * «504 Outdated Optimize Dep» på sine egne moduler mens den holder på. Den
   * første lastingen etter at dev-serveren er startet feilet derfor i
   * React-utgaven, og testen meldte avvik på noe som var i orden ved neste
   * runde. Mot en utrullet app koster oppvarmingen én ekstra lasting og
   * ingenting annet.
   */
  await side.goto(app.url, { waitUntil: "domcontentloaded" }).catch(() => {})
  await side.waitForTimeout(1500)

  side.on("pageerror", (e) => si(`sidefeil: ${e.message}`))
  side.setDefaultTimeout(20_000)
  side.on("console", (m) => {
    if (m.type() === "error") si(`konsollfeil: ${m.text().slice(0, 200)}`)
  })

  try {
    // `networkidle` går aldri i mål her: alle tre holder en åpen
    // hendelsesstrøm. Vi venter på det som faktisk skal stå der i stedet.
    await side.goto(app.url, { waitUntil: "domcontentloaded" })

    /*
     * Vent på `:modal`, ikke på at dialogen er synlig.
     *
     * Serveren sender dialogen med `open`, så den er synlig som en boks på
     * siden allerede før noe skript har kjørt. Det er med vilje: uten
     * JavaScript er innholdet ellers borte. Men det betyr at «synlig» kommer
     * før komponenten har gjort den modal, og en test som venter på synlighet
     * tar bildet for tidlig. `:modal` er forskjellen på en ekte modal og en
     * boks: bare den første gjør resten av siden utilgjengelig.
     */
    await side
      .waitForFunction(
        () => document.querySelector("#velkomst dialog")?.matches(":modal") === true,
        undefined,
        { timeout: 15000 },
      )
      .catch(() => si("velkomsthilsenen ble aldri en modal dialog"))

    await side.getByRole("button", { name: "Jeg merker nok forskjellen" }).click()

    /*
     * Feltet i påmeldingsskjemaet er koblet av komponenten, ikke av serveren.
     *
     * Serveren sender bare struktur her: en ledetekst, et felt og en
     * hjelpetekst, uten id-er og uten `for`. `<fs-field>` lager koblingen i
     * nettleseren. Fram til Fristil 0.9.0 måtte malen liste opp attributtene
     * i `data-preserve-attr`, ellers rev morfingen dem bort. Nå reparerer
     * komponenten seg selv, og da må koblingen faktisk stå her.
     */
    const kobling = await side.evaluate(() => {
      const felt = document.querySelector("fs-field")
      const ledetekst = felt?.querySelector("label")
      const kontroll = felt?.querySelector("input")
      if (!ledetekst || !kontroll) return "fant ikke feltet"
      if (!kontroll.id) return "kontrollen fikk ingen id"
      if (ledetekst.htmlFor !== kontroll.id) return "ledeteksten peker ikke på feltet"
      for (const id of (kontroll.getAttribute("aria-describedby") ?? "").split(/\s+/).filter(Boolean)) {
        if (!document.getElementById(id)) return `aria-describedby peker på #${id}, som ikke finnes`
      }
      if (!ledetekst.classList.contains("fs-label")) return "ledeteksten mangler fs-label"
      return "ok"
    })
    if (kobling !== "ok") si(`påmeldingsfeltet: ${kobling}`)

    await side.getByLabel("Navnet ditt").fill(`Test ${app.navn}`)
    await side.getByRole("button", { name: "Begynn vakta" }).click()
    await side.locator("#tavle").waitFor({ timeout: 15000 })

    // Temaet er brukerens valg, og skal overleve at siden lastes på nytt.
    // Selve radioknappen ligger under merkelappen, som i en ekte knapperad.
    await side.locator(".temavelger label", { hasText: "Mørkt" }).click()

    /*
     * Vent på attributtet, ikke les det i samme åndedrag som klikket.
     *
     * De tre utgavene setter temaet på hver sin måte: Datastar får en patch
     * fra serveren, Astro laster siden på nytt, og React oppdaterer i
     * nettleseren etter at kapselen er skrevet. Avlesningen rett etter
     * klikket traff derfor før React var ferdig, og testen meldte avvik på
     * noe som virket et øyeblikk senere. En test som feiler tilfeldig blir
     * ignorert.
     */
    await side
      .waitForFunction(
        () => document.documentElement.dataset.theme === "dark",
        undefined,
        { timeout: 10000 },
      )
      .catch(() => si("temavelgeren satte ikke data-theme"))
    await side.reload({ waitUntil: "domcontentloaded" })
    await side.locator("#tavle").waitFor({ timeout: 15000 })
    await side
      .waitForFunction(
        () => document.documentElement.dataset.theme === "dark",
        undefined,
        { timeout: 10000 },
      )
      .catch(() => si("temaet overlevde ikke en ny side"))

    // Nedtellingen skal telle, ikke bare stå der.
    const forst = await side.locator(".nedtelling").first().textContent()
    await side.waitForTimeout(2500)
    if ((await side.locator(".nedtelling").first().textContent()) === forst) {
      si(`nedtellingen står stille på ${forst}`)
    }

    /*
     * Vent på at en runde er i gang, ikke på klokka.
     *
     * Testen kan lande midt i et oppgjør, og da finnes ikke skjemaet. En
     * test som bare hopper over skjemaet da, sier ingenting halve tiden.
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


  } catch (e) {
    // En feil her sier hvilken app det gjaldt. Uten dette kom bare en
    // stakksporing fra Playwright, uten et ord om hvilken av de tre.
    si(`stoppet underveis: ${(e as Error).message.split("\n")[0]}`)
    await side.screenshot({ path: `${app.navn}-stoppet.png` }).catch(() => {})
  }

  /*
   * Og så av vakt igjen.
   *
   * Uten dette sto «Test datastar», «Test tanstack» og «Test astro» igjen
   * på tavla i seks minutter etter hver kjøring, og telte med i «tre av fire
   * saksbehandlere har levert». En test som skitner til det den tester er
   * en dårlig test.
   */
  await side
    .evaluate(() => fetch("/ga-av", { method: "POST" }).then(() => undefined))
    .catch(() => si("kom ikke av vakt igjen"))

  await kontekst.close()
}

await nettleser.close()

if (funn.length > 0) {
  console.error(`Fant ${funn.length} avvik:\n${funn.map((f) => `  - ${f}`).join("\n")}`)
  process.exit(1)
}
console.log("De tre utgavene oppfører seg likt.")
