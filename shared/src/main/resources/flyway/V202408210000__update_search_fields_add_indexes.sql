-- lisätään tekstihaun vaatimat kentät viesteihin
UPDATE viestit SET tekstihaku_sisalto=to_tsvector('finnish', sisalto) || to_tsvector('swedish', sisalto) || to_tsvector('english', sisalto) || to_tsvector('simple', sisalto) WHERE tekstihaku_sisalto IS NULL;
UPDATE viestit SET tekstihaku_otsikko=to_tsvector('finnish', otsikko) || to_tsvector('swedish', otsikko) || to_tsvector('english', otsikko) || to_tsvector('simple', otsikko) WHERE tekstihaku_otsikko IS NULL;
UPDATE viestit SET vastaanottajat=to_tsvector('simple', array_to_string((SELECT array_agg(sahkopostiosoite) FROM vastaanottajat WHERE viestit.tunniste=viesti_tunniste), ' ')) WHERE vastaanottajat IS NULL;
UPDATE viestit SET lahettaja=to_tsvector('simple', (SELECT lahettajannimi FROM lahetykset WHERE lahetykset.tunniste=lahetys_tunniste)) || to_tsvector('simple', (SELECT lahettajansahkoposti FROM lahetykset WHERE lahetykset.tunniste=lahetys_tunniste)) WHERE lahettaja IS NULL;
UPDATE viestit SET kayttooikeudet=(SELECT array_agg(kayttooikeus_tunniste) FROM viestit_kayttooikeudet WHERE tunniste=viesti_tunniste) WHERE kayttooikeudet IS NULL;

UPDATE lahetykset_kayttooikeudet SET luotu=(SELECT luotu FROM lahetykset WHERE tunniste=lahetys_tunniste) WHERE luotu IS NULL;

ALTER TABLE viestit ALTER COLUMN tekstihaku_sisalto SET NOT NULL;
ALTER TABLE viestit ALTER COLUMN tekstihaku_otsikko SET NOT NULL;
ALTER TABLE viestit ALTER COLUMN vastaanottajat SET NOT NULL;
ALTER TABLE viestit ALTER COLUMN kayttooikeudet SET NOT NULL;
ALTER TABLE viestit ALTER COLUMN lahettaja SET NOT NULL;

CREATE INDEX IF NOT EXISTS viestit_oikeudet_otsikko_sisalto_vastaanottajat_lahettaja_idx ON viestit USING GIN (kayttooikeudet, tekstihaku_otsikko, tekstihaku_sisalto, vastaanottajat, lahettaja);
CREATE INDEX IF NOT EXISTS lahetykset_kayttooikeudet_oikeus_luotu_idx ON lahetykset_kayttooikeudet(kayttooikeus_tunniste, luotu, lahetys_tunniste);

