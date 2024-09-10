## Sovelluksen käynnistäminen paikallisesti

Kopioi ympäristökohtaiset konffit .env.template-tiedostosta .env.local -nimiseen tiedostoon

Lokaali käynnistys tapahtyy komennolla:

```bash
npm run dev
```

ja löytyy osoitteesta [http://localhost:3000](http://localhost:3000) 

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

## Lokaali ajaminen käyttäen testiympäristön CAS-autentikointia

Käytä raportointi-backendissa profiilia "caslocal" ().
Vaihda .env.local -tiedostoon backend- ja login-urleihin templatesta löytyvät https-osoitteet.

## Tuotantobuildin ajaminen lokaalisti temp hax

Tuotantobuildin ajaminen lokaalisti on toistaiseksi hieman kömpelö viritys. Tuotantobuildin käynnistävä skripti pitää muokata käsin vastaamaan lokaaliympäristöä.
Buildin tekemisen jälkeen korvaa tiedoston .next/standalone.run.sh sisältö: 
* säädä portti niin ettei se ole päällekkäinen lokaalin backendin kanssa
* muuta ympäristömuuttujat vastaamaan lokaalia backendia
* muuta server.js-tiedostopolku vastaamaan tuotantobuildin hakemistoa

Esim. näin:
```bash
#!/bin/bash

export PORT=8081
export VIRKAILIJA_URL=https://virkailija.hahtuvaopintopolku.fi
export VIESTINTAPALVELU_URL=http://localhost:8080
export LOGIN_URL=http://localhost:8080/login
export COOKIE_NAME=JSESSIONID

node .next/standalone/server.js
```
Tämän jälkeen sovellus käynnistyy komennolla
```bash
.next/standalone/run.sh
```

ja käyttöliittymä on osoitteessa http://localhost:8081/raportointi

HUOM! Muistaa perua nämä muutokset tai tehdä uusi build ennen asentamista testiympäristöön!

## Teknologioista

Sovellus on toteutettu [Next.js](https://nextjs.org/) -frameworkilla ja luotu [`create-next-app`](https://github.com/vercel/next.js/tree/canary/packages/create-next-app) -työkalulla.

Käyttöliittymäkomponenteissa on käytetty [Material UI](https://mui.com/material-ui/getting-started/) -kirjastoa

## Asennusympäristö

Sovellus paketoidaan "standalone"-muodossa https://nextjs.org/docs/app/api-reference/next-config-js/output#automatically-copying-traced-files

OPH:n ympäristöissä sovellus toimii aws-lambdoissa nodejs-ajoympäristössä, mikä on toistaiseksi Nextjs:n oletusajoympäristö. 
Tietyt Nextjs-dokumentaatiossa mainitut ominaisuudet kuten middlewaret ovat käytettävissä vain edge-ajoympäristössä, ks. https://nextjs.org/docs/app/building-your-application/routing/middleware#runtime
Tämän vuoksi esim. http-otsakkeiden ja evästeiden käsittely täytyy tehdä middlewaren sijaan palvelinkomponenteissa.

## Lokalisoinnit

Nextjs:n tukema lokalisointimalli nojaa vahvasti route-pohjaiseen ratkaisuun. 
Jos ei haluta kielistystä osoitepolkuun, täytyy tehdä enemmän omaa toteutusta mm.
siihen mistä käyttäjän kieli ja sen mahdollinen vaihtuminen tunnistetaan sekä alustaa kielistys erikseen server- ja client-komponenteille.
Ks. https://github.com/vercel/next.js/discussions/29932
Raportointikäyttöliittymässä käytetty ratkaisu mukailee tätä esimerkkiä: 
https://carlogino.com/blog/nextjs-app-dir-i18n-cookie
vastaavan tyyppinen client-alustus myös tässä:
https://locize.com/blog/next-app-dir-i18n/#step-5

## FYI

Jotta lokaaliympäristössä voi käyttää https-apeja self signed sertifikaatilla, täytyy package.jsonin dev-käynnistysasetuksissa olla tämä konfiguraatio:

```bash
NODE_TLS_REJECT_UNAUTHORIZED=0
```

Ks. https://code-specialist.com/cloud/nextjs-self-signed