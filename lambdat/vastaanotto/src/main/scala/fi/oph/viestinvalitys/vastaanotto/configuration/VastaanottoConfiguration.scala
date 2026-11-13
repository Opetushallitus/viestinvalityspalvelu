package fi.oph.viestinvalitys.vastaanotto.configuration

import tools.jackson.databind.{DeserializationFeature, SerializationFeature}
import tools.jackson.module.scala.DefaultScalaModule
import fi.oph.viestinvalitys.util.ConfigurationUtil
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.servers.Server
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.{Bean, Configuration, Profile}

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
  def jsonMapperBuilderCustomizer(): JsonMapperBuilderCustomizer =
    builder =>
      builder.addModule(DefaultScalaModule)
      builder.disable(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
      builder.enable(SerializationFeature.INDENT_OUTPUT)
}
