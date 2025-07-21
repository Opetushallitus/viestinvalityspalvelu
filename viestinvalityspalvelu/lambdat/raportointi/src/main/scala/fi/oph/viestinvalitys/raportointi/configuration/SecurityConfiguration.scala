package fi.oph.viestinvalitys.raportointi.configuration

import com.zaxxer.hikari.HikariDataSource
import fi.oph.viestinvalitys.raportointi.resource.RaportointiAPIConstants
import fi.oph.viestinvalitys.util.DbUtil
import fi.vm.sade.javautils.kayttooikeusclient.OphUserDetailsServiceImpl
import org.apereo.cas.client.session.{SessionMappingStorage, SingleSignOutFilter}
import org.apereo.cas.client.validation.{Cas20ProxyTicketValidator, TicketValidator}
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.{Bean, Configuration, Profile}
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.http.{HttpMethod, HttpStatus}
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
import org.springframework.security.web.context.{HttpSessionSecurityContextRepository, SecurityContextRepository}
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession
import org.springframework.session.web.http.{CookieSerializer, DefaultCookieSerializer}

@Configuration
@Order(2)
@EnableWebSecurity
@Profile(Array("default"))
@EnableJdbcHttpSession(tableName = "RAPORTOINTI_SESSION")
class SecurityConfiguration {

  val LOG = LoggerFactory.getLogger(classOf[SecurityConfiguration])
  @Bean
  @SpringSessionDataSource
  def sessionDatasource(): HikariDataSource =
    DbUtil.pooledDatasource

  @Bean
  def serviceProperties(@Value("${cas-service.service}") service: String, @Value("${cas-service.sendRenew}") sendRenew: Boolean): ServiceProperties = {
    val serviceProperties = new ServiceProperties()
    serviceProperties.setService(service + RaportointiAPIConstants.RAPORTOINTI_API_PREFIX + "/v1/j_spring_cas_security_check")
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
    casAuthenticationProvider.setAuthenticationUserDetailsService(new OphUserDetailsServiceImpl())
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

  @Bean
  def securityContextRepository (): HttpSessionSecurityContextRepository = {
    val httpSessionSecurityContextRepository = new HttpSessionSecurityContextRepository()
    httpSessionSecurityContextRepository
  }
  //
  // CAS filter
  //
  @Bean
  def casAuthenticationFilter(authenticationManager: AuthenticationManager, serviceProperties: ServiceProperties, securityContextRepository: SecurityContextRepository): CasAuthenticationFilter = {
    val casAuthenticationFilter = new CasAuthenticationFilter()
    casAuthenticationFilter.setAuthenticationManager(authenticationManager)
    casAuthenticationFilter.setServiceProperties(serviceProperties)
    casAuthenticationFilter.setFilterProcessesUrl(RaportointiAPIConstants.RAPORTOINTI_API_PREFIX + "/v1/j_spring_cas_security_check")
    casAuthenticationFilter.setSecurityContextRepository(securityContextRepository)
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
      .securityMatcher(RaportointiAPIConstants.LOGIN_PATH)
      .authorizeHttpRequests(requests =>
        requests.requestMatchers("/v1/j_spring_cas_security_check").permitAll() // päästetään läpi cas-logout
        .anyRequest.fullyAuthenticated)
      .exceptionHandling(c => c.authenticationEntryPoint(casAuthenticationEntryPoint))
      .build()
  }

  @Bean
  def raportointiApiFilterChain(http: HttpSecurity, authenticationFilter: CasAuthenticationFilter, sessionMappingStorage: SessionMappingStorage,
                                securityContextRepository: SecurityContextRepository, casAuthenticationEntryPoint: CasAuthenticationEntryPoint): SecurityFilterChain = {
    http
      .csrf(c => c.disable())
      .securityMatcher("/**")
      .authorizeHttpRequests(requests => requests
        .requestMatchers(HttpMethod.GET, RaportointiAPIConstants.HEALTHCHECK_PATH)
        .permitAll()
        .anyRequest
        .fullyAuthenticated)
      .exceptionHandling(c => c.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
      .addFilterAt(authenticationFilter, classOf[CasAuthenticationFilter])
      .addFilterBefore(singleLogoutFilter(sessionMappingStorage), classOf[CasAuthenticationFilter])
      .securityContext(securityContext => securityContext
        .requireExplicitSave(true)
        .securityContextRepository(securityContextRepository))
      .logout(logout =>
        logout.logoutUrl("/logout")
        .deleteCookies("JSESSIONID"))
      .build()
  }

  @Bean
  def cookieSerializer(): CookieSerializer = {
    val serializer = new DefaultCookieSerializer();
    serializer.setUseSecureCookie(true)
    serializer.setCookieName("JSESSIONID");
    serializer.setCookiePath(RaportointiAPIConstants.RAPORTOINTI_API_PREFIX)
    serializer;
  }

  //
  // Käsitellään CASilta tuleva SLO-pyyntö ja suljetaan istunto
  // requestSingleLogoutFilter ei ole tarpeen, koska käyttäjät kirjautuvat ulos aina CAS-logoutilla virkailija-raamien kautta
  // CAS lähettää kutsun tähän filtteriin jos käyttäjällä on tiketti tähän palveluun
  //
  @Bean
  def singleLogoutFilter(sessionMappingStorage: SessionMappingStorage): SingleSignOutFilter = {
    SingleSignOutFilter.setSessionMappingStorage(sessionMappingStorage)
    val singleSignOutFilter: SingleSignOutFilter = new SingleSignOutFilter()
    singleSignOutFilter.setIgnoreInitConfiguration(true)
    singleSignOutFilter
  }

}
