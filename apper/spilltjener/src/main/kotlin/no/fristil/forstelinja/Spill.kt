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
const val OPPGJOR_MS = 15_000L
const val SLUTT_MS = 30_000L

/**
 * Hvor lenge hver fase varer.
 *
 * Kan settes med miljøvariabler. To minutter per runde er valgt fordi saken
 * skal leses: to faner, en tabell, tre felt og en hjelpetekst om hjemlene.
 * Med et halvt minutt rakk man å gjette, ikke å saksbehandle, og det er
 * saksbehandlingen som er poenget. Fire runder gir da en omgang på rundt ni
 * minutter. Skal spillet vises fram i full fart, settes `RUNDE_MS` ned.
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
const val POENG_FELLE = 5

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
  /** Feltet spilleren mener er feil, eller `null` for «saken er i orden». */
  val felle: String? = null,
)

data class Spiller(
  val id: String,
  val navn: String,
  val stack: Stack,
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
) {
  private val laas = Mutex()

  /** Slår ut hver gang noe endrer seg, slik at SSE kan sende på nytt. */
  val endringer = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 16)

  private val spillere = LinkedHashMap<String, Spiller>()
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

  suspend fun svar(spillerId: String, svar: Svar): Boolean {
    val godtatt =
      laas.withLock {
        val spiller = spillere[spillerId] ?: return@withLock false
        if (fase != Fase.RUNDE) return@withLock false
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
        if (spillere.isEmpty()) return@withLock false
        if (spillere.values.any { it.svar == null }) return@withLock false
        telleOpp()
        fase = Fase.OPPGJOR
        faseSlutt = klokke.na() + tider.oppgjor
        true
      }
    if (endret) endringer.emit(Unit)
  }

  private fun telleOpp() {
    val plasseringFor = tavleliste().withIndex().associate { (i, s) -> s.id to i + 1 }
    val fasit = sak.fasit

    for (spiller in spillere.values) {
      val svar = spiller.svar
      var poeng = 0
      if (svar != null) {
        if (svar.vedtak == fasit.vedtak) poeng += POENG_VEDTAK
        if (svar.hjemmel == fasit.hjemmel) poeng += POENG_HJEMMEL
        // Fella teller begge veier: å se den, og å se at det ikke er noen.
        if (svar.felle == fasit.felle) poeng += POENG_FELLE
      }
      spiller.sistePoeng = poeng
      spiller.poeng += poeng
      spiller.forrigePlass = plasseringFor[spiller.id] ?: 0
    }
  }

  private fun nullstillSvar() {
    for (spiller in spillere.values) spiller.svar = null
  }

  private fun lagreToppliste() {
    for (spiller in spillere.values) {
      if (spiller.poeng > 0) toppliste.lagre(spiller.navn, spiller.poeng, spiller.stack)
    }
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
        naMs = klokke.na(),
        sak =
          SakUt(
            id = sak.id,
            tittel = sak.tittel,
            sammendrag = sak.sammendrag,
            soker = sak.soker,
          ),
        hjemler = samling.hjemler,
        fasit = if (visFasit) sak.fasit else null,
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
              svar = it.svar,
            )
          },
        evigToppliste = toppliste.topp(10),
      )
    }
}
