package fi.oph.viestinvalitus.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.junit.jupiter.api.{Assertions, Test}

import java.util.UUID

@Test
class ViestiTest {

  @Test def testSerialization(): Unit = {

    val viesti = Viesti(
      otsikko = "testOtsikko",
      sisalto = "testSisalto",
      sisallonTyyppi = SisallonTyyppi.text,
      kielet = java.util.List.of(Kieli.fi, Kieli.sv),
      lahettavanVirkailijanOid = "testLahettajanOID",
      lahettaja = Lahettaja("testLahettajaNimi", "testLahettajaOsoite"),
      vastaanottajat = Seq(Vastaanottaja("testVastaanottajaNimi", "testVastaanOttajaOsoite")),
      liitteet = Seq(UUID.randomUUID()),
      lahettavaPalvelu = "testLahettavaPalvelu",
      prioriteetti = Prioriteetti.normaali,
      sailytysAika = 10,
      kayttooikeusRajoitukset = Seq("testKayttooikeusRajoitus"),
      metadata = Map("key" -> "value")
    )

    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    val stringRepresentation = mapper.writeValueAsString(viesti);

    val deserialized = mapper.readValue(stringRepresentation, classOf[Viesti])
    Assertions.assertEquals(viesti, deserialized);
  }
}
