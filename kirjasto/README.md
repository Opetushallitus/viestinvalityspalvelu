## Viestinvälityspalvelu kirjasto

Kirjaston avulla asiakasjärjestelmät voivat käyttää viestinvälityspalvelua java-rajapinnan läpi. Käyttöön tarvitaan
tämä riippuvuus, sekä palvelutunnus jolla on tarvittavat oikeudet viestien lähettämiseksi. Kirjaston transitiiviset
riippuvuudet on pyritty minimoimaan.

### Käyttö

Client instanssi luodaan builderilla, esim:

```
      ViestinvalitysClient client = ClientBuilder.viestinvalitysClientBuilder()
        .withEndpoint(<viestinvälityspalvelun osoite, esim: "https://viestinvalitys.hahtuvaopintopolku.fi">)
        .withUsername(<palvelutunnus>)
        .withPassword(<salana>)
        .withCasEndpoint(<cas-osoite, esim: https://virkalija.hahtuvaopintopolku.fi/cas>)
        .withCallerId(<caller id>)
        .build()
```

Tämän jälkeen client-instanssilla voi luoda pyyntöjä jotka luovat liitteitä, lähetyksiä, ja viestejä, sekä tarkastella
näiden tilaa, Esim. seuraavasti:

Voidaan joko luoda ensin lähetys ja liittää samaan lähetykseen useita viestejä. Lähetysten käyttö on tarpeellista esim.
tilanteissa joissa a) haluataan tarkastella useita vastaanottajakohtaisesti kustomoituja viestejä kokonaisuutena, tai
b) viestin kokonaisvastaanottajamäärä ylittää yksittäisen viestin maksimivastaanottajamäärän (ks. lähetysrajapinnan
Viesti-luokka), jolloin viesti täytyy palastella useammaksi viestiksi.

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
.withHtmlSisalto("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\"
    \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html><head><meta http-equiv=\"Content-Type\"
    content=\"text/html; charset=UTF-8\" /><title></title></head><body style=\"margin: 0; font-family: 'Open Sans',
    Arial, sans-serif;\"><H1>Otsikko</h1><p>Viestin sisältö</p><p>Ystävällisin terveisin<br/>Opintopolku</p></body></html>")
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

On suositeltavaa käyttää builder-luokkia lähetysten, viestien jne. luomiseen. Tällöin kaikki pakolliset kentät tulevat
kaikissa tilanteissa täytettyä.

### Validointi

Rajapinta pyrkii validoimaan kaikkien pyyntöjen kaikki kentät, ja kaikille kentille on pyritty asettamaan esim.
maksimipituus. Erityisesti tulee huomata että yksittäisellä viestillä on maksimimäärä vastaanottajia, ja tätä
suuremmalle vastaanottajajoukolle suunnatut viestit tulee palastella useammaksi yksittäiseksi viestiksi. Yksittäisten
kenttien rajoitteet on pyritty kuvaamaan kattavasti Swagger-kuvauksessa, joka puolestaan pyrkii perustumaan suoraan
lähdekoodissa olevien vakioihin ylläpidettävyyden varmistamiseksi.

### Idempotency-avain -toiminnallisuus

Rajapinta sisältää toiminnallisuuden jonka avulla voidaan varmistua siitä ettei samaa viestiä lähetetä toistuvasti
viestinvälityspalvelun tai asiakasjärjestelmän virheen seurauksena. Viestin mukaan voidaan liittää uniikki
idempotencyKey-avain, joka tallennetaan viestinvälityspalveluun. Mikäli sama asiakasjärjestelmä (ts. cas-identiteetti)
yrittää lähettää uutta viestiä samalla avaimella, palautetaan aikaisemman viestin tiedot.

HUOMAA että jos lähetettävä viesti on palasteltu useammaksi viestiksi koska vastaanottajien kokonaismäärä ylittää
yksittäisen viestin maksimivastaanottajamäärä, pitää kaikille viesteille luonnollisesti olla oma idempotency-avain!

### Kirjaston päivitys

Jos kirjastoa on tarve muuttaa tai päivittää, nosta projektin parent-pomissa oleva revision 
ja päivitä uusi snapshot-numero clientia käyttäviin palveluihin.
