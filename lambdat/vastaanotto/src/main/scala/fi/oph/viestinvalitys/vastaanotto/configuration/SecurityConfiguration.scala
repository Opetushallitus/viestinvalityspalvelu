package fi.oph.viestinvalitys.vastaanotto.configuration

import com.zaxxer.hikari.HikariDataSource
import fi.oph.viestinvalitys.util.DbUtil
import fi.oph.viestinvalitys.vastaanotto.App
import fi.oph.viestinvalitys.vastaanotto.resource.LahetysAPIConstants
import fi.vm.sade.java_utils.security.OpintopolkuCasAuthenticationFilter
import fi.vm.sade.javautils.kayttooikeusclient.OphUserDetailsServiceImpl
import jakarta.servlet.http.HttpSessionEvent
import org.apereo.cas.client.session.{SingleSignOutFilter, SingleSignOutHttpSessionListener}
import org.apereo.cas.client.validation.{Cas20ProxyTicketValidator, TicketValidator}
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.{Bean, Configuration, Profile}
import org.springframework.context.event.EventListener
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
import org.springframework.security.web.authentication.logout.{SecurityContextLogoutHandler}
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession
import org.springframework.session.jdbc.{JdbcIndexedSessionRepository, PostgreSqlJdbcIndexedSessionRepositoryCustomizer}
import org.springframework.session.web.http.{CookieSerializer, DefaultCookieSerializer}

@Configuration
@Order(2)
@EnableWebSecurity
@EnableJdbcHttpSession(tableName = "LAHETYS_SESSION")
@Profile(Array("default"))
class SecurityConfiguration {

  @Bean
  def sessionStoreCustomizer(): PostgreSqlJdbcIndexedSessionRepositoryCustomizer =
    new PostgreSqlJdbcIndexedSessionRepositoryCustomizer() {
      // Ei päivitetä sessiota loginin jälkeen. Kuormatestissä tämä mahdollistaa suunnilleen tuplanopeuden
      override def customize(sessionRepository: JdbcIndexedSessionRepository): Unit =
        sessionRepository.setUpdateSessionQuery("UPDATE %TABLE_NAME%\nSET SESSION_ID = ?, LAST_ACCESS_TIME = ?, MAX_INACTIVE_INTERVAL = ?, EXPIRY_TIME = ?, PRINCIPAL_NAME = ?\nWHERE PRIMARY_ID = ? AND PRINCIPAL_NAME IS NULL\n")
    }

  @Bean
  @SpringSessionDataSource
  def sessionDatasource(): HikariDataSource =
    DbUtil.pooledDatasource

  @Bean
  def serviceProperties(@Value("${cas-service.service}") service: String, @Value("${cas-service.sendRenew}") sendRenew: Boolean): ServiceProperties = {
    val serviceProperties = new ServiceProperties()
    serviceProperties.setService(service + LahetysAPIConstants.LAHETYS_API_PREFIX + "/login/j_spring_cas_security_check")
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
    casAuthenticationFilter.setFilterProcessesUrl(LahetysAPIConstants.LAHETYS_API_PREFIX + "/login/j_spring_cas_security_check")
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
      .securityMatcher(LahetysAPIConstants.LOGIN_PATH)
      .authorizeHttpRequests(requests => requests.anyRequest.fullyAuthenticated)
      .exceptionHandling(c => c.authenticationEntryPoint(casAuthenticationEntryPoint))
      .build()
  }

  @Bean
  @Order(2)
  def lahetysApiFilterChain(http: HttpSecurity, authenticationFilter: CasAuthenticationFilter, environment: Environment): SecurityFilterChain = {
    http
      .securityMatcher("/**")
      .authorizeHttpRequests(requests => requests
        .requestMatchers(HttpMethod.GET, "/openapi/**", "/swagger")
        .permitAll()
        .anyRequest
        .fullyAuthenticated)
      .csrf(c => c.disable())
      .exceptionHandling(c => c.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
      .addFilter(authenticationFilter)
      .addFilterBefore(singleLogoutFilter(environment), classOf[CasAuthenticationFilter])
      .build()
  }

  @Bean
  def cookieSerializer(): CookieSerializer = {
    val serializer = new DefaultCookieSerializer();
    serializer.setCookieName("JSESSIONID");
    serializer.setCookiePath(LahetysAPIConstants.LAHETYS_API_PREFIX)
    serializer;
  }

  @Bean
  def securityContextLogoutHandler(): SecurityContextLogoutHandler = {
    val securityContextLogoutHandler = new SecurityContextLogoutHandler();
    securityContextLogoutHandler
  }

  //
  // Käsitellään CASilta tuleva SLO-pyyntö ja suljetaan istunto
  //
  @Bean
  def singleLogoutFilter(environment: Environment): SingleSignOutFilter = {
    val singleSignOutFilter: SingleSignOutFilter = new SingleSignOutFilter();
    singleSignOutFilter.setIgnoreInitConfiguration(true);
    singleSignOutFilter
  }

  @EventListener
  def singleSignOutHttpSessionListener(event: HttpSessionEvent): SingleSignOutHttpSessionListener = {
    val singleSignOutHttpSessionListener = new SingleSignOutHttpSessionListener()
    singleSignOutHttpSessionListener
  }

}
