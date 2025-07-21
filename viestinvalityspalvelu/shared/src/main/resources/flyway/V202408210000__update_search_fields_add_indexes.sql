-- täytetään hakukentät
UPDATE viestit SET haku_sisalto=to_tsvector('simple', sisalto) WHERE haku_sisalto IS NULL;
UPDATE viestit SET haku_otsikko=to_tsvector('simple', otsikko) WHERE haku_otsikko IS NULL;
UPDATE viestit SET haku_kayttooikeudet=(SELECT array_agg(kayttooikeus_tunniste) FROM viestit_kayttooikeudet WHERE tunniste=viesti_tunniste) WHERE haku_kayttooikeudet IS NULL;
UPDATE viestit SET haku_kayttooikeudet=array[]::integer[] WHERE haku_kayttooikeudet IS NULL;
UPDATE viestit SET haku_vastaanottajat=(SELECT array_agg(sahkopostiosoite) FROM vastaanottajat WHERE viestit.tunniste=viesti_tunniste) WHERE haku_vastaanottajat IS NULL;
UPDATE viestit SET haku_lahettaja=(SELECT lahettavanvirkailijanoid FROM lahetykset WHERE lahetykset.tunniste=lahetys_tunniste) WHERE haku_lahettaja IS NULL;
UPDATE viestit SET haku_metadata=(SELECT array_agg(avain || ':' || arvo) FROM metadata WHERE tunniste=viesti_tunniste) WHERE haku_metadata IS NULL;
UPDATE viestit SET haku_metadata='{}'::varchar[] WHERE haku_metadata IS NULL;
UPDATE viestit SET haku_lahettavapalvelu=(SELECT lahettavapalvelu FROM lahetykset WHERE lahetykset.tunniste=lahetys_tunniste) WHERE haku_lahettavapalvelu IS NULL;
UPDATE viestit SET haku_organisaatiot=(SELECT array_agg(organisaatio)
  FROM (SELECT DISTINCT kayttooikeudet.organisaatio
        FROM viestit JOIN viestit_kayttooikeudet ON viestit.tunniste=viestit_kayttooikeudet.viesti_tunniste
        JOIN kayttooikeudet ON viestit_kayttooikeudet.kayttooikeus_tunniste=kayttooikeudet.tunniste) AS data)
        WHERE haku_organisaatiot IS NULL;

-- asetetaan rajoitteet kun kentät on täytetty
ALTER TABLE viestit ALTER COLUMN haku_sisalto SET NOT NULL;
ALTER TABLE viestit ALTER COLUMN haku_otsikko SET NOT NULL;
ALTER TABLE viestit ALTER COLUMN haku_kayttooikeudet SET NOT NULL;
ALTER TABLE viestit ALTER COLUMN haku_vastaanottajat SET NOT NULL;
ALTER TABLE viestit ALTER COLUMN haku_metadata SET NOT NULL;
ALTER TABLE viestit ALTER COLUMN haku_lahettavapalvelu SET NOT NULL;
ALTER TABLE viestit ALTER COLUMN haku_organisaatiot SET NOT NULL;

CREATE EXTENSION IF NOT EXISTS btree_gin;
CREATE INDEX IF NOT EXISTS viestit_haku_idx ON viestit
    USING GIN (haku_kayttooikeudet, haku_otsikko, haku_sisalto, haku_vastaanottajat, haku_lahettaja, haku_metadata,
    haku_lahettavapalvelu, lahetys_tunniste, haku_organisaatiot);