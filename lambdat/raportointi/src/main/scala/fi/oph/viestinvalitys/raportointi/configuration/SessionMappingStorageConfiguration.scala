package fi.oph.viestinvalitys.raportointi.configuration;

import fi.oph.viestinvalitys.raportointi.security.JdbcSessionMappingStorage
import org.apereo.cas.client.session.SessionMappingStorage
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.session.{Session, SessionRepository}
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

@Configuration
class SessionMappingStorageConfiguration {
    val LOG = LoggerFactory.getLogger(classOf[SessionMappingStorageConfiguration])
    @Bean
    def sessionMappingStorage(sessionRepository: JdbcIndexedSessionRepository): SessionMappingStorage = {
        val jdbcSessionMappingStorage = new JdbcSessionMappingStorage(sessionRepository.asInstanceOf[SessionRepository[Session]]);
        jdbcSessionMappingStorage
    }
}
