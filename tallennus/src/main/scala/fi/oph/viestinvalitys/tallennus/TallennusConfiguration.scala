package fi.oph.viestinvalitys.tallennus

import org.postgresql.ds.PGSimpleDataSource
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.{Bean, Configuration}

@Configuration
class TallennusConfiguration {

  @Bean
  def dataSource(
                  @Value("${postgres.host}") host: String,
                  @Value("${postgres.port}") port: String,
                  @Value("${postgres.username}") username: String,
                  @Value("${postgres.password}") password: String): PGSimpleDataSource = {
    val ds: PGSimpleDataSource = new PGSimpleDataSource()
    ds.setServerName(host)
    ds.setDatabaseName("test")
    ds.setPortNumber(port.toInt)
    ds.setUser(username)
    ds.setPassword(password)
    ds
  }

}
