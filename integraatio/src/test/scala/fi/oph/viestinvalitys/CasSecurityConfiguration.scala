package fi.oph.viestinvalitys

import com.zaxxer.hikari.HikariDataSource
import fi.oph.viestinvalitys.raportointi.App
import fi.oph.viestinvalitys.raportointi.resource.RaportointiAPIConstants
import fi.oph.viestinvalitys.util.DbUtil
import fi.vm.sade.java_utils.security.OpintopolkuCasAuthenticationFilter
import fi.vm.sade.javautils.kayttooikeusclient.OphUserDetailsServiceImpl
import org.apereo.cas.client.validation.{Cas20ProxyTicketValidator, TicketValidator}
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.{Bean, Configuration, Profile}
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.cas.ServiceProperties
import org.springframework.security.cas.authentication.CasAuthenticationProvider
import org.springframework.security.cas.web.{CasAuthenticationEntryPoint, CasAuthenticationFilter}
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession
import org.springframework.session.web.http.{CookieSerializer, DefaultCookieSerializer}

/**
 *
 */
@Configuration
@Order(3)
@EnableWebSecurity
@Profile(Array("caslocal"))
@EnableJdbcHttpSession(tableName = "RAPORTOINTI_SESSION")
class CasSecurityConfiguration {

  @Bean
  @SpringSessionDataSource
  def sessionDatasource(): HikariDataSource =
    DbUtil.pooledDatasource

  @Bean
  @Profile(Array("caslocal"))
  @Order(1)
  def casLoginFilterChain(http: HttpSecurity, casAuthenticationEntryPoint: CasAuthenticationEntryPoint): SecurityFilterChain = {
    http
      .securityMatcher(RaportointiAPIConstants.RAPORTOINTI_API_PREFIX+"/login")
      .authorizeHttpRequests(requests => requests.anyRequest.fullyAuthenticated)
      .exceptionHandling(c => c.authenticationEntryPoint(casAuthenticationEntryPoint))
      .build()
  }

  @Bean
  @Profile(Array("caslocal"))
  def raportointiApiFilterChain(http: HttpSecurity, authenticationFilter: CasAuthenticationFilter): SecurityFilterChain = {
    http
      .securityMatcher("/**")
      .authorizeHttpRequests(requests => requests.anyRequest.fullyAuthenticated)
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

  @Bean
  @Profile(Array("caslocal"))
  def serviceProperties(@Value("${cas-service.service}") service: String, @Value("${cas-service.sendRenew}") sendRenew: Boolean): ServiceProperties = {
    val serviceProperties = new ServiceProperties()
    serviceProperties.setService(service + RaportointiAPIConstants.RAPORTOINTI_API_PREFIX +"/login/j_spring_cas_security_check")
    serviceProperties.setSendRenew(sendRenew)
    serviceProperties.setAuthenticateAllArtifacts(true)
    serviceProperties
  }

  //
  // CAS authentication provider (authentication manager)
  //
  @Bean
  @Profile(Array("caslocal"))
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
  @Profile(Array("caslocal"))
  def ticketValidator(environment: Environment): TicketValidator = {
    val ticketValidator = new Cas20ProxyTicketValidator(environment.getRequiredProperty("web.url.cas"))
    ticketValidator.setAcceptAnyProxy(true)
    ticketValidator
  }

  //
  // CAS filter
  //
  @Bean
  @Profile(Array("caslocal"))
  def casAuthenticationFilter(authenticationManager: AuthenticationManager, serviceProperties: ServiceProperties): CasAuthenticationFilter = {
    val casAuthenticationFilter = new OpintopolkuCasAuthenticationFilter(serviceProperties)
    casAuthenticationFilter.setAuthenticationManager(authenticationManager)
    casAuthenticationFilter.setFilterProcessesUrl(RaportointiAPIConstants.RAPORTOINTI_API_PREFIX +"/login/j_spring_cas_security_check")
    casAuthenticationFilter
  }

  //
  // CAS entry point
  //
  @Profile(Array("caslocal"))
  @Bean def casAuthenticationEntryPoint(environment: Environment, serviceProperties: ServiceProperties): CasAuthenticationEntryPoint = {
    val casAuthenticationEntryPoint = new CasAuthenticationEntryPoint()
    casAuthenticationEntryPoint.setLoginUrl(environment.getRequiredProperty("web.url.cas") + "/login")
    casAuthenticationEntryPoint.setServiceProperties(serviceProperties)
    casAuthenticationEntryPoint
  }

  @Bean
  @Profile(Array("caslocal"))
  def authenticationManager(http: HttpSecurity, casAuthenticationProvider: CasAuthenticationProvider): AuthenticationManager = {
    http.getSharedObject(classOf[AuthenticationManagerBuilder])
      .authenticationProvider(casAuthenticationProvider)
      .build()
  }

}
