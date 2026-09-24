# Skallet. Det har ingen avhengigheter og ingenting å bygge, så bildet er
# bare Bun og én fil. Det snakker heller ikke med spilltjeneren: hver ramme
# er en helt vanlig adresse.
FROM oven/bun:1
WORKDIR /app

COPY apper/skall/package.json ./
COPY apper/skall/src src
# Temaet ligger utenfor appmappa, som resten av det de tre utgavene deler.
COPY felles/tema.css felles/tema.css

ENV HOST=:: \
    PORT=8084 \
    TEMA_CSS_FIL=/app/felles/tema.css

CMD ["bun", "run", "src/skall.ts"]
