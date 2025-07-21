## Sovelluksen käynnistäminen paikallisesti

Kopioi ympäristökohtaiset konffit .env.template-tiedostosta .env.local -nimiseen tiedostoon

Lokaali käynnistys tapahtyy komennolla:

```bash
npm run dev
```

ja löytyy osoitteesta [http://localhost:3000/raportointi](http://localhost:3000/raportointi) 

Lokaaliympäristössä palvelu toimii lokaalia viestinvälityspalvelua vasten, ks.
https://github.com/Opetushallitus/viestinvalityspalvelu

Toistaiseksi käyttöliittymää ei voi ajaa lokaalisti testiympäristön backendia vasten testiympäristöihin konffattujen uudelleenohjauksien takia.

## Yksikkötestien ajo

Yksikkötestit on tehty vitestillä. Testit saa ajettua komennolla

```bash
npm test
```

## Ympäristöihin asennettava build

Asennuspaketointi tapahtuu komennolla

```bash
npm run build
```

Standalone-paketoitu sovellus löytyy hakemistosta .next/standalone ja deploy-skripti kopioi sen haluttuun ympäristöön.

HUOM! Kaikki tarvittavat tiedostot eivät kopioidu standalone-hakemistoon vaan static-hakemiston sisältö pitää kopioida erikseen ks. https://nextjs.org/docs/app/api-reference/next-config-js/output#automatically-copying-traced-files

## Lokaali ajaminen käyttäen testiympäristön CAS-autentikointia

Käytä raportointi-backendissa profiilia "caslocal" (asetetaan tiedostossa integraatio/src/test/resources/application.properties).
Vaihda .env.local -tiedostoon backend- ja login-urleihin templatesta löytyvät https-osoitteet.

## Tuotantobuildin (standalone) ajaminen lokaalisti 

```bash
npm run build
npm run start
```
Päivitä tarvittaessa .env.local -tiedostoon käyttämäsi backend-url (http tai https)

## Teknologioista

Sovellus on toteutettu [Next.js](https://nextjs.org/) -frameworkilla ja luotu [`create-next-app`](https://github.com/vercel/next.js/tree/canary/packages/create-next-app) -työkalulla.

Käyttöliittymäkomponenteissa on käytetty [Material UI](https://mui.com/material-ui/getting-started/) -kirjastoa

## Lokalisoinnit

Lokalisointiin on käytetty [Next-intl] (https://next-intl-docs.vercel.app/) -kirjastoa.

Nextjs:n tukema lokalisointimalli nojaa vahvasti route-pohjaiseen ratkaisuun. 
Jos ei haluta kielistystä osoitepolkuun, täytyy tehdä omaa toteutusta mm.
siihen mistä käyttäjän kieli ja sen mahdollinen vaihtuminen tunnistetaan. 
ks. https://next-intl-docs.vercel.app/docs/getting-started/app-router/without-i18n-routing

## FYI

Jotta lokaaliympäristössä voi käyttää https-apeja self signed sertifikaatilla (esim lokaali Spring Boot backend), täytyy .env.local ympäristömuuttujissa olla tämä konfiguraatio:

```bash
NODE_TLS_REJECT_UNAUTHORIZED=0
```

Ks. https://code-specialist.com/cloud/nextjs-self-signed