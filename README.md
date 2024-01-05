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
lambdaan erikseen). Localstackia käytetään ainoastaan S3-liitetiedostobucketin, SES:in, ja SQS:n osalta.

Lokaalin ympäristön käyttöönotto

1. Asenna docker-compose: https://docs.docker.com/compose/install/
2. Mene hakemistoon ./integraatio/docker
3. Käynnistä docker-ympäristö komennolla: docker-compose up
4. Käynnistä lokaali sovellus ajamalla mainMethod-metodi luokassa fi.oph.viestinvalitys.vastaanotto.DevApp
5. Kirjaudu sisään sovellukseen menemällä osoitteeseen: https://localhost:8080/login
6. Mene osoitteeseen: https://localhost:8080/swagger, kaikkia kutsuja pitäisi pystyä kokeilemaan esimerkkiparametreilla
7. Järjestelmän tila pitää toistaiseksi tarkastaa kannasta (salasana on "app"): psql -U app --host localhost -d viestinvalitys

Lähtökohtaisesti mailit ohjautuvat MailCatcheriin joka löytyy osoitteesta http://localhost:1080. SES delivery-
eventtiä voi testata liittämällä vastaanottajan nimiosaan liitteen +success (esim. vallu.vastaanottaja+success@example.com),
jolloin maili ohjataan Localstackin SES-palvelulle joka palauttaa Delivery-eventin. Myös +bounce ja +complaint
-liitteet sisältävät osoitteet ohjataan Localstack SES -palvelulle, mutta se ei toistaiseksi tue Bounce- ja
Complaint-eventtejä.

Huomaa että Localstack-ympäristö ei persistoi tilaansa, joten jos sammutat docker-composen, niin tallennetut liitteet
katoavat S3-bucketista (kannassa ne säilyvät).

### Asennus hahtuva-testiympäristöön

1. Asenna aws vault: https://github.com/99designs/aws-vault
2. Asenna cdk cli (esim. homebrew:lla)
3. Aja juuressa ./deploy.sh hahtuva deploy
4. Kirjaudu sisään sovellukseen osoitteessa: https://viestinvalitys.hahtuvaopintopolku.fi/login
5. Swagger on osoitteessa: https://viestinvalitys.hahtuvaopintopolku.fi/swagger

### Tietokannan luonti uuteen ympäristöön

1. Kirjaudu sisään kantaan bastionilta oph-tunnuksella: psql -u oph --host viestinvalitys.db.hahtuvaopintopolku.fi -d postgres
2. Luo tietokanta: CREATE DATABASE viestinvalitys;
3. Aja (lokaalisti) sovelluskäyttäjien luomiseksi skripti: tools/db/update-postgres-db-roles.sh <ympäristö> viestinvalitys

### Kuormatestaus

1. Käynnistä kuormatestausympäristö komennolla: ./deploy.sh <ympäristö> loadup
2. Kirjaudu sisään kuormatestausinstanssiin komennolla: ./loadtesting/ssh.sh <ympäristö>
3. Aja kuormatesti komennolla: k6 run script.js
4. Seuraa ajon kulkua dashboardilta: https://eu-west-1.console.aws.amazon.com/cloudwatch/home?region=eu-west-1#dashboards/dashboard/Viestinvalitys
5. Tuhoa kuormatestausympäristö komennolla: ./deploy.sh <ympäristö> loaddown
