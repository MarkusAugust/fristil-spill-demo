package no.fristil.forstelinja.datastar


/**
 * Navnet denne utgaven har utad.
 *
 * De tre appene er den samme skjermen i hvert sitt rammeverk, og hver av dem
 * sier hvem den er i velkomsthilsenen og i topplinja.
 */
const val APPNAVN = "Datastar"

/**
 * De tre utgavene, og hvor de kjører.
 *
 * Adressene settes med miljøvariabler, slik at det samme oppsettet virker
 * lokalt og utrullet. Lokalt deler de tre kapselen, siden kapsler ikke bryr
 * seg om portnummer, og da beholder du navnet ditt når du bytter utgave.
 */
data class Utgave(val navn: String, val rammeverk: String, val adresse: String)

/**
 * Lenka til en annen utgave, med det du er og hva du har valgt.
 *
 * I drift ligger de tre utgavene på hvert sitt domene, og en kapsel gjelder
 * bare for sitt eget. Uten dette mistet du navnet ditt og plassen på tavla i
 * det du byttet, og måtte melde deg på som en ny saksbehandler. Id-en er
 * altså en billett som følger med i adressen, og den appen du kommer til
 * bytter den inn i sin egen kapsel og fjerner den fra adressen igjen.
 *
 * Billetten er i praksis nøkkelen til spilleren, og den som får lenka blir
 * deg. I et spill er det greit. I noe som betyr noe ville dette vært en
 * kortlevd engangsbillett fra spilltjeneren i stedet.
 */
fun lenkeTilUtgave(adresse: String, spillerId: String?, tema: String?): String {
  val deler = buildList {
    if (spillerId != null) add("spiller=${java.net.URLEncoder.encode(spillerId, "UTF-8")}")
    if (tema == "light" || tema == "dark") add("tema=$tema")
  }
  return if (deler.isEmpty()) adresse else "$adresse?${deler.joinToString("&")}"
}

val UTGAVER =
  listOf(
    Utgave(
      "TanStack Start",
      "React, tegnet i nettleseren",
      System.getenv("TANSTACK_URL") ?: "http://localhost:8082",
    ),
    Utgave(
      "Datastar",
      "Kotlin, ferdige HTML-biter fra serveren",
      System.getenv("DATASTAR_URL") ?: "http://localhost:8081",
    ),
    Utgave(
      "Astro",
      "hele sider fra serveren, med én øy",
      System.getenv("ASTRO_URL") ?: "http://localhost:8083",
    ),
  )

/** Versjonen av designsystemet siden henter fra CDN. */
const val FRISTIL_VERSJON = "0.18.0"

private const val CDN = "https://cdn.jsdelivr.net/npm/@fristil/designsystem@$FRISTIL_VERSJON"

/**
 * Stilarket siden trenger, hentet fra CDN.
 *
 * Ingen npm, ingen byggesteg. Det er sporet «Uten byggverktøy» i Fristils
 * egen dokumentasjon, og denne appen er beviset på at det virker.
 *
 * Én fil: `dist/fristil.css` er alle stilarkene flatet ut, uten `@import`.
 * Før sto det tjueåtte lenker her, fem av dem bare for å hente i første
 * runde det `field.css` ellers hentet i den andre, og på et mobilnett sto
 * feltene uten ramme til den andre runden kom.
 */
val STILARK = listOf("$CDN/dist/fristil.css")

/**
 * Web-komponentene siden registrerer, også fra CDN.
 *
 * Ett kall: `defineFs()` fra `dist/register.js` registrerer alle ni. Før sto
 * det sju importer her, én per komponent.
 */
val KOMPONENTER = listOf("$CDN/dist/register.js" to "defineFs")

/**
 * Byggefunksjonene, som panelet trenger for å skrive markupen sin.
 *
 * Panelet er felles for de tre appene og importerer ikke `fs` selv: to av dem
 * bunter designsystemet og skal ikke få et CDN-kall i drift for et
 * feilsøkingspanel. Denne appen henter alt fra CDN uansett, så her er det den
 * samme adressen som resten.
 */
const val FS_MODUL = "$CDN/dist/fs.js"

const val DATASTAR_CDN = "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.4/bundles/datastar.js"
