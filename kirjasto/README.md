## Viestinvälityspalvelu kirjasto

Kirjaston avulla asiakasjärjestelmät voivat käyttää viestinvälityspalvelua java-rajapinnan läpi.

### Käyttö

Client instanssi luodaan builderilla, esim:

```
      ViestinvalitysClient client = ViestinvalitysClient.builder()
        .withEndpoint(<viestinvälityspalvelun osoite, esim: "https://viestinvalitys.hahtuvaopintopolku.fi">)
        .withUsername(<käyttäjätunnus>)
        .withPassword(<salana>)
        .withCasEndpoint(<cas-osoite, esim: https://virkalija.hahtuvaopintopolku.fi/cas>)
        .withCallerId(<caller id>)
        .build()
```

Tämän jälkeen client-instanssilla voi luoda pyyntöjä jotka luovat liitteitä, lähetyksiä, ja viestejä, sekä tarkastelevat näiden tilaa, Esim. viesti luodaan seuraavasti:

```
      LuoViestiResponse response = viestinvalitysClient.luoViesti(Viesti.builder()
        .withOtsikko("testiotsikko")
        .withTextSisalto("testisisältö")
        .withKielet("fi")
        .withLahettaja(Optional.empty(), "noreply@opintopolku.fi")
        .withVastaanottajat(Vastaanottajat.builder()
          .withVastaanottaja(Optional.empty(), "test@example.com")
          .build())
        .withNormaaliPrioriteetti()
        .withSailytysAika(1)
        .withLahettavaPalvelu("virkailijatyopoyta")
        .build())
```
