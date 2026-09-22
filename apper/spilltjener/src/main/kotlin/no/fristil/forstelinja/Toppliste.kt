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
    }
  }

  fun lagre(navn: String, poeng: Int, stack: Stack) {
    forbindelse
      .prepareStatement("INSERT INTO resultat (navn, poeng, stack, nar) VALUES (?, ?, ?, ?)")
      .use {
        it.setString(1, navn)
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
   */
  fun topp(antall: Int): List<ToppEntry> =
    forbindelse
      .prepareStatement(
        """
        SELECT navn, poeng, stack, nar
        FROM resultat
        WHERE id IN (
          SELECT id FROM resultat r2
          WHERE r2.navn = resultat.navn
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
