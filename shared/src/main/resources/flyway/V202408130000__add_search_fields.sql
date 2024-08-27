ALTER TABLE viestit ADD COLUMN IF NOT EXISTS haku_sisalto tsvector;
ALTER TABLE viestit ADD COLUMN IF NOT EXISTS haku_otsikko tsvector;
ALTER TABLE viestit ADD COLUMN IF NOT EXISTS haku_kayttooikeudet integer[];
ALTER TABLE viestit ADD COLUMN IF NOT EXISTS haku_vastaanottajat varchar[];
ALTER TABLE viestit ADD COLUMN IF NOT EXISTS haku_lahettaja varchar;
ALTER TABLE viestit ADD COLUMN IF NOT EXISTS haku_metadata varchar[];

ALTER TABLE lahetykset_kayttooikeudet ADD COLUMN IF NOT EXISTS luotu timestamp;