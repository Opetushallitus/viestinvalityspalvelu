package fi.oph.viestinvalitys.vastaanotto.configuration

import fi.oph.viestinvalitys.vastaanotto.App
import fi.oph.viestinvalitys.vastaanotto.resource.APIConstants
import fi.oph.viestinvalitys.vastaanotto.security.{SecurityConstants, SecurityOperaatiot}
import fi.vm.sade.java_utils.security.OpintopolkuCasAuthenticationFilter
import fi.vm.sade.javautils.kayttooikeusclient.OphUserDetailsServiceImpl
import jakarta.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.apereo.cas.client.validation.{Cas20ProxyTicketValidator, TicketValidator}
import org.springframework.context.annotation.{Bean, Configuration, Profile}
import org.springframework.core.annotation.Order
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.http.{HttpMethod, HttpStatus}
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.{User, UserDetails, UserDetailsService}
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.csrf.{CsrfToken, CsrfTokenRequestHandler}
import org.springframework.security.web.util.matcher.RegexRequestMatcher
import org.springframework.session.web.http.{CookieSerializer, DefaultCookieSerializer}
import org.springframework.core.env.Environment
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.cas.ServiceProperties
import org.springframework.security.cas.authentication.CasAuthenticationProvider
import org.springframework.security.cas.web.{CasAuthenticationEntryPoint, CasAuthenticationFilter}
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder

import java.util.function.Supplier
import scala.jdk.CollectionConverters.*

@Configuration
@Order(2)
@EnableWebSecurity
@Profile(Array("default"))
class SecurityConfiguration {

  @Bean
  def serviceProperties(@Value("${cas-service.service}") service: String, @Value("${cas-service.sendRenew}") sendRenew: Boolean): ServiceProperties = {
    val serviceProperties = new ServiceProperties()
    serviceProperties.setService(service + "/j_spring_cas_security_check")
    serviceProperties.setSendRenew(sendRenew)
    serviceProperties.setAuthenticateAllArtifacts(true)
    serviceProperties
  }

  //
  // CAS authentication provider (authentication manager)
  //
  @Bean
  def casAuthenticationProvider(serviceProperties: ServiceProperties, ticketValidator: TicketValidator, environment: Environment, @Value("${cas-service.key}") key: String): CasAuthenticationProvider = {
    val host = environment.getProperty("host.alb", environment.getRequiredProperty("host.virkailija"))
    val casAuthenticationProvider = new CasAuthenticationProvider()
    casAuthenticationProvider.setUserDetailsService(new OphUserDetailsServiceImpl(host, App.CALLER_ID))
    casAuthenticationProvider.setServiceProperties(serviceProperties)
    casAuthenticationProvider.setTicketValidator(ticketValidator)
    casAuthenticationProvider.setKey(key)
    casAuthenticationProvider
  }

  @Bean
  def ticketValidator(environment: Environment): TicketValidator = {
    val ticketValidator = new Cas20ProxyTicketValidator(environment.getRequiredProperty("web.url.cas"))
    ticketValidator.setAcceptAnyProxy(true)
    ticketValidator
  }

  //
  // CAS filter
  //
  @Bean
  def casAuthenticationFilter(authenticationManager: AuthenticationManager, serviceProperties: ServiceProperties): CasAuthenticationFilter = {
    val casAuthenticationFilter = new OpintopolkuCasAuthenticationFilter(serviceProperties)
    casAuthenticationFilter.setAuthenticationManager(authenticationManager)
    casAuthenticationFilter.setFilterProcessesUrl("/j_spring_cas_security_check")
    casAuthenticationFilter
  }

  //
  // CAS entry point
  //
  @Bean def casAuthenticationEntryPoint(environment: Environment, serviceProperties: ServiceProperties): CasAuthenticationEntryPoint = {
    val casAuthenticationEntryPoint = new CasAuthenticationEntryPoint()
    casAuthenticationEntryPoint.setLoginUrl(environment.getRequiredProperty("web.url.cas") + "/login")
    casAuthenticationEntryPoint.setServiceProperties(serviceProperties)
    casAuthenticationEntryPoint
  }

  @Bean
  def authenticationManager(http: HttpSecurity, casAuthenticationProvider: CasAuthenticationProvider): AuthenticationManager = {
    http.getSharedObject(classOf[AuthenticationManagerBuilder])
      .authenticationProvider(casAuthenticationProvider)
      .build()
  }

  @Bean
  @Order(1)
  def loginFilterChain(http: HttpSecurity, casAuthenticationEntryPoint: CasAuthenticationEntryPoint): SecurityFilterChain = {
    http
      .securityMatcher("/login")
      .authorizeHttpRequests(requests => requests.anyRequest.fullyAuthenticated)
      .exceptionHandling(c => c.authenticationEntryPoint(casAuthenticationEntryPoint))
      .build()
  }

  @Bean
  @Order(2)
  def lahetysApiFilterChain(http: HttpSecurity, authenticationFilter: CasAuthenticationFilter): SecurityFilterChain = {
    http
      .securityMatcher("/**")
      .authorizeHttpRequests(requests => requests
        .requestMatchers(HttpMethod.GET, "/openapi/**")
        .permitAll()
        .anyRequest
        .fullyAuthenticated)
      .csrf(c => c.disable())
      .exceptionHandling(c => c.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
      .addFilter(authenticationFilter)
      .build()
  }

  @Bean
  def cookieSerializer(): CookieSerializer = {
    val serializer = new DefaultCookieSerializer();
    serializer.setCookieName("JSESSIONID");
    serializer;
  }
}
