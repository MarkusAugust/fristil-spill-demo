import { readFileSync } from "node:fs"

/**
 * Kommunelista.
 *
 * Samme fil som de to andre appene leser, av samme grunn som `brett.css`
 * ligger i `felles/`: en demo som skal vise den samme skjermen i tre
 * teknologier kan ikke ha tre ulike kommunelister.
 */
const STI = process.env.KOMMUNER_FIL ?? "../../felles/kommuner.json"

/**
 * Fila har en `_om`-nøkkel ved siden av lista, der det står hvorfor Mosvik
 * ikke er med. Lista ligger altså under `kommuner`, ikke i rota.
 */
const innhold = JSON.parse(readFileSync(STI, "utf8")) as { kommuner: string[] }

if (!Array.isArray(innhold.kommuner)) {
  throw new Error(`«${STI}» har ingen liste under «kommuner».`)
}

export const KOMMUNER: string[] = innhold.kommuner
