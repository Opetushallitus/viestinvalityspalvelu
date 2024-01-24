## Sovelluksen käynnistäminen paikallisesti

Lokaali käynnistys tapahtyy komennolla:

```bash
npm run dev
```

ja löytyy osoitteesta [http://localhost:3000](http://localhost:3000) 

Lokaaliympäristössä palvelu toimii lokaalia viestinvälityspalvelua vasten, ks.
https://github.com/Opetushallitus/viestinvalityspalvelu

TODO lokaalikehitys testiympäristön viestintäpalvelua vasten

## Yksikkötestien ajo

Yksikkötestit on tehty vitestillä. Testit saa ajettua komennolla

```bash
npm test
```

## Teknologioista

Sovellus on toteutettu [Next.js](https://nextjs.org/) -frameworkilla ja luotu [`create-next-app`](https://github.com/vercel/next.js/tree/canary/packages/create-next-app) -työkalulla.

Käyttöliittymäkomponenteissa on käytetty [Material UI](https://mui.com/material-ui/getting-started/) -kirjastoa

## FYI

Bugin vuoksi juurihakemistossa täytyy olla tyhjä /pages -hakemisto, jotta middleware tulee ajettua lokaalikehitysmoodissa. Ks. https://github.com/vercel/next.js/issues/43141

Tämän korjaantumista kannattanee seurailla nextjs-versiopäivityksissä ja poistaa turha hakemisto kun issue on korjattu.

Jotta lokaaliympäristössä voi käyttää https-apeja self signed sertifikaatilla, täytyy package.jsonin dev-käynnistysasetuksissa olla tämä konfiguraatio:

```bash
NODE_TLS_REJECT_UNAUTHORIZED=0
```

Ks. https://code-specialist.com/cloud/nextjs-self-signed