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

## Prøv det

| | Adresse |
| --- | --- |
| **Alle tre side om side** | https://forstelinja.up.railway.app |
| TanStack Start og React | https://forstelinja-react.up.railway.app |
| Datastar og Kotlin | https://forstelinja-kotlin.up.railway.app |
| Astro | https://forstelinja-astro.up.railway.app |

Åpne skallet og meld deg på i alle tre rammene, med hvert sitt navn. Da er du
tre saksbehandlere på det samme brettet, og ser samme sak, samme frist og
samme tavle i tre teknologier samtidig. Svar i én ramme, så skjer det i de to
andre.

Du kan også bytte utgave underveis, fra topplinja, og beholde navnet ditt.

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

Skallet er en fjerde, liten tjeneste som bare viser de tre i hver sin ramme
på én skjerm. Den snakker ikke med spilltjeneren og vet ikke hva en sak er.

Spilltjeneren er med vilje ikke den samme prosessen som Datastar-appen. Ellers ville de to andre vært klienter av Datastar-appen, og sammenligningen blitt skjev.

## Omgangen

Fire runder à to minutter, med 25 sekunders oppgjør mellom. Rundt ti minutter, og så begynner en ny. Saken skal leses, ikke gjettes: to faner, en tabell, tre felt og en hjelpetekst om hjemlene.

**Ingen lobby.** Rundene går uavbrutt. Den som åpner adressen er med fra neste runde, og venter aldri lenger enn én runde. En lobby ville betydd at den første som kom satt og ventet på noen som aldri kom.

Hver runde er én sak, og full pott er 25 poeng. Hele skjemaet vurderes:

| | Poeng |
| --- | --- |
| Riktig utfall | 10 |
| Riktig hjemmel | 5 |
| Riktig kommune | 5 |
| Riktig om fella | 5 |

Fella er et felt som er feil: en fødselsdato som ikke finnes, en kommune som ble slått sammen i 2012, en e-post uten krøllalfa. Å se at saken er i orden teller like mye som å se fella. Søknadsteksten står i den ene fana og opplysningene om søkeren i den andre, så begge må leses.

Kommunefeltet er verdt et eget ord: én av sakene viser til en kommune som ble slått sammen med en annen i 2012. Den finnes ikke i registeret, og da er det riktige svaret å la feltet stå tomt. Forslagsfeltet sier fra med «Ingen treff» mens du skriver.

**Du kan svare én gang.** Vedtaket låses i det du trykker, og da får du med én gang vite hva som traff og hva det ga. Fasiten og begrunnelsen kommer først når runden er over og alle har levert. Uten låsen ville den umiddelbare tilbakemeldingen vært en fasit man kunne prøvd seg fram til.

Klokka begynner å riste når det er under ti sekunder igjen, og rister mer for
hvert sekund. Den som har bedt om mindre bevegelse får fargen alene, som sier
det samme.

Når runden er over, kommer resultatet som en **modal dialog**: hva du svarte, hva som var riktig, felt for felt, og hvorfor. Rakk du ikke fristen, sier den i stedet at saken ikke ble behandlet i tide, og at avviket er varslet til statsforvalteren. Lukker du dialogen mens du venter på neste sak, kommer du til den igjen med «Se resultatet».

Den dialogen er grunnen til at `<fs-dialog>` finnes i Fristil. Å åpne en `<dialog>` er et kall, og en server som bare sender HTML kan ikke kalle noe; `<dialog open>` er bare en boks på siden. Demoen fant altså et hull i designsystemet, og hullet ble tettet der.

Har alle svart før fristen, kortes den inn til fire sekunder framfor å ende
med én gang. Den som svarte sist trykket nettopp, og skal rekke å se sin egen
kvittering før oppgjøret legger seg over skjermen.

Poeng for riktig, ikke for raskest. En ren reflekskonkurranse ville latt nettverksmodellen avgjøre, og da hadde demoen bevist noe annet enn den skulle.


## Tilstand, og hvorfor det ikke er noen database

| Tilstand | Lever i | Hvor |
| --- | --- | --- |
| Sakene | for alltid, men er innhold | `felles/saker.json` |
| Spillere, runde, svar | minutter | minne |
| Evig toppliste | for alltid | SQLite |

En omgang varer noen minutter, altså kortere enn en utrulling. Alt annet enn topplista er kortere enn levetiden til prosessen. En database å drifte ville vært én ting til som kan feile under en demonstrasjon, og den lærer ingen noe om Fristil.

Starter prosessen på nytt, er omgangen borte og en ny begynner. Spillerne får beskjed gjennom `<fs-connection-status>` framfor en ødelagt skjerm.

Fristen sendes som et **absolutt tidspunkt**, ikke «30 sekunder igjen». Hver klient teller ned selv, så et forsinket bud flytter ikke fristen, og de tre appene kan ikke komme i utakt.

## Sakene

`felles/saker.json` er delt mellom alle fire delene, og er JSON og ikke TypeScript nettopp derfor: Kotlin og JavaScript leser den samme fila, og ingen av dem eier den. Små bokstaver i verdiene av samme grunn.

Arbeidet er ekte saksbehandling. Vitsen er saken:

> **2024/1187** · Søknad om å holde 14 høner i borettslag. Vedlagt uttalelse fra styret, som er negativ, og fra hanen, som ikke er det.

`SakerTest` avviser en fasit som viser til en hjemmel som ikke finnes, to saker med samme id, en felle som ikke er et felt, og en forklaring som er for kort til å være en forklaring.

## Spille det lokalt

```bash
./kjor.sh
```

Skriptet bygger og starter alle fire tjenestene, og stopper dem med Ctrl+C.
Da kjører de tre utgavene på hver sin adresse:

| Adresse | Utgave |
| --- | --- |
| http://localhost:8081 | Datastar og Kotlin |
| http://localhost:8082 | TanStack Start og React |
| http://localhost:8083 | Astro, hele sider fra serveren |
| http://localhost:8084 | Skallet, alle tre side om side |

Åpne **to av dem ved siden av hverandre**, gjerne to forskjellige. Da er du
to spillere på det samme brettet, og du ser tavla oppdatere seg begge steder
uten at du gjør noe. Skjermene skal se like ut. Det er hele poenget med
demoen.

Velkomsthilsenen lar deg bytte utgave underveis, og det gjør topplinja også.

Skal du bare se at det virker, gir `./kjor.sh rask` runder på 30 sekunder.

Krever Java 21 eller nyere og [Bun](https://bun.com). Gradle henter seg selv,
og skriptet installerer avhengighetene til de to JavaScript-appene.

### Eller hver for seg

```bash
cd apper/spilltjener && ./gradlew test && ./gradlew installDist
./build/install/spilltjener/bin/spilltjener
```

| Variabel | Standard | Til hva |
| --- | --- | --- |
| `PORT` | `8080` | |
| `HOST` | `::` | Railways private nett er IPv6 |
| `SAKER_FIL` | `../../felles/saker.json` | |
| `TOPPLISTE_FIL` | `toppliste.db` | på Railway: et volum |
| `RUNDE_MS` | `120000` | lengre runder når noen skal snakke over spillet |
| `OPPGJOR_MS` | `25000` | |
| `SLUTT_MS` | `40000` | |

## Prøven som sier om påstanden holder

```bash
./kjor.sh rask
cd prover && bun install && bun run paritet
```

`prover/paritet.ts` kjører det samme løpet i alle tre utgavene: melder seg
på, fyller ut vedtaket, ser kvitteringen, og sjekker at komponentene gjør
jobben sin. Den ser bare på det brukeren ser, altså roller, ledetekster og
synlige elementer, og vet ingenting om hvordan appene er bygget. En prøve som
lette etter appenes egne id-er ville sagt mer om hvordan de er skrevet enn om
de gjør det samme.

Den venter på at en runde er i gang framfor å hoppe over skjemaet når den
lander midt i et oppgjør. En prøve som feiler tilfeldig blir ignorert, og da
er den verdiløs.

## Railway

Fem tjenester i ett prosjekt, alle fra dette repoet:

| Tjeneste | Adresse | Volum | Dockerfile |
| --- | --- | --- | --- |
| `spilltjener` | ingen, bare internt | ja, til SQLite | `apper/spilltjener/Dockerfile` |
| `tanstack` | `forstelinja-react` | nei | `apper/tanstack/Dockerfile` |
| `datastar` | `forstelinja-kotlin` | nei | `apper/datastar/Dockerfile` |
| `astro` | `forstelinja-astro` | nei | `apper/astro/Dockerfile` |
| `skall` | `forstelinja` | nei | `apper/skall/Dockerfile` |

Domenene er døpt om fra det Railway fant på selv, med
`railway domain update <gammel> --service <tjeneste> --domain <nytt>`.
Variablene under peker på tjenestene og ikke på adressene, så de fulgte med
uten at noe måtte rettes.

**La «Root Directory» stå tom for alle fem.** Det er det motsatte av hva som
er vanlig i et monorepo, og grunnen er `felles/`: sakene, kommunene og
stilarket ligger utenfor appene, og tre av dem leser filer derfra. Settes en
rotmappe per tjeneste, ser byggeren bare appmappa, og `felles/saker.json`
finnes ikke. Dockerfilene bygger derfor fra rota og kopierer inn det de
trenger.

Hver tjeneste må derfor få vite hvilken Dockerfile som er dens. To veier,
begge holder:

- **Med skript.** `./railway-oppsett.sh` lager alle fem tjenestene, volumet
  og domenene, og setter variablene. Krever `railway login` og `railway link`
  først. Den setter `RAILWAY_DOCKERFILE_PATH` per tjeneste.
- **I grensesnittet.** Sett **Settings → Config as code** til appens egen fil,
  for eksempel `apper/astro/railway.json`. Den setter byggeren til Dockerfile,
  hvilken Dockerfile det er, og `/helse` som helsesjekk.

Den siste veien holder konfigurasjonen i git, og gir helsesjekken i tillegg.
Den første er raskest når fem tjenester skal opp på én gang.

### Variabler

Dockerfilene setter stiene til `felles/` og en fornuftig `PORT` selv. Det som
må settes i Railway er hvem som snakker med hvem:

| Tjeneste | Variabel | Verdi |
| --- | --- | --- |
| alle tre appene | `SPILLTJENER` | `http://spilltjener.railway.internal:8080` |
| alle tre appene og skallet | `DATASTAR_URL` | `https://${{datastar.RAILWAY_PUBLIC_DOMAIN}}` |
| | `TANSTACK_URL` | `https://${{tanstack.RAILWAY_PUBLIC_DOMAIN}}` |
| | `ASTRO_URL` | `https://${{astro.RAILWAY_PUBLIC_DOMAIN}}` |
| `spilltjener` | `TOPPLISTE_FIL` | `/data/toppliste.db`, med volumet montert på `/data` |

Adressene brukes i velkomsthilsenen, i topplinja og i skallets tre rammer.
Uten dem peker lenkene på `localhost`, og du merker det først når noen
klikker.

Spilltjeneren skal ikke ha noe offentlig domene. Appene når den på
`spilltjener.railway.internal`, og det private nettet til Railway er
IPv6-bare, så tjenesten må lytte på `::`. Det er standarden i Dockerfilen.

### Det som er verdt å vite

- **Runder og pauser** styres av `RUNDE_MS`, `OPPGJOR_MS` og `SLUTT_MS` på
  spilltjeneren. Standard er to minutter, 25 sekunder og 40 sekunder.
- **`VARM_TIMER`** sier hvor lenge spilltjeneren holder seg våken etter at
  den siste spilleren er borte. Standard er tre timer.
- **Uten volum forsvinner den evige topplista** ved hver utrulling. Ett volum
  på spilltjeneren holder, og en tjeneste med volum får litt nedetid ved
  utrulling. Det er uproblematisk her, siden en omstart uansett starter en ny
  omgang.
- **Kapslene er merket `SameSite=None; Secure` når siden kommer over https.**
  Skallet viser de tre i hver sin ramme, og i drift ligger de på hvert sitt
  domene, så kapselen er en tredjepartskapsel der. Med `Lax` sendte
  nettleseren den aldri: du kunne se spillet i rammen, men ikke melde deg på.
  Lokalt er alle tre på `localhost`, som er samme nettsted uansett
  portnummer, og der er `Lax` det eneste som er lov over http.
- **Billetten i adressen** er det som lar deg beholde navnet ditt når du
  bytter utgave. En kapsel gjelder bare for sitt eget domene, så lenkene
  bærer `?spiller=` og `?tema=`, og appen du kommer til veksler dem inn i
  sine egne kapsler og fjerner dem fra adressen igjen.

### Dvale, og hva det koster

Railway lar en tjeneste sove når den ikke har sendt **utgående** trafikk på
fem til ti minutter, og vekker den på første forespørsel, også fra det
private nettet. Terskelen kan ikke stilles. En sovende tjeneste koster
ingenting.

Det krever at appene slipper taket når ingen ser på, og det gjør de:

- **Strømmen oppstrøms åpnes først når en nettleser ser på**, og lukkes når
  den siste er borte. Før sto den åpen døgnet rundt, og da sov ingenting.
  Pusterommet er ett minutt, siden en oppfriskning av siden er en avmelding
  og en påmelding med et øyeblikk imellom.
- **Spilltjeneren holder varmen en stund etter siste spiller**, styrt av
  `VARM_TIMER` (standard tre timer). Kommer du tilbake fra en kaffepause, er
  det den samme omgangen, ikke en ny. `VARM_TIMER=0` slår det av, og da
  sovner den så snart Railway vil.

Varmen holdes av et navneoppslag hvert annet minutt. Det er en fot i døra, og
det eneste `Varme.kt` gjør. Railway ser bare på utgående pakker, og et oppslag
er den billigste pakken vi kan sende.

**Slå på dvalen i grensesnittet:** Settings → Deploy → Serverless, på hver av
de fem tjenestene. Det finnes ingen kommando for det i `railway`-verktøyet.

Regningen er nesten bare minne:

| | |
| --- | --- |
| Minne, fem tjenester våkne | om lag 0,5 GB til sammen |
| Volum til topplista | 5 GB, som er minstemålet |
| Døgnet rundt, uten dvale | fire til seks dollar i måneden |
| Med dvale, og et par timer spilling i uka | under én dollar i måneden |

JVM-ene har fått et tak på minnet i Dockerfilene. Uten det tar en JVM en
firedel av det containeren har, enten den trenger det eller ikke.

## Velkomsthilsenen, og valget mellom de tre

Den som åpner spillet får en hilsen fra Etaten for alminnelige søknader: de
har fått mange henvendelser om hvilket rammeverk saksbehandlingsløsningen er
skrevet i, de tar det på alvor, og løsningen leveres derfor i tre utgaver,
skreddersydd til hvert sitt rammeverk. De er trygge på at du vil merke
forskjellen.

Det er hele demoen sagt som en vits, og hilsenen er samtidig valget: du
plukker utgave der, og kan bytte når som helst fra topplinja. Du beholder
navnet ditt og plassen din på tavla når du bytter. Lokalt fordi de tre deler
kapselen, siden kapsler ikke bryr seg om portnummer, og i drift fordi lenka
bærer med seg en billett i adressen.

Valget hører altså hjemme i appen, og da tar du med deg dine egne øyne mellom
utgavene. Skallet er noe annet enn det: det er ikke en vei inn i
spillet, men én skjerm der du ser alle tre samtidig og kan sammenligne dem
side om side. Det er den eneste måten å se påstanden til demoen på én gang.

## Datastar-appen

Kotlin med Ktor. Den kan ikke kalle `fs.field()`, så den skriver klassene selv og lar `<fs-field>` gjøre koblingen i nettleseren. Fordi serveren sender det samme området på nytt ved hver patch, står `data-preserve-attr` på det komponenten lager, med verdiene hentet fra pakken selv.

Den henter Fristil **fra CDN, uten npm**. Det er sporet «Uten byggverktøy» i Fristils egen dokumentasjon, og denne appen er beviset på at det virker fra en JVM.

Fire ting kostet tid, og alle fire er notert i koden:

- **Ktor-klienten har et standard tidsavbrudd**, og en SSE-strøm blir aldri ferdig. Oppstrømsforbindelsen døde med én gang, så ingen fikk beskjed om noe.
- **`data-on:load` finnes ikke i Datastar 1.0.4.** Attributtet ble ignorert i stillhet, uten en eneste feil i konsollen, så skjermen oppdaterte seg bare når du selv gjorde noe. Det heter `data-init`.
- **kotlinx.serialization utelater en verdi som er lik standardverdien**, også når kallstedet setter den uttrykkelig. Tavla sa «ukjent» om nettopp den kolonnen som bærer hele poenget. Standardverdien er fjernet, så hvert kallsted må si hva det er.
- **Å bli med er en navigering, ikke en oppdatering.** Hendelsesstrømmen leser kapselen når den åpnes, én gang. Setter man kapselen midt i strømmens levetid, vet serveren fortsatt ikke hvem som sitter der, og dyttet innmeldingsskjemaet tilbake over spillet. Skjemaet er nå et vanlig skjema med omdirigering, som dessuten virker uten JavaScript.

Etterprøvd med to nettlesere mot den publiserte pakken: den ene blir med, den andre ser henne dukke opp uten å gjøre noe, og et halvutfylt skjema beholder både fanevalget, feltverdiene og koblingen `<fs-field>` laget, gjennom en patch fra serveren.

## TanStack Start-appen

React, med Vite og en egen tjenerinngang i `src/server.ts`. Serveren snakker
med spilltjeneren og sender **JSON** ned til nettleseren; React setter sammen
skjermen. Hendelsesstrømmen sender hele tilstanden ved hvert pulsslag, og
innsendingen svarer med det samme skjermbildet, så det finnes bare én form
data kommer i.

Byggefunksjonene hentes fra `@fristil/designsystem/react`, som gir
`className` og `htmlFor`. Appen henter pakken **fra npm**, ikke fra CDN, og
det er med vilje: den ene utgaven viser at Fristil virker uten byggverktøy,
den andre at det virker med.

Tre ting kostet tid, og alle tre ble rettet i designsystemet:

- **`tabIndex` kom ut som en streng.** React vil ha et tall, så TypeScript
  avviste `fs.tabs()` i hele appen. Det virket i nettleseren, siden React
  gjør om verdien selv, så feilen fantes bare i typene.
- **`open` kunne ikke sendes som tom streng.** React 19 setter egenskaper
  framfor attributter på egendefinerte elementer, og `el.open = ""` er usant,
  så dialogen åpnet seg ikke. Byggefunksjonene sender nå `true`.
- **En id som lages av seg selv, overlever ikke hydrering.** `fs.field()`
  lager en når den ikke får en, og serveren og nettleseren lager da hver sin.
  Id-en kommer nå fra Reacts `useId()`, og det står i dokumentasjonen.

## Astro-appen

Hele sider fra serveren. Skjemaet er en vanlig `<form method="post">` til
samme adresse, og svaret er en ny side: enten med feiloppsummeringen og
svarene stående, eller med kvitteringen. Ingenting av det trenger JavaScript.

Det eneste skriptet er øya i `src/oya.ts`, og den gjør fire ting: registrerer
web-komponentene, teller ned fristen, bytter ut tavla av JSON fra
hendelsesstrømmen, og henter en ny side når runden er en annen enn den siden
ble tegnet med. Ingen skjemahåndtering, ingen validering, ingen tilstand.

Tavleradene lages av den samme funksjonen på serveren og i øya. Uten den
ville radmarkupen finnes to ganger, i en mal og i et skript, og de to ville
gått fra hverandre første gang noen la til en kolonne.

Kommunefeltet er verdt å se på: `<fs-suggestion>` filtrerer lista og tar
piltastene helt selv. Serveren sender alle kommunene, og appen har ikke en
eneste linje kode for feltet. I React-utgaven må appen filtrere selv, for der
er det React som eier DOM-en.

To ting kostet tid:

- **Astro skriver to `class`-attributter** når en byggefunksjon spres sammen
  med en egen klasse, og nettleseren beholder det første. Klassen må trekkes
  ut av spredningen. Det står i Fristils dokumentasjon, og jeg gjorde det
  likevel feil i ni komponenter.
- **Kommunelista ligger under en nøkkel i fila**, ikke i rota. Appen leste den
  som en liste, og feilen kom først når noen skulle fylle ut skjemaet. Nå sier
  den fra ved oppstart i stedet.

## Komponentene i bruk

Lista er kort med vilje: hver komponent her gjør en jobb i spillet. En demo
som laster en komponent uten å bruke den beviser ingenting, og en test krever
nå at hver komponent som registreres faktisk står i markupen.

| Komponent | Rolle i spillet |
| --- | --- |
| `<fs-field>` | feltene i vedtaket |
| `<fs-tabs>` | Søknaden / Søkeren |
| `<fs-popover>` | «Hva betyr hjemlene?» |
| `<fs-error-summary>` | et ufullstendig vedtak |
| `<fs-suggestion>` | kommunesøket, der en av fellene ligger |
| `.fs-select` med `data-picker="styled"` | hjemmelen og «er noe feil», med lista tegnet i siden |
| `<fs-dialog>` | resultatet når runden er over |
| `<fs-connection-status>` | når sambandet til spilltjeneren ryker |

`<fs-dialog>` finnes i Fristil på grunn av dette spillet. Å åpne en `<dialog>`
er et kall, og en server som bare sender HTML kan ikke kalle noe. Demoen fant
hullet, og det ble tettet i designsystemet framfor i appen.

Sambandslinja sier fra på to måter, fordi det er to ulike feil. Mister
nettleseren appserveren, kaster Datastars egen henting, og `datastar-fetch`
sier fra. Mister **appserveren spilltjeneren**, er strømmen ned til
nettleseren fortsatt i orden, og da måtte serveren si det selv: den patcher
et skjult merke, og skriptet i siden melder det videre til komponenten.

Den andre veien var ikke åpenbar. Det opplagte var å la strømmen ryke, men
Datastar kobler bare til igjen når lesingen kaster, og en app som ikke
skriver noe ryker aldri. Skjermene ble stående helt normale og aldri
oppdatert mer. Prøvd ved å drepe spilltjeneren med en nettleser åpen, og
starte den igjen: linja kommer, og spillet tar seg inn av seg selv.

Kommunesøket er verdt en forklaring. `felles/kommuner.json` inneholder ikke
Mosvik, som ble slått sammen med Inderøy i 2012. Søkeren i sak 2024/1902
oppgir Mosvik, og finner du den ikke i lista, er det svaret. En test krever
at nøyaktig én sak har en kommune som ikke finnes, så ingen lager en felle
til ved et uhell.

### Serveren skriver struktur, ikke kobling

Dette er alt serveren sender for innmeldingsfeltet:

```html
<fs-field>
  <label class="fs-label" data-preserve-attr="class for">Navnet ditt</label>
  <input class="fs-input" name="navn" type="text" required
         data-preserve-attr="id aria-describedby">
  <p class="fs-help-text" data-preserve-attr="id">Vises på tavla for alle.</p>
</fs-field>
```

Ingen id-er, ingen `for`, ingen `aria-describedby`. Komponenten setter det i
nettleseren, og gjør det likt uansett hvilket språk serveren er skrevet i.

Bevaringslistene må stå der fordi serveren sender det samme området på nytt.
Serveren skriver aldri disse attributtene, så uten listene river morfingen
dem bort ved neste patch, og feltet mister koblingen mellom ledetekst,
kontroll og hjelpetekst. Listene er smalere enn den Fristil oppgir:
`aria-invalid` og `data-state` er serverens, og fredet vi dem, kunne serveren
aldri meldt feltet som ugyldig.

Det var fristende å skrive en Kotlin-utgave av `fs.field()` som regnet ut
det samme. Det ville brutt med hele poenget: skulle hver server skrive
Fristils regler på nytt, ville Fristil vært et JavaScript-designsystem med
en manuell reserve for alle andre. Den utgaven er slettet igjen.

Unntaket er elementer noe annet må peke på. Feiloppsummeringen lenker til
`#hjemmel` og `#felle`, så de to feltene får id fra serveren, og da skriver
den hele koblingen selv. Da har komponenten ingenting å legge til, og
morfingen ingenting å ta bort. Regelen er: navngi det bare når noe skal finne
det, og skriv da resten òg.

### To ting som kostet tid her

**`data-preserve-attr` betyr «ikke rør», og det gjelder begge veier.** Et
felt fredet `aria-invalid`, og da kunne serveren aldri melde feltet som
ugyldig: morfingen nektet å sette verdien den selv hadde sendt. En liste skal
bare inneholde det komponenten lager.

**Gradle kjenner ikke `felles/` som en inngang.** Endret du en sak og kjørte
testene, kunne du få grønt på gammelt grunnlag fordi oppgaven regnes som
oppdatert. Spilltjeneren sier nå fra om at fila er en inngang.

## Status

- [x] Spilltjeneren: regler, poeng, runder, SSE, SQLite
- [x] Datastar-appen (Kotlin), med sju komponenter i bruk
- [x] TanStack Start-appen (React), samme skjerm av JSON
- [x] Astro-appen, hele sider og én øy
- [x] Bytte mellom utgavene, i velkomsthilsenen og i topplinja
- [x] Skallet, som viser de tre side om side
- [x] Railway, fem tjenester med Dockerfile fra rota
