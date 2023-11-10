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

CREATE TABLE viestiryhmat (
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
  prioriteetti varchar(10) NOT NULL
);

CREATE TABLE viestiryhmat_liitteet (
  viestiryhma_tunniste UUID NOT NULL,
  liite_tunniste UUID NOT NULL,
  PRIMARY KEY (viestiryhma_tunniste, liite_tunniste),
  CONSTRAINT fk_viestiryhma_tunniste FOREIGN KEY (viestiryhma_tunniste) REFERENCES viestiryhmat(tunniste),
  CONSTRAINT fk_liite_tunniste FOREIGN KEY (liite_tunniste) REFERENCES liitteet(tunniste)
);

CREATE TABLE viestit (
  tunniste uuid PRIMARY KEY,
  viestiryhma_tunniste uuid NOT NULL,
  nimi varchar NOT NULL,
  sahkopostiosoite varchar NOT NULL,
  tila varchar NOT NULL,
  CONSTRAINT fk_viestiryhma_tunniste FOREIGN KEY (viestiryhma_tunniste) REFERENCES viestiryhmat(tunniste),
  CONSTRAINT fk_lahetys_tunniste FOREIGN KEY (lahetys_tunniste) REFERENCES lahetykset(tunniste)
);
CREATE INDEX viestit_lahetys_tunnisteet_idx ON viestit (lahetys_tunniste);
CREATE INDEX viestit_tilat_idx ON viestit (tila);

CREATE TABLE metadata_avaimet (
  avain varchar(64) PRIMARY KEY
);

CREATE TABLE metadata (
  avain varchar(64),
  arvo varchar(255),
  viestiryhma_tunniste uuid,
  PRIMARY KEY (avain, arvo, viestiryhma_tunniste)
);
CREATE INDEX metadata_viesti_tunniste_idx ON metadata (viestiryhma_tunniste);