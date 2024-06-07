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
    val uuid = UUID.fromString("018ff18f-360b-739e-b1cf-ccca0c17d0f6")
    val actual = Changes.addedDto(Liite(uuid, "tiedostonimi.txt", "text/plain", 123, "1.2.3.4.2", LiitteenTila.SKANNAUS))
    val expected = "[{\"newValue\":\"{\\\"tunniste\\\":\\\"018ff18f-360b-739e-b1cf-ccca0c17d0f6\\\",\\\"nimi\\\":\\\"tiedostonimi.txt\\\",\\\"contentType\\\":\\\"text/plain\\\",\\\"koko\\\":123,\\\"omistaja\\\":\\\"1.2.3.4.2\\\",\\\"tila\\\":{\\\"_$ordinal$4\\\":0,\\\"$name$4\\\":\\\"SKANNAUS\\\"}}\"}]"
    Assertions.assertEquals(expected, actual.asJsonArray().toString)
}
