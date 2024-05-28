package fi.oph.viestinvalitys.raportointi.security;

import fi.oph.viestinvalitys.business.KantaOperaatiot
import fi.oph.viestinvalitys.util.DbUtil
import org.apereo.cas.client.session.SessionMappingStorage
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.SingleColumnRowMapper
import org.springframework.session.Session
import org.springframework.session.SessionRepository
import jakarta.servlet.ServletContext
import jakarta.servlet.http.HttpSession

import java.time.Duration
import java.util.Collections
import java.util.Enumeration
import java.util.List
import java.util.Objects
import java.util.Optional;

class JdbcSessionMappingStorage(sessionRepository: SessionRepository[Session]) extends OphSessionMappingStorage {

    @Override
    def removeSessionByMappingId(mappingId: String): HttpSession = {
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
        val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
        kantaOperaatiot.deleteCasMappingBySessionId(sessionId)
    }

    @Override
    def addSessionById(mappingId: String, session: HttpSession) = {
        val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
        kantaOperaatiot.addMappingForSessionId(mappingId, session.getId)
    }

    @Override
    def clean() = {
        val kantaOperaatiot = new KantaOperaatiot(DbUtil.database)
        kantaOperaatiot.cleanSessionMappings()
    }

}
