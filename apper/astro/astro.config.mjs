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

  /*
   * Hvilke verter som får lov til å si hva forespørselen kom fra.
   *
   * Railway avslutter TLS i kanten og sender videre over vanlig HTTP, med
   * `x-forwarded-proto: https`. Astro tror på det hodet bare for verter som
   * står her; ellers ser den `http://`, sammenligner med `Origin` fra
   * nettleseren, som er `https://`, og avviser hver innsending med 403.
   * Skjemaene virket altså lokalt og ikke i drift.
   */
  security: {
    allowedDomains: [
      { hostname: "**.up.railway.app", protocol: "https" },
      ...(process.env.TILLATT_VERT
        ? [{ hostname: process.env.TILLATT_VERT, protocol: "https" }]
        : []),
    ],
  },

  adapter: node({ mode: "standalone" }),
  server: { port: 8083, host: true },
  devToolbar: { enabled: false },
})
