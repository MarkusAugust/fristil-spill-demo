import { defineFs } from "@fristil/designsystem/register"

import { tavlekropp } from "./lib/tavle"
import type { TavleRad } from "./lib/tilstand"

/**
 * Øya.
 *
 * Dette er alt skriptet denne appen har. Siden kommer ferdig fra serveren,
 * og øya gjør de fire tingene serveren ikke kan gjøre for den:
 *
 * 1. registrerer web-komponentene, som tar seg av faner, forslagsliste,
 *    sprettoppvindu og dialog;
 * 2. teller ned fristen;
 * 3. holder tavla i takt, av JSON fra hendelsesstrømmen;
 * 4. henter en ny side når runden er en annen enn den siden ble tegnet med.
 *
 * Merk hva som ikke står her: ingen skjemahåndtering, ingen validering,
 * ingen tilstand. Det ligger i serveren, og skjemaet er en vanlig POST.
 */

defineFs()

/* --- Temaet ------------------------------------------------------- */

/*
 * Valget ligger i en kapsel, ikke i `localStorage`, så serveren kan skrive
 * `data-theme` selv når den tegner siden. Det fjerner lysglimtet, og valget
 * følger med til de to andre utgavene.
 */
const valgt = document.cookie.match(/(?:^|;\s*)forstelinja-tema=([^;]*)/)?.[1] ?? "system"

for (const knapp of document.querySelectorAll<HTMLInputElement>('input[name="tema"]')) {
  knapp.checked = knapp.value === valgt
  knapp.addEventListener("change", () => {
    if (!knapp.checked) return
    // `SameSite=None` på https: i skallet står utgavene i hver sin ramme, på
    // hvert sitt domene, og en `Lax`-kapsel sendes ikke derfra.
    const tvers =
      location.protocol === "https:" ? "SameSite=None; Secure" : "SameSite=Lax"
    document.cookie = `forstelinja-tema=${knapp.value}; Path=/; Max-Age=${60 * 60 * 24 * 365}; ${tvers}`
    if (knapp.value === "system") document.documentElement.removeAttribute("data-theme")
    else document.documentElement.setAttribute("data-theme", knapp.value)
    merkLenkene(knapp.value)
  })
}

/*
 * Lenkene til de to andre utgavene bærer valget ditt videre.
 *
 * Serveren skriver dem med `?tema=` ved sidelasting, og det holder helt til
 * du bytter tema etterpå: da står lenkene igjen med det gamle valget, og du
 * fikk systemtemaet i den andre utgaven. Kapselen gjelder bare for sitt eget
 * domene i drift, så lenka er det eneste som kan bære valget over.
 */
function merkLenkene(tema: string) {
  for (const lenke of document.querySelectorAll<HTMLAnchorElement>(
    ".utgavevelger__lenke, .velkomst__lenke",
  )) {
    const adresse = new URL(lenke.href)
    if (tema === "system") adresse.searchParams.delete("tema")
    else adresse.searchParams.set("tema", tema)
    lenke.href = adresse.toString()
  }
}

/* --- Nedtellingen ------------------------------------------------- */

const klokke = document.querySelector<HTMLElement>("[data-nedtelling]")

/*
 * Fristen er et absolutt tidspunkt fra serveren, og maskinen her kan gå feil.
 * Avviket regnes ut mot serverens egen klokke slik den sto da siden ble
 * tegnet, og legges til i hver avlesning.
 */
let avvik = klokke?.dataset.na ? Number(klokke.dataset.na) - Date.now() : 0

function tellNed() {
  if (!klokke) return
  const frist = Number(klokke.dataset.frist ?? 0)
  const lengde = Number(klokke.dataset.lengde ?? 0)
  if (!frist) return

  const igjen = Math.max(0, Math.round((frist - (Date.now() + avvik)) / 1000))
  klokke.textContent =
    klokke.dataset.nedtelling === "klokke"
      ? `${Math.floor(igjen / 60)}:${String(igjen % 60).padStart(2, "0")}`
      : String(igjen)

  const del = lengde > 0 ? Math.min(1, Math.max(0, (igjen * 1000) / lengde)) : 1
  const roedt = Math.round(Math.max(0, 1 - del * 2) * 100)
  klokke.style.color = `color-mix(in oklab, var(--spill-frist-slutt) ${roedt}%, var(--spill-frist-start))`

  // Under ti sekunder begynner klokka å riste, og rister mer for hvert
  // sekund. Styrken er et tall; utslaget og farten står i CSS-en, sammen med
  // sperren for den som har bedt om mindre bevegelse.
  const rist = igjen > 0 && igjen <= 10 ? (10 - igjen) / 9 : 0
  klokke.style.setProperty("--spill-rist", rist.toFixed(2))
  if (rist > 0) klokke.setAttribute("data-rister", "")
  else klokke.removeAttribute("data-rister")
}

tellNed()
setInterval(tellNed, 250)

/* --- Fasiten kan åpnes igjen -------------------------------------- */

document.querySelector<HTMLButtonElement>("[data-apne-resultat]")?.addEventListener("click", () => {
  document.getElementById("resultat")?.setAttribute("open", "")
})

/* --- Navnet over en ny side --------------------------------------- */

/*
 * Ved et faseskifte henter øya en ny side, og et navn halvveis skrevet i
 * påmeldingen var borte. TanStack beholdt det, så det var nettopp en
 * forskjell demoen sier at du ikke skal se. Navnet legges i
 * `sessionStorage` rett før, og settes tilbake når siden er lastet. Det
 * gjelder bare én lasting: nøkkelen fjernes med en gang den er lest.
 *
 * Lagringen kan mangle eller kaste i et privat vindu, og da mister brukeren
 * navnet som før, men ingenting annet går i stykker.
 */
const NAVNENOKKEL = "forstelinja-navn"
const navnefelt = () =>
  document.querySelector<HTMLInputElement>('form[action="/bli-med"] input[name="navn"]')

try {
  const lagret = sessionStorage.getItem(NAVNENOKKEL)
  sessionStorage.removeItem(NAVNENOKKEL)
  const felt = navnefelt()
  if (lagret && felt && !felt.value) {
    const { verdi, fokus } = JSON.parse(lagret) as { verdi: string; fokus: boolean }
    felt.value = verdi
    if (fokus) {
      felt.focus()
      felt.setSelectionRange(verdi.length, verdi.length)
    }
  }
} catch {}

const taVarePaNavnet = () => {
  const felt = navnefelt()
  if (!felt?.value) return
  try {
    sessionStorage.setItem(
      NAVNENOKKEL,
      JSON.stringify({ verdi: felt.value, fokus: document.activeElement === felt }),
    )
  } catch {}
}

/* --- Strømmen ----------------------------------------------------- */

type Puls = {
  fase: string
  rundeNr: number
  sakId: string
  fristMs: number
  naMs: number
  harSvart: boolean
  tavle: TavleRad[]
}

const brett = document.getElementById("brett")
const status = () =>
  document.querySelector<
    HTMLElement & { reportFailure(): void; reportSuccess(): void }
  >("fs-connection-status")

if (brett) {
  const kilde = new EventSource("/hendelser")

  kilde.addEventListener("puls", (hendelse) => {
    const puls = JSON.parse((hendelse as MessageEvent<string>).data) as Puls
    status()?.reportSuccess()

    /*
     * Er runden en annen enn den siden ble tegnet med, er hele siden
     * utdatert: ny sak, nytt skjema, ny fasit. Da henter vi den på nytt, og
     * det er akkurat det denne appen skal gjøre.
     *
     * `location.replace("/")` og ikke `location.reload()`. Siden kan være
     * resultatet av en POST, og da sender `reload()` den samme innsendingen
     * en gang til. Det var ikke teoretisk: ved hvert rundeskifte ble det
     * forrige vedtaket sendt inn på den nye saken, og hadde skjemaet
     * valideringsfeil, kom den samme feilmeldingen tilbake på en sak
     * brukeren ikke hadde sett. Astro ble stående mens de to andre gikk
     * videre. `replace` henter alltid med GET, og legger ikke en ny
     * oppføring i historikken, så tilbakeknappen oppfører seg som før.
     */
    if (
      puls.fase !== brett.dataset.fase ||
      String(puls.rundeNr) !== brett.dataset.runde ||
      puls.sakId !== brett.dataset.sak ||
      String(puls.harSvart) !== brett.dataset.svart
    ) {
      taVarePaNavnet()
      location.replace("/")
      return
    }

    // Ellers er det bare tavla som har endret seg, og den kan øya bytte ut.
    avvik = puls.naMs - Date.now()
    if (klokke) klokke.dataset.frist = String(puls.fristMs)

    const kropp = document.querySelector("[data-tavle]")
    if (kropp) kropp.innerHTML = tavlekropp(puls.tavle, puls.fase !== "runde")

    /*
     * Tellingen leses av tavla, som i sidetegningen og de to andre appene.
     * Den sto på spilltjenerens `harSvart`, som bare teller dem som var med
     * da saken kom på bordet. Den som meldte seg på midt i runden, kan
     * levere og står som «Levert» på tavla, men telte ikke, og i skallet
     * sto Astro på «0 av 5» mens de to andre viste «2 av 5».
     */
    const levert = document.querySelector<HTMLElement>("[data-levert]")
    if (levert) {
      const paVakt = puls.tavle.length
      const antall = puls.tavle.filter((r) => r.harSvart).length
      const alle = antall >= paVakt
      levert.hidden = paVakt <= 1
      levert.dataset.alle = String(alle)
      levert.textContent = alle
        ? "Alle har levert. Oppgjøret kommer straks."
        : `${antall} av ${paVakt} saksbehandlere har levert.`
    }
  })

  kilde.addEventListener("samband", () => status()?.reportFailure())
  kilde.onerror = () => status()?.reportFailure()
}
