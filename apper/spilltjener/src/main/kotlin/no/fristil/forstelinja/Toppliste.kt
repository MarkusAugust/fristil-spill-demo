package no.fristil.forstelinja

import java.sql.Connection
import java.sql.DriverManager

/**
 * Den evige topplista, og det eneste som overlever en omstart.
 *
 * SQLite framfor en database å drifte: det er én fil, og det er nok for en
 * liste med ti navn. På Railway må fila ligge på et volum, ellers forsvinner
 * den ved hver utrulling.
 */
class Toppliste(sti: String = System.getenv("TOPPLISTE_FIL") ?: "toppliste.db") {
  private val forbindelse: Connection = DriverManager.getConnection("jdbc:sqlite:$sti")

  init {
    forbindelse.createStatement().use {
      it.executeUpdate(
        """
        CREATE TABLE IF NOT EXISTS resultat (
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          navn TEXT NOT NULL,
          poeng INTEGER NOT NULL,
          stack TEXT NOT NULL,
          nar INTEGER NOT NULL
        )
        """
          .trimIndent()
      )
      it.executeUpdate("CREATE INDEX IF NOT EXISTS resultat_poeng ON resultat (poeng DESC)")
      // Rader lagret før navnene ble ryddet. Uten dette står «Kari» og
      // «Kari » som to spillere i lista for alltid.
      it.executeUpdate("UPDATE resultat SET navn = trim(navn) WHERE navn <> trim(navn)")
    }
  }

  /**
   * Rydder navnet før det lagres.
   *
   * «Markus» og «Markus  » er den samme saksbehandleren, og en toppliste som
   * viser begge sier ingenting om hvem som er best. Mellomrom i endene og
   * doble mellomrom inni forsvinner her, én gang, framfor at hver avlesning
   * må ta høyde for dem.
   */
  private fun ryddet(navn: String): String = navn.trim().replace(Regex("\\s+"), " ")

  fun lagre(navn: String, poeng: Int, stack: Stack) {
    forbindelse
      .prepareStatement("INSERT INTO resultat (navn, poeng, stack, nar) VALUES (?, ?, ?, ?)")
      .use {
        it.setString(1, ryddet(navn))
        it.setInt(2, poeng)
        it.setString(3, stack.name)
        it.setLong(4, System.currentTimeMillis())
        it.executeUpdate()
      }
  }

  /**
   * De beste resultatene, ett per navn.
   *
   * Uten grupperingen fylte den samme spilleren hele lista med sine egne
   * omganger, og en toppliste der det står «Markus» fem ganger sier ingenting
   * om hvem som er best.
   *
   * Sammenligningen er uten hensyn til store og små bokstaver, for «Markus»
   * og «markus» er den samme personen som skrev navnet sitt i hui og hast.
   * Raden med flest poeng bestemmer hvordan navnet staves i lista.
   */
  fun topp(antall: Int): List<ToppEntry> =
    forbindelse
      .prepareStatement(
        """
        SELECT navn, poeng, stack, nar
        FROM resultat
        WHERE id IN (
          SELECT id FROM resultat r2
          WHERE lower(r2.navn) = lower(resultat.navn)
          ORDER BY poeng DESC, nar ASC
          LIMIT 1
        )
        ORDER BY poeng DESC, nar ASC
        LIMIT ?
        """
          .trimIndent()
      )
      .use { setning ->
        setning.setInt(1, antall)
        setning.executeQuery().use { rader ->
          buildList {
            while (rader.next()) {
              add(
                ToppEntry(
                  navn = rader.getString("navn"),
                  poeng = rader.getInt("poeng"),
                  stack = runCatching { Stack.valueOf(rader.getString("stack")) }.getOrDefault(Stack.UKJENT),
                  nar = rader.getLong("nar"),
                )
              )
            }
          }
        }
      }
}
