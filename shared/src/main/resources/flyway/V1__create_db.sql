-- lähetysrajapinnan sessiot
CREATE UNLOGGED TABLE LAHETYS_SESSION (
  PRIMARY_ID CHAR(36) NOT NULL,
  SESSION_ID CHAR(36) NOT NULL,
  CREATION_TIME BIGINT NOT NULL,
  LAST_ACCESS_TIME BIGINT NOT NULL,
  MAX_INACTIVE_INTERVAL INT NOT NULL,
  EXPIRY_TIME BIGINT NOT NULL,
  PRINCIPAL_NAME VARCHAR(100),
  CONSTRAINT LAHETYS_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX LAHETYS_SESSION_IX1 ON LAHETYS_SESSION (SESSION_ID);
CREATE INDEX LAHETYS_SESSION_IX2 ON LAHETYS_SESSION (EXPIRY_TIME);
CREATE INDEX LAHETYS_SESSION_IX3 ON LAHETYS_SESSION (PRINCIPAL_NAME);

CREATE UNLOGGED TABLE LAHETYS_SESSION_ATTRIBUTES (
  SESSION_PRIMARY_ID CHAR(36) NOT NULL,
  ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
  ATTRIBUTE_BYTES BYTEA NOT NULL,
  CONSTRAINT LAHETYS_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
  CONSTRAINT LAHETYS_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES LAHETYS_SESSION(PRIMARY_ID) ON DELETE CASCADE
);

-- lähetysrajapinnan sessiot
CREATE UNLOGGED TABLE RAPORTOINTI_SESSION (
  PRIMARY_ID CHAR(36) NOT NULL,
  SESSION_ID CHAR(36) NOT NULL,
  CREATION_TIME BIGINT NOT NULL,
  LAST_ACCESS_TIME BIGINT NOT NULL,
  MAX_INACTIVE_INTERVAL INT NOT NULL,
  EXPIRY_TIME BIGINT NOT NULL,
  PRINCIPAL_NAME VARCHAR(100),
  CONSTRAINT RAPORTOINTI_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);

CREATE UNIQUE INDEX RAPORTOINTI_SESSION_IX1 ON RAPORTOINTI_SESSION (SESSION_ID);
CREATE INDEX RAPORTOINTI_SESSION_IX2 ON RAPORTOINTI_SESSION (EXPIRY_TIME);
CREATE INDEX RAPORTOINTI_SESSION_IX3 ON RAPORTOINTI_SESSION (PRINCIPAL_NAME);

CREATE UNLOGGED TABLE RAPORTOINTI_SESSION_ATTRIBUTES (
  SESSION_PRIMARY_ID CHAR(36) NOT NULL,
  ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
  ATTRIBUTE_BYTES BYTEA NOT NULL,
  CONSTRAINT RAPORTOINTI_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
  CONSTRAINT RAPORTOINTI_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID) REFERENCES RAPORTOINTI_SESSION(PRIMARY_ID) ON DELETE CASCADE
);


CREATE TABLE liitteet (
  tunniste uuid PRIMARY KEY,
  nimi varchar NOT NULL,
  contenttype varchar NOT NULL,
  koko int NOT NULL,
  omistaja varchar NOT NULL,
  tila varchar NOT NULL,
  luotu timestamp NOT NULL
);
-- skannattavat liitteet pitää lukita aina kun päivitetään liitteiden tiloja joten ne pitää olla nopeasti haettavissa
CREATE INDEX liitteet_skannauksessa_idx ON liitteet (tunniste) WHERE tila<>'PUHDAS';

INSERT INTO liitteet VALUES('3fa85f64-5717-4562-b3fc-2c963f66afa6', 'screenshot.png', 'image/png', 0, '', 'PUHDAS', '2040-01-01 00:00:00.000000'::timestamp);

CREATE TYPE prioriteetti AS ENUM ('KORKEA', 'NORMAALI');

CREATE TABLE lahetykset (
  tunniste uuid PRIMARY KEY,
  otsikko varchar NOT NULL,
  lahettavapalvelu varchar(255) NOT NULL,
  lahettavanvirkailijanoid varchar(255),
  lahettajannimi varchar(255),
  lahettajansahkoposti varchar(255) NOT NULL,
  replyto varchar(255),
  prioriteetti prioriteetti NOT NULL,
  omistaja varchar NOT NULL,
  luotu timestamp NOT NULL,
  poistettava timestamp NOT NULL
);
INSERT INTO lahetykset VALUES('3fa85f64-5717-4562-b3fc-2c963f66afa6', 'Esimerkkiotsikko', 'Esimerkkipalvelu', '1.2.246.562.24.1', 'Lasse Lähettäjä', 'lasse.lahettaja@opintopolku.fi', null, 'NORMAALI', 'Esimerkkiomistaja', now(), '2040-01-01 00:00:00.000000'::timestamp);

CREATE TABLE viestit (
  tunniste uuid PRIMARY KEY,
  lahetys_tunniste uuid NOT NULL,
  otsikko varchar(255) NOT NULL,
  sisalto text NOT NULL,
  sisallontyyppi varchar(4) NOT NULL,
  kielet_fi boolean NOT NULL,
  kielet_sv boolean NOT NULL,
  kielet_en boolean NOT NULL,
  prioriteetti prioriteetti NOT NULL, -- denormalisoitu korkean prioriteetin ratelimitterin takia
  omistaja varchar NOT NULL,
  luotu timestamp NOT NULL,
  CONSTRAINT fk_lahetys_tunniste FOREIGN KEY (lahetys_tunniste) REFERENCES lahetykset(tunniste) ON DELETE CASCADE
);
-- lähetykseen kuuluvat viestit haetaan usein
CREATE INDEX viestit_lahetys_tunnisteet_idx ON viestit (lahetys_tunniste);
-- korkean prioriteetin viestien rate-limitteri hakee äskettäin luodut viestit per omistaja
CREATE INDEX viestit_korkea_omistaja_luotu_idx ON viestit (omistaja, luotu) WHERE prioriteetti='KORKEA';

CREATE TABLE maskit (
  viesti_tunniste UUID NOT NULL,
  salaisuus varchar NOT NULL,
  maski varchar,
  PRIMARY KEY (viesti_tunniste, salaisuus),
  CONSTRAINT fk_viesti_tunniste FOREIGN KEY (viesti_tunniste) REFERENCES viestit(tunniste) ON DELETE CASCADE
);

CREATE TABLE viestit_liitteet (
  viesti_tunniste UUID NOT NULL,
  liite_tunniste UUID NOT NULL,
  indeksi integer NOT NULL,
  PRIMARY KEY (viesti_tunniste, liite_tunniste),
  CONSTRAINT fk_viesti_tunniste FOREIGN KEY (viesti_tunniste) REFERENCES viestit(tunniste) ON DELETE CASCADE,
  CONSTRAINT fk_liite_tunniste FOREIGN KEY (liite_tunniste) REFERENCES liitteet(tunniste)
);
-- liitteistä pitää päästä viesteihin kun:
--  - merkitään skannauksen jälkeen vastaanottajia lähetyskelpoisiksi
--  - poistetaan liitteitä joiden viestit on poistettu
CREATE INDEX viestit_liitteet_liite_tunniste_idx ON viestit_liitteet (liite_tunniste);

CREATE TABLE kayttooikeudet (
  tunniste SERIAL PRIMARY KEY,
  organisaatio varchar,
  oikeus varchar
);
CREATE UNIQUE INDEX kayttooikeudet_idx ON kayttooikeudet (organisaatio, oikeus);

CREATE TABLE viestit_kayttooikeudet (
  viesti_tunniste uuid NOT NULL,
  kayttooikeus_tunniste int,
  PRIMARY KEY (viesti_tunniste, kayttooikeus_tunniste),
  CONSTRAINT fk_viesti_tunniste FOREIGN KEY (viesti_tunniste) REFERENCES viestit(tunniste) ON DELETE CASCADE,
  CONSTRAINT fk_kayttooikeus_tunniste FOREIGN KEY (kayttooikeus_tunniste) REFERENCES kayttooikeudet(tunniste)
);

CREATE TABLE lahetykset_kayttooikeudet (
  lahetys_tunniste uuid NOT NULL,
  kayttooikeus_tunniste int,
  PRIMARY KEY (lahetys_tunniste, kayttooikeus_tunniste),
  CONSTRAINT fk_lahetys_tunniste FOREIGN KEY (lahetys_tunniste) REFERENCES lahetykset(tunniste) ON DELETE CASCADE,
  CONSTRAINT fk_kayttooikeus_tunniste FOREIGN KEY (kayttooikeus_tunniste) REFERENCES kayttooikeudet(tunniste)
);

CREATE TABLE vastaanottajat (
  tunniste uuid PRIMARY KEY,
  viesti_tunniste uuid NOT NULL,
  nimi varchar,
  sahkopostiosoite varchar NOT NULL,
  tila varchar NOT NULL,
  luotu timestamp NOT NULL,
  prioriteetti prioriteetti NOT NULL, -- denormalisoitu lähetysjonon takia
  ses_tunniste varchar,
  CONSTRAINT fk_viesti_tunniste FOREIGN KEY (viesti_tunniste) REFERENCES viestit(tunniste) ON DELETE CASCADE
);
-- lähetysjono
CREATE INDEX vastaanottajat_jono_idx ON vastaanottajat (prioriteetti, luotu) WHERE tila='ODOTTAA';

-- viestin vastaanottajat haetaan usein
CREATE INDEX vastaanottajat_viesti_tunnisteet_idx ON vastaanottajat (viesti_tunniste);

-- tilaa päivitetään ses_tunnisteen perusteella
CREATE INDEX vastaanottajat_ses_tunnisteet_idx ON vastaanottajat (ses_tunniste);

CREATE TABLE vastaanottaja_siirtymat (
  vastaanottaja_tunniste uuid NOT NULL,
  aika timestamp NOT NULL,
  tila varchar NOT NULL,
  lisatiedot varchar,
  CONSTRAINT fk_vastaanottaja_tunniste FOREIGN KEY (vastaanottaja_tunniste) REFERENCES vastaanottajat(tunniste) ON DELETE CASCADE
);
-- vastaanottajan tilasiirtymät haetaan usein
CREATE INDEX vastaanottaja_siirtymat_vastaanottaja_tunnisteet_idx ON vastaanottaja_siirtymat (vastaanottaja_tunniste);

CREATE TABLE metadata_avaimet (
  avain varchar(64) PRIMARY KEY
);

CREATE TABLE metadata (
  avain varchar(64),
  arvo varchar(255),
  viesti_tunniste uuid,
  PRIMARY KEY (avain, arvo, viesti_tunniste),
  CONSTRAINT fk_viesti_tunniste FOREIGN KEY (viesti_tunniste) REFERENCES viestit(tunniste) ON DELETE CASCADE
);
-- viestin metadata haetaan usein
CREATE INDEX metadata_viesti_tunniste_idx ON metadata (viesti_tunniste);