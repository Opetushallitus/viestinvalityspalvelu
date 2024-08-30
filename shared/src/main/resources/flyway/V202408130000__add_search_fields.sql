-- lisätään haun vaatimat denormalisoidut kentät viesteihin
ALTER TABLE viestit ADD COLUMN IF NOT EXISTS haku_sisalto tsvector;
ALTER TABLE viestit ADD COLUMN IF NOT EXISTS haku_otsikko tsvector;
ALTER TABLE viestit ADD COLUMN IF NOT EXISTS haku_kayttooikeudet integer[];
ALTER TABLE viestit ADD COLUMN IF NOT EXISTS haku_vastaanottajat varchar[];
ALTER TABLE viestit ADD COLUMN IF NOT EXISTS haku_lahettaja varchar;
ALTER TABLE viestit ADD COLUMN IF NOT EXISTS haku_metadata varchar[];
ALTER TABLE viestit ADD COLUMN IF NOT EXISTS haku_lahettavapalvelu varchar;

UPDATE lahetykset SET tunniste='0181a38f-0883-7a0e-8155-83f5d9a3c226' WHERE tunniste='3fa85f64-5717-4562-b3fc-2c963f66afa6';