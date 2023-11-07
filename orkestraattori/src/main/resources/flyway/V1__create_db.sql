CREATE TABLE liitteet (
  tunniste uuid PRIMARY KEY,
  nimi varchar NOT NULL,
  content_type varchar NOT NULL,
  koko int NOT NULL,
  omistaja varchar NOT NULL,
  tila varchar NOT NULL
);

CREATE TABLE lahetykset (
  tunniste uuid PRIMARY KEY,
  otsikko varchar NOT NULL,
  omistaja varchar NOT NULL
);
INSERT INTO lahetykset VALUES('3fa85f64-5717-4562-b3fc-2c963f66afa6', 'Esimerkkiotsikko', 'Esimerkkiomistaja');

CREATE TABLE viestipohjat (
  tunniste uuid PRIMARY KEY,
  otsikko varchar NOT NULL
);

CREATE TABLE viestit (
  tunniste uuid PRIMARY KEY,
  viestipohja_tunniste uuid NOT NULL,
  lahetys_tunniste uuid NOT NULL,
  sahkopostiosoite varchar NOT NULL,
  CONSTRAINT fk_viestipohja_tunniste FOREIGN KEY (viestipohja_tunniste) REFERENCES viestipohjat(tunniste),
  CONSTRAINT fk_lahetys_tunniste FOREIGN KEY (lahetys_tunniste) REFERENCES lahetykset(tunniste)
);
CREATE INDEX viestit_lahetys_tunnisteet_idx ON viestit (lahetys_tunniste);

