package fi.oph.viestinvalitus.vastaanotto.configuration

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.{Bean, Configuration, Profile}
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory

import java.util
import java.util.List

@Configuration
class VastaanottoConfiguration {

  /**
   * Päivitetään serverin osoite jotta swagger-ui:sta tehdyt kutsut menevät oikeaan paikkaan
   */
  @Bean
  @Profile(Array("default")) def customOpenAPI: OpenAPI = {
    val server = new Server
    server.setUrl("https://viestinvalitus.hahtuvaopintopolku.fi")
    new OpenAPI().servers(util.List.of(server))
  }

  /**
   * Käytetään Jedistä Lettucen sijaan koska yhteyden saaminen ylös näyttää olevan huomattavasti nopeampaa
   */
  @Bean
  @Profile(Array("default")) def redisConnectionFactory: JedisConnectionFactory = {
    val config = new RedisStandaloneConfiguration
    config.setHostName(System.getenv("spring_redis_host"))
    config.setPort(System.getenv("spring_redis_port").toInt)
    val connectionFactory = new JedisConnectionFactory(config)
    connectionFactory.setUsePool(false)
    connectionFactory
  }


}
