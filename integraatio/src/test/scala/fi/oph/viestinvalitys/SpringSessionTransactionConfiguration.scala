package fi.oph.viestinvalitys

import org.springframework.context.annotation.{Bean, Configuration}
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.{TransactionOperations, TransactionTemplate}
import javax.sql.DataSource

@Configuration
class SpringSessionTransactionConfiguration {

  @Bean(Array("springSessionTransactionOperations"))
  def springSessionTransactionOperations(
      @SpringSessionDataSource dataSource: DataSource
  ): TransactionOperations = {
    val transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource))
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
    transactionTemplate
  }
}
