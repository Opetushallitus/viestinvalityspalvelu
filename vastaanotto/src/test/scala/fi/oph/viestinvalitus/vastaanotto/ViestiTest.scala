package fi.oph.viestinvalitus.vastaanotto

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
      sisallonTyyppi = "text",
      kielet = java.util.List.of("fi", "sv"),
      lahettavanVirkailijanOid = "testLahettajanOID",
      lahettaja = Lahettaja("testLahettajaNimi", "testLahettajaOsoite"),
      vastaanottajat = java.util.List.of(Vastaanottaja("testVastaanottajaNimi", "testVastaanOttajaOsoite")),
      liitteidenTunnisteet = java.util.List.of("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
      lahettavaPalvelu = "testLahettavaPalvelu",
      prioriteetti = "normaali",
      sailytysAika = 10,
      kayttooikeusRajoitukset = java.util.List.of("testKayttooikeusRajoitus"),
      metadata = java.util.Map.of("key", "value")
    )

    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    val stringRepresentation = mapper.writeValueAsString(viesti);

    val deserialized = mapper.readValue(stringRepresentation, classOf[Viesti])
    Assertions.assertEquals(viesti, deserialized);
  }
}
