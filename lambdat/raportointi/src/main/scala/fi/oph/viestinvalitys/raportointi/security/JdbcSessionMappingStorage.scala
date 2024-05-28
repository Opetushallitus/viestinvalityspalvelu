package fi.oph.viestinvalitys.raportointi.security;

import fi.oph.viestinvalitys.business.KantaOperaatiot
import fi.oph.viestinvalitys.util.DbUtil
import org.springframework.session.Session
import org.springframework.session.SessionRepository
import jakarta.servlet.http.HttpSession
import org.slf4j.LoggerFactory

class JdbcSessionMappingStorage(sessionRepository: SessionRepository[Session]) extends OphSessionMappingStorage {

    val LOG = LoggerFactory.getLogger(classOf[JdbcSessionMappingStorage])
    @Override
    def removeSessionByMappingId(mappingId: String): HttpSession = {
        LOG.info(s"removeSessionByMappingId $mappingId")
        val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
        val sessionByMapping = kantaOperaatiot.getSessionIdByMappingId(mappingId)
          .map(s => sessionRepository.findById(s))
        val httpSession = sessionByMapping match
            case Some(s) => new HttpSessionAdapter(sessionRepository, s)
            case _ => null
        httpSession
    }

    @Override
    def removeBySessionById(sessionId: String) = {
        LOG.info(s"removeBySessionById $sessionId")
        val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
        kantaOperaatiot.deleteCasMappingBySessionId(sessionId)
    }

    @Override
    def addSessionById(mappingId: String, session: HttpSession) = {
        LOG.info(s"addSessionById $mappingId")
        val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
        kantaOperaatiot.addMappingForSessionId(mappingId, session.getId)
    }

    @Override
    def clean() = {
        val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
        kantaOperaatiot.cleanSessionMappings()
    }

}
