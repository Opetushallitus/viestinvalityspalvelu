CREATE TABLE liitteet (
  tunniste uuid PRIMARY KEY,
  nimi varchar NOT NULL,
  contenttype varchar NOT NULL,
  koko int NOT NULL,
  omistaja varchar NOT NULL,
  tila varchar NOT NULL
);
INSERT INTO liitteet VALUES('3fa85f64-5717-4562-b3fc-2c963f66afa6', 'esimerkkiliite', 'not defined', 0, '', 'PUHDAS');

CREATE TABLE lahetykset (
  tunniste uuid PRIMARY KEY,
  otsikko varchar NOT NULL,
  omistaja varchar NOT NULL
);
INSERT INTO lahetykset VALUES('3fa85f64-5717-4562-b3fc-2c963f66afa6', 'Esimerkkiotsikko', 'Esimerkkiomistaja');

CREATE TABLE viestit (
  tunniste uuid PRIMARY KEY,
  lahetys_tunniste uuid NOT NULL,
  otsikko varchar(255) NOT NULL,
  sisalto text NOT NULL,
  sisallontyyppi varchar(4) NOT NULL,
  kielet_fi boolean NOT NULL,
  kielet_sv boolean NOT NULL,
  kielet_en boolean NOT NULL,
  lahettavanvirkailijanoid varchar(255),
  lahettajannimi varchar(255) NOT NULL,
  lahettajansahkoposti varchar(255) NOT NULL,
  lahettavapalvelu varchar(255) NOT NULL,
  prioriteetti varchar(10) NOT NULL,
  CONSTRAINT fk_lahetys_tunniste FOREIGN KEY (lahetys_tunniste) REFERENCES lahetykset(tunniste)
);
CREATE INDEX viestit_lahetys_tunnisteet_idx ON viestit (lahetys_tunniste);

CREATE TABLE viestit_liitteet (
  viesti_tunniste UUID NOT NULL,
  liite_tunniste UUID NOT NULL,
  PRIMARY KEY (viesti_tunniste, liite_tunniste),
  CONSTRAINT fk_viesti_tunniste FOREIGN KEY (viesti_tunniste) REFERENCES viestit(tunniste),
  CONSTRAINT fk_liite_tunniste FOREIGN KEY (liite_tunniste) REFERENCES liitteet(tunniste)
);

CREATE TABLE vastaanottajat (
  tunniste uuid PRIMARY KEY,
  viesti_tunniste uuid NOT NULL,
  nimi varchar NOT NULL,
  sahkopostiosoite varchar NOT NULL,
  tila varchar NOT NULL,
  aikaisintaan timestamp NOT NULL,
  CONSTRAINT fk_viesti_tunniste FOREIGN KEY (viesti_tunniste) REFERENCES viestit(tunniste)
);
CREATE INDEX viestit_tilat_aikaisintaan_idx ON vastaanottajat (tila, aikaisintaan);
CREATE INDEX vastaanottajat_viesti_tunnisteet_idx ON vastaanottajat (viesti_tunniste);

CREATE TABLE metadata_avaimet (
  avain varchar(64) PRIMARY KEY
);

CREATE TABLE metadata (
  avain varchar(64),
  arvo varchar(255),
  viesti_tunniste uuid,
  PRIMARY KEY (avain, arvo, viesti_tunniste)
);
CREATE INDEX metadata_viesti_tunniste_idx ON metadata (viesti_tunniste);