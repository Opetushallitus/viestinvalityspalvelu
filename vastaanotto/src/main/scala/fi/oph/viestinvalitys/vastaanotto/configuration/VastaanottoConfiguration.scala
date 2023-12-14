package fi.oph.viestinvalitys.vastaanotto.configuration

import com.fasterxml.jackson.annotation.{JsonInclude, JsonSetter, Nulls}
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.{DeserializationFeature, MapperFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.servers.Server
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.{Bean, Configuration, Primary, Profile}
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.{Jackson2ObjectMapperBuilder, MappingJackson2HttpMessageConverter}

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
    server.setUrl("https://viestinvalitys.hahtuvaopintopolku.fi")
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

  @Bean
  @Primary
  def objectMapper(): ObjectMapper = {
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    mapper.registerModule(new Jdk8Module()) // tämä on java.util.Optional -kenttiä varten
    mapper.configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    mapper.configure(SerializationFeature.INDENT_OUTPUT, true)
    mapper
  }
}
