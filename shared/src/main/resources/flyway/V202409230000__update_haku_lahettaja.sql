UPDATE lahetykset SET lahettavanvirkailijanoid='1.2.246.562.24.1' WHERE tunniste='0181a38f-0883-7a0e-8155-83f5d9a3c226';
ALTER TABLE viestit ALTER COLUMN haku_lahettaja DROP NOT NULL;
UPDATE viestit SET haku_lahettaja=NULL;
UPDATE viestit SET haku_lahettaja=(SELECT lahettavanvirkailijanoid FROM lahetykset WHERE lahetykset.tunniste=lahetys_tunniste) WHERE haku_lahettaja IS NULL;
