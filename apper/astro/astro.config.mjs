// @ts-check
import node from "@astrojs/node"
import { defineConfig } from "astro/config"

/**
 * Astro-utgaven av Førstelinja.
 *
 * Denne appen sender **hele sider**. Hver innsending er en vanlig POST, og
 * svaret er en ny side. Det eneste skriptet på siden er øya som lytter på
 * hendelsesstrømmen, teller ned fristen og henter tavla som JSON.
 *
 * `output: "server"` fordi hver visning er personlig: tavla, dine poeng og
 * om du har svart. Ingenting av det kan bygges på forhånd.
 */
export default defineConfig({
  output: "server",
  adapter: node({ mode: "standalone" }),
  server: { port: 8083, host: true },
  devToolbar: { enabled: false },
})
