## Viestinvälityspalvelu kirjasto

Kirjaston avulla asiakasjärjestelmät voivat käyttää viestinvälityspalvelua java-rajapinnan läpi.

### Käyttö

Client instanssi luodaan builderilla, esim:

```
      ViestinvalitysClient client = ClientBuilder.viestinvalitysClientBuilder()
        .withEndpoint(<viestinvälityspalvelun osoite, esim: "https://viestinvalitys.hahtuvaopintopolku.fi">)
        .withUsername(<käyttäjätunnus>)
        .withPassword(<salana>)
        .withCasEndpoint(<cas-osoite, esim: https://virkalija.hahtuvaopintopolku.fi/cas>)
        .withCallerId(<caller id>)
        .build()
```

Tämän jälkeen client-instanssilla voi luoda pyyntöjä jotka luovat liitteitä, lähetyksiä, ja viestejä, sekä tarkastelevat näiden tilaa, Esim. seuraavasti:

Voidaan joko luoda ensin lähetys ja liittää samaan lähetykseen useita viestejä

```
LuolahetysResponse luoLahetysResponse = viestinvalitysClient.luoLahetys(
ViestinvalitysBuilder.lahetysBuilder()
.withOtsikko("Lahetyksen otsikko")
.withLahettavaPalvelu("virkailijantyopoyta")
.withLahettaja(Optional.empty(), "noreply@opintopolku.fi")
.withNormaaliPrioriteetti()
.withSailytysaika(365)
.withLahettavanVirkailijanOid("1.2.246.562.24.1")
.build())

ViestinvalitysBuilder.viestiBuilder()
.withOtsikko("viestin otsikko")
.withHtmlSisalto("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" /><title></title></head><body style=\"margin: 0; font-family: 'Open Sans', Arial, sans-serif;\"><H1>Otsikko</h1><p>Viestin sisältö</p><p>Ystävällisin terveisin<br/>Opintopolku</p></body></html>")
.withKielet("fi")
.withVastaanottajat(ViestinvalitysBuilder.vastaanottajatBuilder()
          .withVastaanottaja(Optional.empty(), "test@example.com")
          .build())
.withKayttooikeusRajoitukset(ViestinvalitysBuilder.kayttooikeusrajoituksetBuilder()
          .withKayttooikeus("APP_HAKEMUS_CRUD", "1.2.246.562.10.240484683010")
          .build())
.withLahetysTunniste(lahetysTunniste.toString)
.build()
```

Tai luoda viestejä erillisinä lähetyksinä
```
      LuoViestiResponse response = viestinvalitysClient.luoViesti(
      ViestinvalitysBuilder.viestiBuilder()
        .withOtsikko("testiotsikko")
        .withTextSisalto("testisisältö")
        .withKielet("fi")
        .withVastaanottajat(ViestinvalitysBuilder.vastaanottajatBuilder()
          .withVastaanottaja(Optional.empty(), "test@example.com")
          .build())
        .withNormaaliPrioriteetti()
        .withSailytysAika(365)
        .withLahettavaPalvelu("virkailijantyopoyta")
        .withKayttooikeusRajoitukset(ViestinvalitysBuilder.kayttooikeusrajoituksetBuilder()
             .withKayttooikeus("APP_HAKEMUS_CRUD", "1.2.246.562.10.240484683010")
             .build())
        .withLahettaja(Optional.empty(), "noreply@opintopolku.fi")
        .build())
```
### Kirjaston päivitys

Jos kirjastoa on tarve muuttaa tai päivittää, nosta projektin parent-pomissa oleva revision 
ja päivitä uusi snapshot-numero clientia käyttäviin palveluihin.
