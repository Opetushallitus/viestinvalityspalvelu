package fi.oph.viestinvalitys.raportointi.configuration;

import fi.oph.viestinvalitys.raportointi.security.JdbcSessionMappingStorage
import org.apereo.cas.client.session.SessionMappingStorage
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.session.{Session, SessionRepository}
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

@Configuration
class SessionMappingStorageConfiguration {
    @Bean
    def sessionMappingStorage(sessionRepository: JdbcIndexedSessionRepository): SessionMappingStorage = {
        val jdbcSessionMappingStorage = new JdbcSessionMappingStorage(sessionRepository.asInstanceOf[SessionRepository[Session]]);
        jdbcSessionMappingStorage
    }
}
