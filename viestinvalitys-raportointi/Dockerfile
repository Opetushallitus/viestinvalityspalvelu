FROM node:22-alpine AS builder
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# next.config.mjs: output: 'standalone', basePath: '/raportointi'
RUN npx next build

FROM node:22-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV PORT=8080
# server.js kuuntelee HOSTNAME:PORT; kontissa on kuunneltava kaikkia rajapintoja
ENV HOSTNAME=0.0.0.0

# Standalone-serveri ja sen tarvitsemat staattiset tiedostot.
# (Sovelluksella ei ole public-hakemistoa, joten sitä ei kopioida.)
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static

EXPOSE 8080

CMD ["node", "server.js"]
