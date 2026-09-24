# Astro-appen.
#
# Bygges fra rota av repoet, ikke fra appmappa: appen serverer
# `felles/brett.css` og leser kommunelista derfra, og de filene ligger
# utenfor appen. På Railway betyr det at «Root Directory» skal stå tom.
FROM oven/bun:1 AS bygger
WORKDIR /kilde

COPY apper/astro/package.json apper/astro/bun.lock ./
RUN bun install --frozen-lockfile

COPY apper/astro/tsconfig.json apper/astro/astro.config.mjs ./
COPY apper/astro/src src
RUN bun run build

FROM oven/bun:1
WORKDIR /app

COPY --from=bygger /kilde/node_modules node_modules
COPY --from=bygger /kilde/dist dist
COPY --from=bygger /kilde/package.json ./
COPY felles felles

ENV BRETT_CSS_FIL=/app/felles/brett.css \
    TEMA_CSS_FIL=/app/felles/tema.css \
    PANEL_JS_FIL=/app/felles/panel.js \
    PANEL_AVLYTT_JS_FIL=/app/felles/panel-avlytt.js \
    KOMMUNER_FIL=/app/felles/kommuner.json \
    HOST=:: \
    PORT=8083

CMD ["bun", "./dist/server/entry.mjs"]
