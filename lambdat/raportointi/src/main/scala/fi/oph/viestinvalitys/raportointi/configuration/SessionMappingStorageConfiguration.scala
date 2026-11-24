package fi.oph.viestinvalitys.raportointi.configuration;

import fi.oph.viestinvalitys.security.JdbcSessionMappingStorage
import org.apereo.cas.client.session.SessionMappingStorage
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource
import org.springframework.session.{Session, SessionRepository}
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.{TransactionOperations, TransactionTemplate}

import javax.sql.DataSource

@Configuration
class SessionMappingStorageConfiguration {
    val LOG = LoggerFactory.getLogger(classOf[SessionMappingStorageConfiguration])

    @Bean(Array("springSessionTransactionOperations"))
    @ConditionalOnMissingBean(name = Array("springSessionTransactionOperations"))
    def springSessionTransactionOperations(@SpringSessionDataSource dataSource: DataSource): TransactionOperations = {
        val transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource))
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
        transactionTemplate
    }

    @Bean
    def sessionMappingStorage(sessionRepository: JdbcIndexedSessionRepository): SessionMappingStorage = {
        val jdbcSessionMappingStorage = new JdbcSessionMappingStorage(sessionRepository.asInstanceOf[SessionRepository[Session]], "raportointi");
        jdbcSessionMappingStorage
    }
}
