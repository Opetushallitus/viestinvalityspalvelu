package fi.oph.viestinvalitys.raportointi

import fi.oph.viestinvalitys.business.Kayttooikeus
import fi.oph.viestinvalitys.raportointi.integration.OrganisaatioClient
import fi.oph.viestinvalitys.raportointi.resource.LahetysResource
import org.junit.jupiter.api.{Assertions, Test, TestInstance}
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock

import java.util.Optional

@TestInstance(Lifecycle.PER_CLASS)
class LahetysResourceTest {

  val PARENT_ORGANISAATIO = "1.2.246.562.10.240484683010"
  val CHILD_ORGANISAATIO = "1.2.246.562.10.2014041814455745619200"
  val ORGANISAATIO2 = "1.2.246.562.10.79559059674"
  val OIKEUS = "OIKEUS1"
  val mockOrganisaatioClient = mock[OrganisaatioClient]

  @Test def testLapsiOrganisaatiotSisaltyvatRajaukseen(): Unit =
    when(mockOrganisaatioClient.getAllChildOidsFlat(PARENT_ORGANISAATIO)).thenReturn(Set(CHILD_ORGANISAATIO))
    val lahetysResource: LahetysResource = new LahetysResource()
    Assertions.assertEquals(Set(Kayttooikeus(OIKEUS, Option.apply(PARENT_ORGANISAATIO)), Kayttooikeus(OIKEUS, Option.apply(CHILD_ORGANISAATIO))),
      lahetysResource.organisaatiorajaus(Optional.of(PARENT_ORGANISAATIO), Set(Kayttooikeus(OIKEUS, Option.apply(PARENT_ORGANISAATIO)), Kayttooikeus(OIKEUS, Option.apply(CHILD_ORGANISAATIO))), mockOrganisaatioClient))

  @Test def testMuuOrganisaatioKarsitaan(): Unit =
    when(mockOrganisaatioClient.getAllChildOidsFlat(PARENT_ORGANISAATIO)).thenReturn(Set(CHILD_ORGANISAATIO))
    val lahetysResource: LahetysResource = new LahetysResource()
    Assertions.assertEquals(Set(Kayttooikeus(OIKEUS, Option.apply(PARENT_ORGANISAATIO)), Kayttooikeus(OIKEUS, Option.apply(CHILD_ORGANISAATIO))),
      lahetysResource.organisaatiorajaus(Optional.of(PARENT_ORGANISAATIO), Set(Kayttooikeus(OIKEUS, Option.apply(PARENT_ORGANISAATIO)), Kayttooikeus(OIKEUS, Option.apply(ORGANISAATIO2)), Kayttooikeus(OIKEUS, Option.apply(CHILD_ORGANISAATIO))), mockOrganisaatioClient))

  @Test def testParentOrganisaatioKarsitaan(): Unit =
    when(mockOrganisaatioClient.getAllChildOidsFlat(CHILD_ORGANISAATIO)).thenReturn(Set.empty)
    val lahetysResource: LahetysResource = new LahetysResource()
    Assertions.assertEquals(Set(Kayttooikeus(OIKEUS, Option.apply(CHILD_ORGANISAATIO))),
      lahetysResource.organisaatiorajaus(Optional.of(CHILD_ORGANISAATIO), Set(Kayttooikeus(OIKEUS, Option.apply(PARENT_ORGANISAATIO)), Kayttooikeus(OIKEUS, Option.apply(ORGANISAATIO2)), Kayttooikeus(OIKEUS, Option.apply(CHILD_ORGANISAATIO))), mockOrganisaatioClient))
}
