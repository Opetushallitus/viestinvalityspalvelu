## Viestinvälityspalvelu

Viestinvälityspalvelun avulla sovellukset voivat lähettää sähköpostiviestejä OPH:n nimissä. 

### Arkkitehtuuri

Viestinvälityspalvelu on toteutettu lambdoilla, ja siinä on seuraavat osat:

1. Lähetyspyyntöjen vastaanotto ja tilan raportointi

    Tämä komponentti sisältää endpointit joiden avulla viestinvälityspalvelun asiakasjärjestelmät voivat:
    - Luoda uusia lähetyksiä (aggregaatioentiteetti jonka avulla joukkoa viestejä voidaan tarkastella kokonaisuutena)
    - Tallentaa uusia liitetiedostoja
    - Luoda uusia viestejä
    - Tarkkailla lähetysten ja viestien tilaa


2. Liitetiedostojen skannaus
    
    Ympäristöihin on asennettu BucketAV-palvelu skannaamaan S3-palveluun tallennettuja tiedostoja. Tämä komponentti
kuuntelee BucketAV-palvelulta tulevia SNS-notifikaatioita ja päivittää sen perusteella tallenetut liitetiedostot
puhtaiksi tai saastuneiksi.


3. Lähetysten ajastamisen ajastaminen

    Tämän komponentin ainoa tarkoitus on mahdollistaa lähetysvalmiiden viestien pollaus muutaman sekunnin välein.
Erillinen komponentti on tarpeellinen, koska pienin aikaikkuna johon lambdan toistuvan ajon voi aikatauluttaa on yksi
minuutti.


4. Lähetysvalmiiden viestien pollaus

    Tämä komponentti pollaa tietokantaa ja ottaa lähetykseen maksimissaan tietyn määrän viestien vastaanottajia. ettei
SES-palvelun lähetysquota ylity.


5. Lähetys

    Tämä komponentti hoitaa itse lähetyksen.


6. Lähetyksen tilan päivitys

    Tämä komponentti kuuntelee SES-palvelulta tulevia notifikaatioita ja päivittää sen perusteella yksittäiselle
vastaanottajalle tehdyn lähetyksen tilaa.


7. Vanhojen viestien poisto

    Tämä komponentti poistaa vanhat viestit, vastaanottajat, liitteet ja lähetykset kun viesteille määritelty
säilytysaika on päättynyt.


### Lokaali Ympäristö

Lokaali ympäristö emuloi sovelluksen keskeisiä toiminnallisuuksia. Se perustuu Spring Boot -sovellukseen, koska tällä
tavalla featurekehitys on yksinkertaisempaa (esim. debuggerin voi laittaa kiinni yhteen JVM:ään eikä jokaiseen
lambdaan erikseen). Localstackia käytetään ainoastaan S3-liitetiedostobucketin osalta.

Lokaalin ympäristön käyttöönotto

1. Asenna docker-compose: https://docs.docker.com/compose/install/
2. Mene hakemistoon ./lokaali/docker
3. Käynnistä docker-ympäristö komennolla: docker-compose up
4. Käynnistä lokaali sovellus ajamalla luokka fi.oph.viestinvalitys.vastaanotto.DevApp
5. Kirjaudu sisään sovellukseen menemällä osoitteeseen: https://localhost:8443/v2/resource/healthcheck
6. Mene osoitteeseen: https://localhost:8443/swagger, kaikkia kutsuja pitäisi pystyä kokeilemaan esimerkkiparametreilla
7. Järjestelmän tila pitää toistaiseksi tarkastaa kannasta (salasana on "app"): psql -U app --host localhost -d viestinvalitys

Huomaa että Localstack-ympäristö ei persistoi tilaansa, joten jos sammutat docker-composen, niin tallennetut liitteet
katoavat S3-bucketista (kannassa ne säilyvät).

### Asennus hahtuva-testiympäristöön

1. Asenna aws vault: https://github.com/99designs/aws-vault
2. Aja juuressa ./deploy.sh hahtuva deploy
3. Kirjaudu sisään sovellukseen osoitteessa: https://viestinvalitys.hahtuvaopintopolku.fi/v2/resource/healthcheck
4. Swagger on osoitteessa: https://viestinvalitys.hahtuvaopintopolku.fi/swagger