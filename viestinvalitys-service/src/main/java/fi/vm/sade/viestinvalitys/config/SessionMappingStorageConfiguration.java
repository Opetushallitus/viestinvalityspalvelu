package fi.vm.sade.viestinvalitys.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;

/**
 * Exposes the CAS single-logout {@link OphSessionMappingStorage} as a bean so it can be injected
 * into {@code SecurityConfig}'s SingleSignOutFilter. Mirrors the equivalent configuration in
 * oppijanumerorekisteri-service / kayttooikeus-service.
 */
@Configuration
public class SessionMappingStorageConfiguration {

    @Bean
    OphSessionMappingStorage sessionMappingStorage(
            JdbcTemplate jdbcTemplate, JdbcIndexedSessionRepository sessionRepository) {
        return new JdbcSessionMappingStorage(jdbcTemplate, sessionRepository);
    }
}
