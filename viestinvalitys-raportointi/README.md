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

TODO prod buildin ajaminen lokaalisti, lokaalikehitys testiympäristön CASia vasten

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

## Tuotantobuildin ajaminen lokaalisti temp hax

Tuotantobuildin ajaminen lokaalisti on toistaiseksi hieman kömpelö viritys. Tuotantobuildin käynnistävä skripti pitää muokata käsin vastaamaan lokaaliympäristöä.
Buildin tekemisen jälkeen korvaa tiedoston .next/standalone.run.sh sisältö: 
* säärä portti niin ettei se ole päällekkäinen lokaalin backendin kanssa
* muuta ympäristömuuttujat vastaamaan lokaalia backendia
* muuta server.js-tiedostopolku vastaamaan tuotantobuildin hakemistoa

Esim. näin:
```bash
#!/bin/bash

export PORT=8081
export VIESTINTAPALVELU_URL=http://localhost:8080
export LOGIN_URL=http://localhost:8080/login
export COOKIE_NAME=JSESSIONID

node .next/standalone/server.js
```
Tämän jälkeen käyttöliittymä käynnistyy komennolla
```bash
.next/standalone/run.sh
```
HUOM! Muistaa perua nämä muutokset tai tehdä uusi build ennen asentamista testiympäristöön!

## Teknologioista

Sovellus on toteutettu [Next.js](https://nextjs.org/) -frameworkilla ja luotu [`create-next-app`](https://github.com/vercel/next.js/tree/canary/packages/create-next-app) -työkalulla.

Käyttöliittymäkomponenteissa on käytetty [Material UI](https://mui.com/material-ui/getting-started/) -kirjastoa

## Asennusympäristö

Sovellus paketoidaan "standalone"-muodossa https://nextjs.org/docs/app/api-reference/next-config-js/output#automatically-copying-traced-files

OPH:n ympäristöissä sovellus toimii aws-lambdoissa nodejs-ajoympäristössä, mikä on toistaiseksi Nextjs:n oletusajoympäristö. 
Tietyt Nextjs-dokumentaatiossa mainitut ominaisuudet kuten middlewaret ovat käytettävissä vain edge-ajoympäristössä, ks. https://nextjs.org/docs/app/building-your-application/routing/middleware#runtime
Tämän vuoksi esim. http-otsakkeiden ja evästeiden käsittely täytyy tehdä middlewaren sijaan palvelinkomponenteissa.

## FYI

Ei kannata päivittää nextjs-versiota ennenkuin yhteensopivuusongelmat Mui-komponenttien kanssa on korjattu. Toimii versiossa 14.0.3, rikki 14.0.4. 
Ks. https://github.com/vercel/next.js/issues/59804

Jotta lokaaliympäristössä voi käyttää https-apeja self signed sertifikaatilla, täytyy package.jsonin dev-käynnistysasetuksissa olla tämä konfiguraatio:

```bash
NODE_TLS_REJECT_UNAUTHORIZED=0
```

Ks. https://code-specialist.com/cloud/nextjs-self-signed