package fi.oph.viestinvalitys.vastaanotto.model

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import fi.oph.viestinvalitys.vastaanotto.configuration.VastaanottoConfiguration
import fi.oph.viestinvalitys.vastaanotto.model.{Lahettaja, Vastaanottaja, Viesti}
import org.junit.jupiter.api.{Assertions, Test}

import java.util.{Optional, UUID}

@Test
class ViestiTest {

  def getExampleViesti(): Viesti =
    Viesti(
      otsikko = Optional.of("testOtsikko"),
      sisalto = Optional.of("testSisalto"),
      sisallonTyyppi = Optional.of("text"),
      kielet = Optional.of(java.util.List.of("fi", "sv")),
      maskit = Optional.of(java.util.List.of(Maski(Optional.of("salainen linkki"), Optional.of("<salainen linkki>")))),
      lahettavanVirkailijanOid = Optional.of("testLahettajanOID"),
      lahettaja = Optional.of(Lahettaja(Optional.of("testLahettajaNimi"), Optional.of("testLahettajaOsoite"))),
      replyTo = Optional.of("ville.virkamies@oph.fi"),
      vastaanottajat = Optional.of(java.util.List.of(Vastaanottaja(Optional.of("testVastaanottajaNimi"), Optional.of("testVastaanOttajaOsoite")))),
      liitteidenTunnisteet = Optional.of(java.util.List.of("3fa85f64-5717-4562-b3fc-2c963f66afa6")),
      lahettavaPalvelu = Optional.of("testLahettavaPalvelu"),
      lahetysTunniste = Optional.of("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
      prioriteetti = Optional.of("normaali"),
      sailytysAika = Optional.of(10),
      kayttooikeusRajoitukset = Optional.of(java.util.List.of("testKayttooikeusRajoitus")),
      metadata = Optional.of(java.util.Map.of("key", java.util.List.of("value")))
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
