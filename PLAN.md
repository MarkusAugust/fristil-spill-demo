# Plan

Rekkefølgen er enkleste først. Hvert punkt sier hva som er galt, hva som
skal gjøres, og hvor stort det er. Punktene om Fristil står her fordi det er
demoen som avdekket dem: det er hele grunnen til at spillet finnes.

Kryss av når noe er gjort, og la begrunnelsen stå. Den er det som er verdt
noe om et halvt år.

## 1. Temaet følger ikke med mellom utgavene

- [ ] Temavelgeren skriver om `?tema=` på lenkene i det du velger

Serveren skriver lenkene med `?tema=` ved sidelasting, og den delen virker:
med `forstelinja-tema=light` i kapselen kommer lenkene ut som
`…?spiller=…&tema=light`. Men velger du tema etterpå, står lenkene i siden
igjen med det gamle valget. I Datastar-appen ligger utgavevelgeren utenfor
området serveren patcher, så den blir stående til neste fulle sidelasting.

Gjøres i alle tre appene, og paritetsprøven får et tilfelle som bytter tema
og følger lenken.

## 2. Stacken på tavla og i topplista står stille

- [ ] Spilltjeneren tar imot hvilken utgave spilleren sitter i
- [ ] Appene sier fra når de henter tilstanden

Spilleren får `stack` når hun melder seg på, og beholder den. Bytter du
utgave, står du fortsatt oppført med den gamle appen, både på tavla og i den
evige topplista, som lagrer `spiller.stack` ved omgangsslutt.

Stacken følger med i `/api/tilstand`, som hver app kaller for hver spiller
uansett. Da dekkes både billetten i drift og den delte kapselen lokalt, uten
et kall til.

## 3. Fristil: rydd bort det som ikke stemmer lenger

- [ ] `takeover.ts` nevner `::part()` og «lit»

Begge er borte: ingen komponent har shadow DOM, og pakken har ingen
avhengigheter. Samme klasse som reglene for `lit-plugin` i `.vscode`.

Og spørsmålet bak: gir `fristil overta` mening slik Fristil er nå? Ja, men
smalere enn før. Utseendet dekkes av laget og `--fs-*`-variablene, så det som
står igjen er «jeg vil at denne komponenten skal oppføre seg annerledes».
Det er en ekte grunn, og en komponent er i dag 100 til 300 linjer uten
avhengigheter, altså noe man faktisk kan overta. Begrunnelsen i fila må bare
si det som er sant nå.

## 4. Kontrakten som data, ikke som prosa

- [ ] Pakken eksporterer klasser, bevaringslister og lovlige verdier som JSON
- [ ] En vaktpost krever at JSON-en stemmer med byggefunksjonene
- [ ] Kotlin-appen genererer konstantene sine av den

Den tydeligste svakheten demoen har avdekket. Kotlin-appen har 172 linjer med
håndskrevne klassenavn og bevaringslister, kopiert fra dokumentasjonen.
Endrer Fristil en liste, går appen stille i stykker. Bare `field` eksporterer
lista si som data i dag; fanene, dialogen, sprettoppvinduet og forslagsfeltet
står bare i brødtekst.

Dette er det ene tiltaket som ville spart mest tid for en utvikler utenfor
JavaScript.

## 5. Fristil sier fra på serveren når id-en lages av seg selv

- [ ] `fs.field()` uten `id` kaster når den kjøres uten `document`

En tilfeldig id er alltid feil på en server, og feilen viser seg først som et
hydreringsavvik i nettleseren. Fristil kan se det selv, og meldingen kan peke
rett på `useId()`.

## 6. Forslagsfeltet i React

- [ ] Dokumentasjonen sier at React er et «serveren filtrerer»-tilfelle
- [ ] Demoen setter `server-filtered`

`<fs-suggestion>` filtrerer selv. I React eier React DOM-en, så appen må
filtrere, og da gjør de to det samme arbeidet. Attributtet finnes allerede.

## 7. CLAUDE.md

- [ ] Forslagsfeltet i React, altså hvem som filtrerer
- [ ] Registreringen må skje før strømmen åpnes, og statisk import er trygt
- [ ] Kontrakten som data, når den finnes
- [ ] Et valg med tre tilstander skal aldri låne navnet til en boolsk egenskap

## 8. Panelet som viser markup og rendringsteknikk

- [ ] Felles panel og animasjon i `felles/brett.css`
- [ ] Hver app melder sin egen siste oppdatering

En knapp nederst åpner et panel i tre deler:

- **Hva som ble oppdatert:** DOM-treet som faktisk endret seg ved siste klikk,
  med en lysende ramme og en myk animasjon.
- **Hvordan:** teknikken. «Serveren sendte en HTML-bit og morfet `#tavle`»,
  «serveren sendte JSON og React tegnet om `<Tavle>`», eller «hele siden kom
  på nytt, og øya byttet ut tabellkroppen».
- **Med hva:** selve nyttelasten, HTML-biten eller JSON-en.

Hver app registrerer sin egen siste oppdatering, siden det er nettopp
forskjellen vi vil vise. Panelet og animasjonen er felles, så de tre ser like
ut.
