ALTER TABLE viestit ADD COLUMN IF NOT EXISTS tekstihaku_sisalto tsvector;
ALTER TABLE viestit ADD COLUMN IF NOT EXISTS tekstihaku_otsikko tsvector;
ALTER TABLE viestit ADD COLUMN IF NOT EXISTS kayttooikeudet integer[];
ALTER TABLE viestit ADD COLUMN IF NOT EXISTS vastaanottajat tsvector;
ALTER TABLE viestit ADD COLUMN IF NOT EXISTS lahettaja tsvector;

ALTER TABLE lahetykset_kayttooikeudet ADD COLUMN IF NOT EXISTS luotu timestamp;