package fi.oph.viestinvalitys

import org.springframework.context.annotation.Bean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.{TransactionOperations, TransactionTemplate}
import javax.sql.DataSource

@TestConfiguration
class SpringSessionTransactionConfiguration {

  @Bean(Array("springSessionTransactionOperations"))
  @ConditionalOnMissingBean(name = Array("springSessionTransactionOperations"))
  def springSessionTransactionOperations(
      @SpringSessionDataSource dataSource: DataSource
  ): TransactionOperations = {
    val transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource))
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
    transactionTemplate
  }
}
