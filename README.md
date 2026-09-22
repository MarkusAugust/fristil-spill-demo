# Førstelinja

Et spill som viser at det samme designsystemet, [Fristil](https://github.com/MarkusAugust/fristil), gir identisk brukergrensesnitt i tre helt ulike teknologier.

Alle spillerne er saksbehandlere i samme etat og behandler de samme sakene samtidig. Tavla viser hvilken app hver spiller sitter i:

```
TAVLE · runde 2 av 4
──────────────────────────────────
  1.  Kari      34 p    ● TanStack
  2.  Ola       28 p    ● Kotlin
  3.  Ingrid    21 p    ● Astro
```

Kari og Ola spiller sammen, i sanntid, fra to helt ulike stacker, og skjermene deres ser like ut. Det er hele påstanden til Fristil, demonstrert av folk som ikke tenker på den.

## Delene

```
                    ┌──────────────────────────┐
                    │  SPILLTJENEREN (Kotlin)  │
                    │   intern, bare JSON      │
                    └────┬────────┬────────┬───┘
        ┌────────────────┘        │        └────────────────┐
┌───────▼────────┐      ┌─────────▼────────┐      ┌─────────▼────────┐
│ TanStack Start │      │ Datastar (Kotlin)│      │      Astro       │
└───────┬────────┘      └─────────┬────────┘      └─────────┬────────┘
        │ JSON                    │ HTML-biter              │ ny side
┌───────▼────────┐      ┌─────────▼────────┐      ┌─────────▼────────┐
│    nettleser   │      │    nettleser     │      │    nettleser     │
└────────────────┘      └──────────────────┘      └──────────────────┘
```

Spilltjeneren eier reglene og tilstanden, og sender **bare JSON**. De tre appene eier hver sin presentasjon. Alle tre snakker med spilltjeneren fra sin egen server, aldri fra nettleseren: det er slik rammeverkene selv er ment å brukes, det holder spilltjeneren intern, og det gjør at den eneste forskjellen mellom appene er den vi vil vise fram.

| App | Ned til nettleseren | Hvor tegningen skjer |
| --- | --- | --- |
| TanStack Start | JSON | i nettleseren |
| Datastar | ferdige HTML-biter | på serveren |
| Astro | en ny side, og JSON til én liten øy | på serveren |

Spilltjeneren er med vilje ikke den samme prosessen som Datastar-appen. Ellers ville de to andre vært klienter av Datastar-appen, og sammenligningen blitt skjev.

## Omgangen

Fire runder à 30 sekunder, med ti sekunders oppgjør mellom. Rundt tre minutter, og så begynner en ny.

**Ingen lobby.** Rundene går uavbrutt. Den som åpner adressen er med i neste runde innen et halvt minutt. En lobby ville betydd at den første som kom satt og ventet på noen som aldri kom.

Hver runde er én sak, og full pott er 20 poeng:

| | Poeng |
| --- | --- |
| Riktig vedtak | 10 |
| Riktig hjemmel | 5 |
| Riktig om fella | 5 |

Fella er et felt som er feil: en fødselsdato som ikke finnes, en kommune som ble slått sammen i 2012, en e-post uten krøllalfa. Å se at saken er i orden teller like mye som å se fella.

Poeng for riktig, ikke for raskest. En ren reflekskonkurranse ville latt nettverksmodellen avgjøre, og da hadde demoen bevist noe annet enn den skulle.

Når runden er over bytter saksområdet innhold der det står: fasit, din plassering, tavla, nedtelling. Oppgjøret kommer bevisst ikke i en dialog, for en dialog hvert 40. sekund er slitsom. Dialogen sparer vi til slutten av omgangen.

## Tilstand, og hvorfor det ikke er noen database

| Tilstand | Lever i | Hvor |
| --- | --- | --- |
| Sakene | for alltid, men er innhold | `felles/saker.json` |
| Spillere, runde, svar | minutter | minne |
| Evig toppliste | for alltid | SQLite |

En omgang varer tre minutter, altså kortere enn en utrulling. Alt annet enn topplista er kortere enn levetiden til prosessen. En database å drifte ville vært én ting til som kan feile under en demonstrasjon, og den lærer ingen noe om Fristil.

Starter prosessen på nytt, er omgangen borte og en ny begynner. Spillerne får beskjed gjennom `<fs-connection-status>` framfor en ødelagt skjerm.

Fristen sendes som et **absolutt tidspunkt**, ikke «30 sekunder igjen». Hver klient teller ned selv, så et forsinket bud flytter ikke fristen, og de tre appene kan ikke komme i utakt.

## Sakene

`felles/saker.json` er delt mellom alle fire delene, og er JSON og ikke TypeScript nettopp derfor: Kotlin og JavaScript leser den samme fila, og ingen av dem eier den. Små bokstaver i verdiene av samme grunn.

Arbeidet er ekte saksbehandling. Vitsen er saken:

> **2024/1187** · Søknad om å holde 14 høner i borettslag. Vedlagt uttalelse fra styret, som er negativ, og fra hanen, som ikke er det.

`SakerTest` avviser en fasit som viser til en hjemmel som ikke finnes, to saker med samme id, en felle som ikke er et felt, og en forklaring som er for kort til å være en forklaring.

## Kjøre lokalt

```bash
cd apper/spilltjener
./gradlew test          # reglene, med falsk klokke
./gradlew installDist
./build/install/spilltjener/bin/spilltjener
```

| Variabel | Standard | Til hva |
| --- | --- | --- |
| `PORT` | `8080` | |
| `HOST` | `::` | Railways private nett er IPv6 |
| `SAKER_FIL` | `../../felles/saker.json` | |
| `TOPPLISTE_FIL` | `toppliste.db` | på Railway: et volum |
| `RUNDE_MS` | `30000` | lengre runder når noen skal snakke over spillet |
| `OPPGJOR_MS` | `10000` | |
| `SLUTT_MS` | `20000` | |

## Railway

Fem tjenester i ett prosjekt:

| Tjeneste | Offentlig | Volum |
| --- | --- | --- |
| `spilltjener` | nei | ja, til SQLite |
| `tanstack` | ja | nei |
| `datastar` | ja | nei |
| `astro` | ja | nei |
| `skall` | ja | nei |

Appene når spilltjeneren på `spilltjener.railway.internal`, så den trenger aldri et offentlig domene.

To ting å passe på, notert før vi kom dit:

- **`felles/` og «root directory».** Setter man en root directory per tjeneste, henter Railway bare filer derfra, og da finnes ikke `felles/saker.json`. Tjenester som trenger den må settes opp som et delt monorepo, med byggkommandoer som kjører fra rota, eller få `SAKER_FIL` pekt et annet sted.
- **Uten volum forsvinner topplista** ved hver utrulling. Ett volum per tjeneste, og en tjeneste med volum får litt nedetid ved utrulling. For spilltjeneren er det uproblematisk, siden en omstart uansett starter en ny omgang.

Og én ting som **ikke er etterprøvd ennå**: jeg mener Railways private nett er IPv6-bare, slik at tjenesten må lytte på `::` og ikke `0.0.0.0` for å være synlig internt. Standarden her er `::`, men det må bekreftes mot Railways egen dokumentasjon når vi setter opp.

## Datastar-appen

Kotlin med Ktor. Den kan ikke kalle `fs.field()`, så den skriver klassene og koblingen selv og lar `<fs-field>` gjøre resten i nettleseren. Fordi serveren sender det samme området på nytt ved hver patch, står `data-preserve-attr` på hvert element komponenten rører, med verdiene hentet fra pakken selv.

Den henter Fristil **fra CDN, uten npm**. Det er sporet «Uten byggverktøy» i Fristils egen dokumentasjon, og denne appen er beviset på at det virker fra en JVM.

Fire ting kostet tid, og alle fire er notert i koden:

- **Ktor-klienten har et standard tidsavbrudd**, og en SSE-strøm blir aldri ferdig. Oppstrømsforbindelsen døde med én gang, så ingen fikk beskjed om noe.
- **`data-on:load` finnes ikke i Datastar 1.0.4.** Attributtet ble ignorert i stillhet, uten en eneste feil i konsollen, så skjermen oppdaterte seg bare når du selv gjorde noe. Det heter `data-init`.
- **kotlinx.serialization utelater en verdi som er lik standardverdien**, også når kallstedet setter den uttrykkelig. Tavla sa «ukjent» om nettopp den kolonnen som bærer hele poenget. Standardverdien er fjernet, så hvert kallsted må si hva det er.
- **Å bli med er en navigering, ikke en oppdatering.** Hendelsesstrømmen leser kapselen når den åpnes, én gang. Setter man kapselen midt i strømmens levetid, vet serveren fortsatt ikke hvem som sitter der, og dyttet innmeldingsskjemaet tilbake over spillet. Skjemaet er nå et vanlig skjema med omdirigering, som dessuten virker uten JavaScript.

Etterprøvd med to nettlesere mot den publiserte pakken: den ene blir med, den andre ser henne dukke opp uten å gjøre noe, og et halvutfylt skjema beholder både fanevalget, feltverdiene og koblingen `<fs-field>` laget, gjennom en patch fra serveren.

## Alle åtte komponentene er i bruk

| Komponent | Rolle i spillet |
| --- | --- |
| `<fs-field>` | feltene i vedtaket |
| `<fs-tabs>` | Søkeren / Vedtak |
| `<fs-popover>` | «Hva betyr hjemlene?» |
| `<fs-error-summary>` | et ufullstendig vedtak |
| `<fs-suggestion>` | kommunesøket, der en av fellene ligger |
| `<fs-toast>` | meldinger |
| `<fs-connection-status>` | når sambandet ryker |
| `<fs-session-timeout>` | vakta di, hvis du går fra maskinen |

Kommunesøket er verdt en forklaring. `felles/kommuner.json` inneholder ikke
Mosvik, som ble slått sammen med Inderøy i 2012. Søkeren i sak 2024/1902
oppgir Mosvik, og finner du den ikke i lista, er det svaret. En test krever
at nøyaktig én sak har en kommune som ikke finnes, så ingen lager en felle
til ved et uhell.

### Serveren skriver struktur, ikke kobling

Den viktigste rettelsen i appen. Dette er alt serveren sender for et felt:

```html
<fs-field>
  <label class="fs-label">Navnet ditt</label>
  <input class="fs-input" name="navn" type="text" required>
  <p class="fs-help-text">Vises på tavla for alle.</p>
</fs-field>
```

Ingen id-er, ingen `for`, ingen `aria-describedby`. Komponenten setter det i
nettleseren, og gjør det likt uansett hvilket språk serveren er skrevet i.

Det var fristende å skrive en Kotlin-utgave av `fs.field()` som regnet ut
det samme. Det ville brutt med hele poenget: skulle hver server skrive
Fristils regler på nytt, ville Fristil vært et JavaScript-designsystem med
en manuell reserve for alle andre. Den utgaven er slettet igjen.

Unntaket er elementer noe annet må peke på. Feiloppsummeringen lenker til
`#hjemmel`, så det feltet får en id fra serveren. Regelen er: navngi det
bare når noe skal finne det.

### To ting som kostet tid her

**`data-preserve-attr` betyr «ikke rør», og det gjelder begge veier.**
Feltene fredet `aria-invalid`, og da kunne serveren aldri melde et felt som
ugyldig: morfingen nektet å sette verdien den selv hadde sendt. Feltene
fredes ikke lenger, for serveren skriver dem komplett. Bare det brukeren
eier fredes: fanevalget, sprettoppvinduet og forslagslista.

**Gradle kjenner ikke `felles/` som en inngang.** Endret du en sak og kjørte
testene, kunne du få grønt på gammelt grunnlag fordi oppgaven regnes som
oppdatert. Begge modulene sier nå fra om at fila er en inngang.

## Status

- [x] Spilltjeneren: regler, poeng, runder, SSE, SQLite
- [x] Datastar-appen (Kotlin), med alle åtte komponentene
- [ ] TanStack Start-appen
- [ ] Astro-appen
- [ ] Skallet med bryteren
- [ ] Railway
