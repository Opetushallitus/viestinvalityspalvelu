package fi.oph.viestinvalitys.vastaanotto.configuration

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import fi.oph.viestinvalitys.util.ConfigurationUtil
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.{Bean, Configuration, Primary, Profile}

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
    server.setUrl(s"https://viestinvalitys.${ConfigurationUtil.opintopolkuDomain}")
    new OpenAPI().servers(util.List.of(server))
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
