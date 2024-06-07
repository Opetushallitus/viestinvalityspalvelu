package security

import com.github.f4b6a3.uuid.UuidCreator
import fi.oph.viestinvalitys.business.{Liite, LiitteenTila}
import fi.vm.sade.auditlog.Changes
import org.junit.jupiter.api.{Assertions, Test, TestInstance}
import org.junit.jupiter.api.TestInstance.Lifecycle

import java.util.UUID

@TestInstance(Lifecycle.PER_CLASS)
class AuditLoggerTest {

  @Test def testMuodostaLiiteChanges(): Unit =
    val uuid = UuidCreator.getTimeOrderedEpoch()
    val actual = Changes.addedDto(Liite(uuid, "tiedostonimi.txt", "text/plain", 123, "1.2.3.4.2", LiitteenTila.SKANNAUS))
    val expected = new Changes.Builder()
      .added("tunniste", uuid.toString)
      .added("nimi", "tiedostonimi.txt")
      .added("contentType", "text/plain")
      .added("koko", "123")
      .added("omistaja", "1.2.3.4.2")
      .added("tila", LiitteenTila.SKANNAUS.toString)
      .build()
    // TODO assertio että lokittuu vastaavasti
}
