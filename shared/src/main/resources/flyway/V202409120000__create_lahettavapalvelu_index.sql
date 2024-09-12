-- lista lähettävistä palveluista muodostetaan toistaiseksi lähetyksien tiedoista, palvelu cachettaa
create index lahetykset_lahettavapalvelu_index on lahetykset (lahettavapalvelu asc);