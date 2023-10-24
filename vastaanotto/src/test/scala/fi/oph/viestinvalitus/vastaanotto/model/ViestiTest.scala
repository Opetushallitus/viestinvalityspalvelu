package fi.oph.viestinvalitus.vastaanotto.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import fi.oph.viestinvalitus.vastaanotto.configuration.VastaanottoConfiguration
import fi.oph.viestinvalitus.vastaanotto.model.{Lahettaja, Vastaanottaja, Viesti}
import org.junit.jupiter.api.{Assertions, Test}

import java.util.{Optional, UUID}

@Test
class ViestiTest {

  def getExampleViesti(): Viesti =
    Viesti(
      otsikko = "testOtsikko",
      sisalto = "testSisalto",
      sisallonTyyppi = "text",
      kielet = java.util.List.of("fi", "sv"),
      lahettavanVirkailijanOid = java.util.Optional.of("testLahettajanOID"),
      lahettaja = Lahettaja("testLahettajaNimi", "testLahettajaOsoite"),
      vastaanottajat = java.util.List.of(Vastaanottaja("testVastaanottajaNimi", "testVastaanOttajaOsoite")),
      liitteidenTunnisteet = java.util.List.of("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
      lahettavaPalvelu = "testLahettavaPalvelu",
      lahetysTunniste = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      prioriteetti = "normaali",
      sailytysAika = 10,
      kayttooikeusRajoitukset = java.util.List.of("testKayttooikeusRajoitus"),
      metadata = java.util.Map.of("key", "value")
    )

  def getObjectMapper(): ObjectMapper =
    new VastaanottoConfiguration().objectMapper()

  @Test def testSerializationRoundtrip(): Unit =
    val mapper = getObjectMapper()

    val viesti = getExampleViesti()
    val json = mapper.writeValueAsString(viesti);
    val deserialized = mapper.readValue(json, classOf[Viesti])
    Assertions.assertEquals(viesti, deserialized);

  @Test def testIgnoreMissingProperties(): Unit =
    val mapper = getObjectMapper()
    val viesti = getExampleViesti().copy(lahettavanVirkailijanOid = Optional.empty)
    val json = mapper.writeValueAsString(viesti).replaceAll("\"lahettavanVirkailijanOid\"\\s*:\\s*null\\s*,\\n?", "") // lähettäjän OID voi olla määrittelemätön

    val deserialized = mapper.readValue(json, classOf[Viesti])
    Assertions.assertEquals(viesti, deserialized);

}
