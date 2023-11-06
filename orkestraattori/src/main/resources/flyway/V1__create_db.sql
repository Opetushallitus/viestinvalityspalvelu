CREATE TABLE liitteet (
  tunniste uuid PRIMARY KEY,
  nimi varchar NOT NULL,
  content_type varchar NOT NULL,
  koko int NOT NULL,
  omistaja varchar NOT NULL,
  tila varchar NOT NULL
);
CREATE INDEX liitteet_tilat_idx ON liitteet (tila);

CREATE TABLE lahetykset (
  tunniste uuid PRIMARY KEY,
  otsikko varchar NOT NULL,
  omistaja varchar NOT NULL
);

CREATE TABLE viestipohjat (
  tunniste uuid PRIMARY KEY,
  otsikko varchar NOT NULL
);

CREATE TABLE viestit (
  tunniste uuid PRIMARY KEY,
  viestipohja_tunniste uuid NOT NULL,
  sahkopostiosoite varchar NOT NULL,
  CONSTRAINT fk_viestipohja_tunniste FOREIGN KEY (viestipohja_tunniste) REFERENCES viestipohjat(tunniste)
);

