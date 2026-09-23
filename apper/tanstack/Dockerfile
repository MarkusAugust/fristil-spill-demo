# TanStack Start-appen.
#
# Bygges fra rota av repoet, ikke fra appmappa: appen serverer
# `felles/brett.css` og leser kommunelista derfra, og de filene ligger
# utenfor appen. På Railway betyr det at «Root Directory» skal stå tom.
FROM oven/bun:1 AS bygger
WORKDIR /kilde

# Avhengighetene i sitt eget lag, så en endring i kildekoden ikke henter dem
# på nytt.
COPY apper/tanstack/package.json apper/tanstack/bun.lock ./
RUN bun install --frozen-lockfile

COPY apper/tanstack/tsconfig.json apper/tanstack/vite.config.ts ./
COPY apper/tanstack/src src
RUN bun run build

FROM oven/bun:1
WORKDIR /app

# Node_modules blir med: den bygde tjeneren importerer fortsatt fra dem.
COPY --from=bygger /kilde/node_modules node_modules
COPY --from=bygger /kilde/dist dist
COPY --from=bygger /kilde/package.json ./
COPY apper/tanstack/serve.ts ./
COPY felles felles

ENV BRETT_CSS_FIL=/app/felles/brett.css \
    PANEL_JS_FIL=/app/felles/panel.js \
    KOMMUNER_FIL=/app/felles/kommuner.json \
    HOST=:: \
    PORT=8082

CMD ["bun", "run", "serve.ts"]
