# Skallet. Det har ingen avhengigheter og ingenting å bygge, så bildet er
# bare Bun og én fil. Det snakker heller ikke med spilltjeneren: hver ramme
# er en helt vanlig adresse.
FROM oven/bun:1
WORKDIR /app

COPY apper/skall/package.json ./
COPY apper/skall/src src

ENV HOST=:: \
    PORT=8084

CMD ["bun", "run", "src/skall.ts"]
