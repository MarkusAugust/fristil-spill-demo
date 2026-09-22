package no.fristil.forstelinja

import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Reglene i Førstelinja, og all tilstanden spillet har.
 *
 * Alt her lever i minnet, og det er et bevisst valg: en omgang varer noen
 * minutter, altså kortere enn en utrulling. Det eneste som skal overleve en
 * omstart er den evige topplista, og den har sin egen fil.
 *
 * Omgangene går uavbrutt, uten lobby. Den som åpner adressen er med fra
 * neste runde, og venter aldri lenger enn en runde. En lobby ville betydd at
 * den første som kom satt og ventet på noen som aldri kom.
 *
 * Tilstanden ligger bak én lås. Ved demo-skala trengs ikke mer, og en lås er
 * lettere å resonnere om enn flere.
 */

const val RUNDER_PER_SPILL = 4
const val RUNDE_MS = 120_000L
const val OPPGJOR_MS = 25_000L
const val SLUTT_MS = 40_000L

/**
 * Hvor mange runder på rad en spiller kan la være å svare før hun ryddes
 * bort fra tavla.
 *
 * En lukket fane sier ikke fra til noen, så uten dette blir hvert besøk
 * stående som et navn på tavla til prosessen starter på nytt. Runder og ikke
 * sekunder: en spiller som leser en sak i to minutter er ikke borte, og en
 * klokke ville ryddet henne bort midt i lesingen.
 *
 * Spilleren får beskjed på skjermen før det skjer, og tallet er høyere enn
 * halve omgangen, så en som bommer på én sak ikke mister plassen sin.
 */
const val RUNDER_UTEN_SVAR_FOR_BORTE = 3

/**
 * Hvor lenge hver fase varer.
 *
 * Kan settes med miljøvariabler. To minutter per runde er valgt fordi saken
 * skal leses: to faner, en tabell, tre felt og en hjelpetekst om hjemlene.
 * Med et halvt minutt rakk man å gjette, ikke å saksbehandle, og det er
 * saksbehandlingen som er poenget. Oppgjøret er langt nok til å lese fasiten
 * og begrunnelsen, ikke bare til å se poengsummen. Fire runder gir en omgang
 * på rundt ti minutter. Skal spillet vises fram i full fart, settes
 * `RUNDE_MS` ned.
 */
data class Tider(
  val runde: Long = RUNDE_MS,
  val oppgjor: Long = OPPGJOR_MS,
  val slutt: Long = SLUTT_MS,
) {
  companion object {
    fun fraMiljo() =
      Tider(
        runde = System.getenv("RUNDE_MS")?.toLongOrNull() ?: RUNDE_MS,
        oppgjor = System.getenv("OPPGJOR_MS")?.toLongOrNull() ?: OPPGJOR_MS,
        slutt = System.getenv("SLUTT_MS")?.toLongOrNull() ?: SLUTT_MS,
      )
  }
}

/** Full pott per runde: vedtaket, hjemmelen og fella. */
const val POENG_VEDTAK = 10
const val POENG_HJEMMEL = 5
const val POENG_KOMMUNE = 5
const val POENG_FELLE = 5
const val POENG_FULL_POTT = POENG_VEDTAK + POENG_HJEMMEL + POENG_KOMMUNE + POENG_FELLE

/*
 * Verdiene på tråden skrives med små bokstaver.
 *
 * `felles/saker.json` redigeres for hånd og leses av både JVM-en og de to
 * JavaScript-appene, og der er små bokstaver det vanlige. Uten `@SerialName`
 * ville Kotlin krevd «AVSLATT» i en fil et menneske skal lese.
 */
@Serializable
enum class Vedtak {
  @SerialName("innvilget") INNVILGET,
  @SerialName("avslatt") AVSLATT,
}

@Serializable
enum class Fase {
  @SerialName("runde") RUNDE,
  @SerialName("oppgjor") OPPGJOR,
  @SerialName("slutt") SLUTT,
}

/** Hvilken app spilleren sitter i. Vises på tavla, og er hele poenget. */
@Serializable
enum class Stack {
  @SerialName("tanstack") TANSTACK,
  @SerialName("datastar") DATASTAR,
  @SerialName("astro") ASTRO,
  @SerialName("ukjent") UKJENT,
}

@Serializable
data class Svar(
  val vedtak: Vedtak? = null,
  val hjemmel: String? = null,
  /**
   * Kommunen spilleren bekreftet, eller `null` for «den finnes ikke».
   *
   * Én av sakene viser til en kommune som ble slått sammen med en annen. Da
   * er det riktige svaret å la feltet stå tomt, og forslagsfeltet sier fra
   * med «Ingen treff» mens du skriver.
   */
  val kommune: String? = null,
  /** Feltet spilleren mener er feil, eller `null` for «saken er i orden». */
  val felle: String? = null,
)

data class Spiller(
  val id: String,
  val navn: String,
  val stack: Stack,
  /**
   * Om spilleren var med da saken kom på bordet.
   *
   * Den som melder seg på midt i en runde får gjerne svare, og får poeng for
   * det. Det hun ikke skal få, er «avvik registrert, varslet til
   * fylkesmannen» for en sak hun aldri så.
   */
  var medPaSaken: Boolean = false,
  /** Runder på rad uten svar. Nok av dem, og hun regnes som gått hjem. */
  var runderUtenSvar: Int = 0,
  var poeng: Int = 0,
  var svar: Svar? = null,
  var sistePoeng: Int = 0,
  var forrigePlass: Int = 0,
)

/** Klokka, slik at testene slipper å vente i ekte sekunder. */
fun interface Klokke {
  fun na(): Long
}

class Spill(
  private val samling: Sakssamling,
  private val toppliste: Toppliste,
  private val klokke: Klokke = Klokke { System.currentTimeMillis() },
  private val tider: Tider = Tider(),
  /**
   * Kommuneregisteret, som fasiten for kommunefeltet regnes ut av.
   *
   * Står søkerens kommune her, er det den som er riktig svar. Står den ikke,
   * finnes ikke kommunen lenger, og da er det riktige å la feltet stå tomt.
   */
  private val kommuner: List<String> = emptyList(),
) {
  private val laas = Mutex()

  /** Slår ut hver gang noe endrer seg, slik at SSE kan sende på nytt. */
  val endringer = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 16)

  private val spillere = LinkedHashMap<String, Spiller>()

  /**
   * Den evige topplista, husket mellom lagringene.
   *
   * `tilstand()` kalles for hver ramme til hver spiller, og lå før med en
   * SQL-spørring inne i låsen. Lista endrer seg bare når en omgang er over.
   */
  private var evig: List<ToppEntry> = toppliste.topp(10)
  private var rekkefolge: List<Sak> = trekkSaker()

  var fase: Fase = Fase.RUNDE
    private set

  var rundeNr: Int = 1
    private set

  var faseSlutt: Long = klokke.na() + tider.runde
    private set

  val sak: Sak
    get() = rekkefolge[rundeNr - 1]

  private fun trekkSaker(): List<Sak> = samling.saker.shuffled().take(RUNDER_PER_SPILL)

  suspend fun bliMed(navn: String, stack: Stack): Spiller {
    val ren = navn.trim().take(24).ifBlank { "Anonym" }
    val spiller = Spiller(id = UUID.randomUUID().toString(), navn = ren, stack = stack)
    laas.withLock { spillere[spiller.id] = spiller }
    endringer.emit(Unit)
    return spiller
  }

  /**
   * Tar imot ett vedtak per spiller per runde.
   *
   * Et svar kan ikke endres. Det er ikke pynt: spilleren får vite med én
   * gang om svaret traff, og uten låsen kunne hvem som helst prøvd seg fram
   * til full pott.
   */
  suspend fun svar(spillerId: String, svar: Svar): Boolean {
    val godtatt =
      laas.withLock {
        val spiller = spillere[spillerId] ?: return@withLock false
        if (fase != Fase.RUNDE) return@withLock false
        if (spiller.svar != null) return@withLock false
        spiller.svar = svar
        true
      }
    if (godtatt) endringer.emit(Unit)
    return godtatt
  }

  /**
   * Flytter spillet videre når fristen er ute.
   *
   * Kalles av løkka i `Main`, og av testene direkte. Den gjør ingenting før
   * klokka har passert fristen, så den kan kalles så ofte man vil.
   */
  suspend fun tikk() {
    val endret =
      laas.withLock {
        if (klokke.na() < faseSlutt) return@withLock false
        when (fase) {
          Fase.RUNDE -> {
            telleOpp()
            fase = Fase.OPPGJOR
            faseSlutt = klokke.na() + tider.oppgjor
          }
          Fase.OPPGJOR ->
            if (rundeNr < RUNDER_PER_SPILL) {
              rundeNr += 1
              nullstillSvar()
              fase = Fase.RUNDE
              faseSlutt = klokke.na() + tider.runde
            } else {
              lagreToppliste()
              fase = Fase.SLUTT
              faseSlutt = klokke.na() + tider.slutt
            }
          Fase.SLUTT -> nyOmgang()
        }
        true
      }
    if (endret) endringer.emit(Unit)
  }

  /** Avslutter runden med én gang hvis alle har svart. */
  suspend fun avsluttHvisAlleHarSvart() {
    val endret =
      laas.withLock {
        if (fase != Fase.RUNDE) return@withLock false
        // Den som har sittet over to runder på rad har trolig lukket fana,
        // og skal ikke holde de andre igjen til fristen går ut. Én bom
        // holder ikke: da ville en som leser sakte blitt regnet bort, og
        // dermed bommet neste gang òg.
        val med = spillere.values.filter { it.runderUtenSvar < RUNDER_UTEN_SVAR_FOR_BORTE - 1 }
        if (med.isEmpty()) return@withLock false
        if (med.any { it.svar == null }) return@withLock false
        telleOpp()
        fase = Fase.OPPGJOR
        faseSlutt = klokke.na() + tider.oppgjor
        true
      }
    if (endret) endringer.emit(Unit)
  }

  /**
   * Hvordan ett svar slo ut mot fasiten.
   *
   * Én utregning, brukt to steder: til kvitteringen spilleren får med én
   * gang, og til opptellingen når runden er over. To utregninger kunne gått
   * fra hverandre, og da ville kvitteringen sagt noe annet enn tavla.
   */
  private fun vurder(svar: Svar?, sak: Sak): Vurdering {
    val fasit = sak.fasit
    if (svar == null) return Vurdering(false, false, false, false, 0)
    val vedtak = svar.vedtak == fasit.vedtak
    val hjemmel = svar.hjemmel == fasit.hjemmel
    // Tomt felt er et svar: det betyr «kommunen finnes ikke lenger».
    val kommune = svar.kommune?.ifBlank { null } == riktigKommune(sak)
    // Fella teller begge veier: å se den, og å se at det ikke er noen.
    val felle = svar.felle == fasit.felle
    return Vurdering(
      vedtakRiktig = vedtak,
      hjemmelRiktig = hjemmel,
      kommuneRiktig = kommune,
      felleRiktig = felle,
      poeng =
        (if (vedtak) POENG_VEDTAK else 0) +
          (if (hjemmel) POENG_HJEMMEL else 0) +
          (if (kommune) POENG_KOMMUNE else 0) +
          (if (felle) POENG_FELLE else 0),
    )
  }

  /** Kommunen som er riktig svar, eller `null` når den ikke finnes lenger. */
  private fun riktigKommune(sak: Sak): String? =
    sak.soker.kommune.takeIf { it in kommuner }

  private fun telleOpp() {
    val plasseringFor = tavleliste().withIndex().associate { (i, s) -> s.id to i + 1 }

    for (spiller in spillere.values) {
      if (spiller.svar == null && spiller.medPaSaken) spiller.runderUtenSvar += 1
      else spiller.runderUtenSvar = 0

      val poeng = vurder(spiller.svar, sak).poeng
      spiller.sistePoeng = poeng
      spiller.poeng += poeng
      spiller.forrigePlass = plasseringFor[spiller.id] ?: 0
    }

    ryddBortBorte()
  }

  private fun nullstillSvar() {
    for (spiller in spillere.values) {
      spiller.svar = null
      spiller.medPaSaken = true
    }
  }

  /** Spillere som har sittet over nok runder til at de nok har gått hjem. */
  private fun ryddBortBorte() {
    spillere.values.removeAll { it.runderUtenSvar >= RUNDER_UTEN_SVAR_FOR_BORTE }
  }

  private fun lagreToppliste() {
    for (spiller in spillere.values) {
      if (spiller.poeng > 0) toppliste.lagre(spiller.navn, spiller.poeng, spiller.stack)
    }
    evig = toppliste.topp(10)
  }

  /**
   * Ny omgang.
   *
   * Spillerne blir med videre med null poeng. Å kaste dem ut ville betydd at
   * alle måtte skrive navnet sitt på nytt hvert tredje minutt.
   */
  private fun nyOmgang() {
    rekkefolge = trekkSaker()
    rundeNr = 1
    fase = Fase.RUNDE
    faseSlutt = klokke.na() + tider.runde
    for (spiller in spillere.values) {
      spiller.poeng = 0
      spiller.sistePoeng = 0
      spiller.forrigePlass = 0
      spiller.svar = null
      spiller.medPaSaken = true
      spiller.runderUtenSvar = 0
    }
  }

  suspend fun glem(spillerId: String) {
    val fantes = laas.withLock { spillere.remove(spillerId) != null }
    if (fantes) endringer.emit(Unit)
  }

  private fun tavleliste(): List<Spiller> =
    spillere.values.sortedWith(compareByDescending<Spiller> { it.poeng }.thenBy { it.navn })

  /** Tilstanden slik én spiller skal se den. */
  suspend fun tilstand(spillerId: String?): Tilstand =
    laas.withLock {
      val tavle = tavleliste()
      val meg = spillerId?.let { spillere[it] }
      val visFasit = fase != Fase.RUNDE

      Tilstand(
        fase = fase,
        rundeNr = rundeNr,
        runderTotalt = RUNDER_PER_SPILL,
        fristMs = faseSlutt,
        rundeLengdeMs = tider.runde,
        grenseUtenSvar = RUNDER_UTEN_SVAR_FOR_BORTE,
        poeng =
          PoengUt(
            vedtak = POENG_VEDTAK,
            hjemmel = POENG_HJEMMEL,
            kommune = POENG_KOMMUNE,
            felle = POENG_FELLE,
            fullPott = POENG_FULL_POTT,
          ),
        faseLengdeMs =
          when (fase) {
            Fase.RUNDE -> tider.runde
            Fase.OPPGJOR -> tider.oppgjor
            Fase.SLUTT -> tider.slutt
          },
        naMs = klokke.na(),
        sak =
          SakUt(
            id = sak.id,
            tittel = sak.tittel,
            sammendrag = sak.sammendrag,
            tekst = sak.tekst,
            soker = sak.soker,
          ),
        hjemler = samling.hjemler,
        fasit = if (visFasit) sak.fasit else null,
        // Kommunefeltet har ingen fasit i fila: den regnes ut av registeret,
        // og «ingen» er et gyldig svar.
        fasitKommune = if (visFasit) riktigKommune(sak) else null,
        forklaring = if (visFasit) sak.forklaring else null,
        tavle =
          tavle.mapIndexed { i, s ->
            TavleRad(
              plass = i + 1,
              navn = s.navn,
              poeng = s.poeng,
              stack = s.stack,
              erMeg = s.id == spillerId,
            )
          },
        meg =
          meg?.let {
            MegUt(
              navn = it.navn,
              poeng = it.poeng,
              sistePoeng = it.sistePoeng,
              plass = tavle.indexOfFirst { rad -> rad.id == it.id } + 1,
              forrigePlass = it.forrigePlass,
              harSvart = it.svar != null,
              medPaSaken = it.medPaSaken,
              runderUtenSvar = it.runderUtenSvar,
              svar = it.svar,
              vurdering = it.svar?.let { svar -> vurder(svar, sak) },
            )
          },
        evigToppliste = evig,
      )
    }
}
