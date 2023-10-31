package fi.oph.viestinvalitys.orkestraattori

import fi.oph.viestinvalitys.model.Viestipohjat
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.{Autowired, Value}
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.{Bean, Configuration}


@Configuration
class TallennusConfiguration {

/*
  @Bean
  def dataSource(
                  @Value("${postgres.host}") host: String,
                  @Value("${postgres.port}") port: String,
                  @Value("${postgres.username}") username: String,
                  @Value("${postgres.password}") password: String): PGSimpleDataSource = {
    val ds: PGSimpleDataSource = new PGSimpleDataSource()
    ds.setServerNames(host)
    ds.setDatabaseName("test")
    ds.setPortNumbers(port.toInt)
    ds.setUser(username)
    ds.setPassword(password)
    ds
  }
*/

}
