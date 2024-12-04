package fi.oph.viestinvalitys.vastaanotto.model

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.junit.jupiter.api.{Assertions, Test}

import java.util.Optional

@Test
class ViestiTest {

  def getExampleViesti(): ViestiImpl =
    ViestiImpl(
      otsikko = Optional.of("testOtsikko"),
      sisalto = Optional.of("testSisalto"),
      sisallonTyyppi = Optional.of("text"),
      kielet = Optional.of(java.util.List.of("fi", "sv")),
      maskit = Optional.of(java.util.List.of(MaskiImpl(Optional.of("salainen linkki"), Optional.of("<salainen linkki>")))),
      lahettavanVirkailijanOid = Optional.of("testLahettajanOID"),
      lahettaja = Optional.of(LahettajaImpl(Optional.of("testLahettajaNimi"), Optional.of("testLahettajaOsoite"))),
      replyTo = Optional.of("ville.virkamies@oph.fi"),
      vastaanottajat = Optional.of(java.util.List.of(VastaanottajaImpl(Optional.of("testVastaanottajaNimi"), Optional.of("testVastaanOttajaOsoite")))),
      liitteidenTunnisteet = Optional.of(java.util.List.of("3fa85f64-5717-4562-b3fc-2c963f66afa6")),
      lahettavaPalvelu = Optional.of("testLahettavaPalvelu"),
      lahetysTunniste = Optional.of("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
      prioriteetti = Optional.of(LahetysImpl.LAHETYS_PRIORITEETTI_NORMAALI),
      sailytysaika = Optional.of(10),
      kayttooikeusRajoitukset = Optional.of(java.util.List.of(KayttooikeusImpl(Optional.of("1.2.246.562.10.00000000000000006666"), Optional.of("oikeus")))),
      metadata = Optional.of(java.util.Map.of("key", java.util.List.of("value"))),
      idempotencyKey = Optional.of("avain")
    )

  def getObjectMapper(): ObjectMapper = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    mapper.registerModule(new Jdk8Module()) // tämä on java.util.Optional -kenttiä varten
    mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true)
    mapper
  }

  @Test def testSerializationRoundtrip(): Unit =
    val mapper = getObjectMapper()

    val viesti = getExampleViesti()
    val json = mapper.writeValueAsString(viesti);
    val deserialized = mapper.readValue(json, classOf[ViestiImpl])
    Assertions.assertEquals(viesti, deserialized);

  @Test def testIgnoreMissingProperties(): Unit =
    val mapper = getObjectMapper()
    val viesti = getExampleViesti().copy(lahettavanVirkailijanOid = Optional.empty)
    val json = mapper.writeValueAsString(viesti).replaceAll("\"lahettavanVirkailijanOid\"\\s*:\\s*null\\s*,\\n?", "") // lähettäjän OID voi olla määrittelemätön

    val deserialized = mapper.readValue(json, classOf[ViestiImpl])
    Assertions.assertEquals(viesti, deserialized);
}
