## Viestinvälityspalvelu

Viestinvälityspalvelun avulla sovellukset voivat lähettää sähköpostiviestejä OPH:n nimissä. 

### Arkkitehtuuri

Arkkitehtuurikuvaus on osoitteessa: https://wiki.eduuni.fi/pages/viewpage.action?pageId=395905952

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


4. Lähetysvalmiiden viestien pollaus ja lähetys

    Tämä komponentti pollaa tietokantaa ja ottaa lähetykseen maksimissaan tietyn määrän viestien vastaanottajia ettei
SES-palvelun lähetysquota ylity.


5. Lähetyksen tilan päivitys

    Tämä komponentti kuuntelee SES-palvelulta tulevia notifikaatioita ja päivittää sen perusteella yksittäiselle
vastaanottajalle tehdyn lähetyksen tilaa.


6. Vanhojen viestien poisto

    Tämä komponentti poistaa vanhat viestit, vastaanottajat, liitteet ja lähetykset kun viesteille määritelty
säilytysaika on päättynyt.

7. Raportointi

   Tämä komponentti sisältää viestinvälityspalvelun käyttöliittymän tarvitsemat endpointit.

### Lokaali ympäristö

Lokaali ympäristö emuloi sovelluksen keskeisiä toiminnallisuuksia. Se perustuu Spring Boot -sovellukseen, koska tällä
tavalla featurekehitys on yksinkertaisempaa (esim. debuggerin voi laittaa kiinni yhteen JVM:ään eikä jokaiseen
lambdaan erikseen). Localstackia käytetään ainoastaan S3-liitetiedostobucketin, SES:in, SQS:n ja CloudWatchin osalta.
Osaa toiminnallisuuksista (esim. lähetyksen ajastus, BucketAV-skannaus) simuloidaan testikoodilla.

Lokaali ympäristö ei oletuksena käytä CAS-integraatiota, vaan spring-konfiguraatiossa on määritelty testikäyttäjät (ks.
integraatioprojektin SecurityConfiguration-luokka), näin a) lokaali kehitys ei ole riippuvainen CAS-yhteydestä, ja b)
samaa konfiguraatiota voidaan käyttää integraatiotesteissä.

Lokaalin ympäristön käyttöönotto

1. Asenna docker-compose: https://docs.docker.com/compose/install/
2. Mene hakemistoon ./integraatio/docker
3. Käynnistä docker-ympäristö komennolla: docker-compose up
4. Käynnistä lokaali sovellus ajamalla mainMethod-metodi luokassa fi.oph.viestinvalitys.vastaanotto.DevApp. Käynnistyksen
   yhteydessä luodaan tarvittavat komponentit localstackiin (S3-bucketit, SQS-jonot)
5. Kirjaudu sisään sovellukseen menemällä osoitteeseen: https://localhost:8080/login (tunnukset esim. user/password)
6. Mene osoitteeseen: https://localhost:8080/swagger, kaikkia kutsuja pitäisi pystyä kokeilemaan esimerkkiparametreilla
7. Järjestelmän tilaa voi seurata kannasta (salasana on "app"): psql -U app --host localhost -d viestinvalitys

Lähtökohtaisesti mailit ohjautuvat MailCatcheriin joka löytyy osoitteesta http://localhost:1080. Lähetetyn viestin tilan
päivitystä (SES -eventtejä) voi testata liittämällä vastaanottajan nimiosaan liitteen +success (esim. vallu.vastaanottaja+success@example.com),
jolloin maili ohjataan Localstackin SES-palvelulle joka palauttaa Delivery-eventin. Myös +bounce ja +complaint
-liitteet sisältävät osoitteet ohjataan Localstack SES -palvelulle, mutta se ei toistaiseksi tue Bounce- ja
Complaint-eventtejä.

Huomaa että Localstack-ympäristö ei persistoi tilaansa, joten jos sammutat docker-composen, niin tallennetut liitteet
katoavat S3-bucketista (kannassa ne säilyvät).

### Lokaali ympäristö CAS-autentikoinnilla

Lokaalia Spring Boot -sovellusta voi ajaa myös CAS-autentikointia käyttäen. Tämä onnistuu vaihtamalla integraatio-projektin application.properties-tiedostossa profiiliksi `caslocal`. 
Tällöin tulee käyttöön erillinen Spring Security -konfiguraatio luokassa CasSecurityConfiguration. 

Oletuksena käytetään CAS-autentikoinnin ja muiden järjestelmien rajapintojen osalta hahtuva-ympäristöä. Testiympäristön voi vaihtaa DevApp-tiedostossa olevia osoitteita muokkaamalla.

CAS-kirjautumista käytettäessä myös mäyttöliittymän env.local-tiedostoon on päivitettävä raportointi-backendin osoite ja kirjautumisosoite env.templatessa olevan esimerkin mukaan.

HUOM! Integraatiotestejä ajettaessa täytyy olla dev-profiili käytössä jotta formlogin toimii.

### Asennus testiympäristöön

1. Asenna aws vault: https://github.com/99designs/aws-vault
2. Asenna cdk cli (esim. homebrew:lla)
3. Aja cdk-hakemistossa `npm ci`
4. Tee tarvittaessa palveluista tuoreet buildit
5. Aja juuressa ./deploy.sh hahtuva deploy
6. Kirjaudu sisään sovellukseen osoitteessa: https://viestinvalitys.hahtuvaopintopolku.fi/lahetys/login
7. Swagger on osoitteessa: https://viestinvalitys.hahtuvaopintopolku.fi/swagger

Lisäksi integraatioita varten ympäristön parameter storessa on oltava cas-autentikaation palvelutunnuksen salasana:
- /<ympäristö>/viestinvalitys/palvelutunnus-password

### Tietokannan luonti uuteen ympäristöön

1. Deployaa persistenssi-stack
2. Luo seuraavat parametrit ssm:ssä: 
- /<ympäristö>/postgresqls/viestinvalityspalvelu/app-user-password
- /<ympäristö>/postgresqls/viestinvalityspalvelu/master-user-password
- /<ympäristö>/postgresqls/viestinvalityspalvelu/readonly-user-password
3. Aseta oph-käyttäjän salasana RDS-kannalle (master-user-password ks. yllä)
4. Kirjaudu sisään kantaan bastionilta oph-tunnuksella: psql -U oph --host viestinvalitys.db.<ympäristö>opintopolku.fi -d postgres
5. Luo tietokanta: CREATE DATABASE viestinvalitys;
6. Aja (lokaalisti) sovelluskäyttäjien luomiseksi skripti: tools/db/update-postgres-db-roles.sh <ympäristö> viestinvalitys

### Tietokantamigraatiot

Toistaiseksi tietokantamigraatioita ei ajeta automaattisesti deployn yhteydessä, vaan ne pitää ajaa erikseen käsin migraatiolambdan kautta.
Etsi AWS-consolessa lambda-funktioista halutun ympäristön [ympäristö]-viestinvalityspalvelu-migraatio lambda ja käynnistä se Test-välilehden kautta.

### Kuormatestaus

1. Käynnistä kuormatestausympäristö komennolla: ./deploy.sh <ympäristö> loadup
2. Kirjaudu sisään kuormatestausinstanssiin komennolla: ./loadtesting/ssh.sh <ympäristö>
3. Käynnistä shelli komennolla: bash
4. ja kuormatesti komennolla: k6 run script.js
5. Seuraa ajon kulkua Cloudwatchin dashboardilta, dashboardin nimi on <ympäristö>-viestinvalitys

   Dashboardista pitäisi näkyä seuraavat vaiheet:
   - Rampup: lähetettyjen viesti (sekä korkea että normaali prioriteetti) määrä nousee samassa tahdissa lähetyspyyntöjen kanssa
   - Maksimi lähetysnopeus ylittyy: jolloin normaaliprioriteetin viestien lähetysnopeus alkaa laskea
   - Korkean priorieetin viestin määrä ylittää maksimilähetysnopeuden: normaalien prioriteetin viestejä ei lähetetä,
     korkean prioriteetin viestejä lähetetään hitaammin kuin niitä saapuu
   - Rampdown: jossain vaiheessa ramp-downia korkean prioriteetin jono tyhjenee jolloin normaalin prioriteetin viestejä
     aletaan jälleen lähettää
   - Normaalin prioriteetin jonon purku: ajon loputtua järjestelmä purkaa maksimilähetysnopeudella normaalin prioriteetin jonon

6. Tutki tulokset konsolilta. Raportilla ei pitäisi olla virheitä. Erityisesti skaalausvaiheessa yksittäiset pyynnöt
   voivat kestää pitkään, mutta palvelulle ei ainakaan toistaiseksi ole määritelty SLA:ta.

7. Tuhoa kuormatestausympäristö komennolla: ./deploy.sh <ympäristö> loaddown (muista tehdä tämä!)

Kuormatestaus pituus on tarkoituksella rajattu lyhyeksi, koska viestien lähettäminen SES simulaattorin laskutetaan
normaalitaksoilla, jolloin hinnaksi tulee noin 1 euro/minuutti.

Kuormatestauksen ajo jokaisen tiketin yhteydessä on suositeltavaa. Koska:
   - Kesto on lyhyt joten ylimääräinen vaiva on pieni
   - Apimuutokset tms. voivat rikkoa skriptin, joten skriptin toimivuus tulee testattua samalla