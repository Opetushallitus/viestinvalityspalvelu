package fi.oph.viestinvalitys.vastaanotto.configuration

import fi.oph.viestinvalitys.security.JdbcSessionMappingStorage
import org.apereo.cas.client.session.SessionMappingStorage
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.{Bean, Configuration}
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource
import org.springframework.session.{Session, SessionRepository}
import org.springframework.session.jdbc.{JdbcIndexedSessionRepository, PostgreSqlJdbcIndexedSessionRepositoryCustomizer}
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.{TransactionOperations, TransactionTemplate}
import org.springframework.jdbc.datasource.DataSourceTransactionManager

import javax.sql.DataSource

@Configuration
class SessionConfiguration {
  val LOG = LoggerFactory.getLogger(classOf[SessionConfiguration])

  @Bean(Array("springSessionTransactionOperations"))
  @ConditionalOnMissingBean(name = Array("springSessionTransactionOperations"))
  def springSessionTransactionOperations(@SpringSessionDataSource dataSource: DataSource): TransactionOperations = {
    val transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource))
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
    transactionTemplate
  }

  @Bean
  def sessionStoreCustomizer(): PostgreSqlJdbcIndexedSessionRepositoryCustomizer =
    new PostgreSqlJdbcIndexedSessionRepositoryCustomizer() {
      // Ei päivitetä sessiota loginin jälkeen. Kuormatestissä tämä mahdollistaa suunnilleen tuplanopeuden
      override def customize(sessionRepository: JdbcIndexedSessionRepository): Unit =
        sessionRepository.setUpdateSessionQuery("UPDATE %TABLE_NAME%\nSET SESSION_ID = ?, LAST_ACCESS_TIME = ?, MAX_INACTIVE_INTERVAL = ?, EXPIRY_TIME = ?, PRINCIPAL_NAME = ?\nWHERE PRIMARY_ID = ? AND PRINCIPAL_NAME IS NULL\n")
    }
  @Bean
  def vastaanottoSessionMappingStorage(sessionRepository: JdbcIndexedSessionRepository): SessionMappingStorage = {
    val jdbcSessionMappingStorage = new JdbcSessionMappingStorage(sessionRepository.asInstanceOf[SessionRepository[Session]], "lahetys");
    jdbcSessionMappingStorage
  }
}
