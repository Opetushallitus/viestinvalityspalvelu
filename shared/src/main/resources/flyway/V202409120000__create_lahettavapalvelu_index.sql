-- lista lähettävistä palveluista muodostetaan toistaiseksi lähetyksien tiedoista, palvelu cachettaa
CREATE INDEX IF NOT EXISTS lahetykset_lahettavapalvelu_index on lahetykset (lahettavapalvelu asc);