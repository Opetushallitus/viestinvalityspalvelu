package fi.oph.viestinvalitys

import com.zaxxer.hikari.HikariDataSource
import fi.oph.viestinvalitys.util.DbUtil
import fi.oph.viestinvalitys.vastaanotto.security.SecurityConstants
import fi.oph.viestinvalitys.vastaanotto.security.SecurityConstants.SECURITY_ROOLI_PREFIX
import org.springframework.context.annotation.{Bean, Configuration, Profile}
import org.springframework.core.annotation.Order
import org.springframework.http.{HttpMethod, HttpStatus}
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.userdetails.{User, UserDetails, UserDetailsService}
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.session.jdbc.config.annotation.SpringSessionDataSource
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession
import org.springframework.session.web.http.{CookieSerializer, DefaultCookieSerializer}

/**
 *
 */
@Configuration
@Profile(Array("!caslocal"))
@Order(2)
@EnableWebSecurity
@EnableJdbcHttpSession(tableName = "LAHETYS_SESSION")
class SecurityConfiguration {

  @Bean
  @SpringSessionDataSource
  def sessionDatasource(): HikariDataSource =
    DbUtil.pooledDatasource

  @Bean
  def users(): UserDetailsService = {
    val admin = User.withDefaultPasswordEncoder()
      .username("admin")
      .password("password")
      .authorities(SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA, SecurityConstants.SECURITY_ROOLI_PAAKAYTTAJA+"_"+SecurityConstants.OPH_ORGANISAATIO_OID)
      .build()
    val user = User.withDefaultPasswordEncoder()
      .username("user")
      .password("password")
      .authorities(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL, SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL+"_1.2.246.562.10.240484683010", SecurityConstants.SECURITY_ROOLI_KATSELU_FULL+"_1.2.246.562.10.73999728683", SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, SecurityConstants.SECURITY_ROOLI_KATSELU_FULL+"_1.2.246.562.10.240484683010", SECURITY_ROOLI_PREFIX + "APP_OIKEUS", SECURITY_ROOLI_PREFIX +"APP_OIKEUS_1.2.246.562.10.240484683010", SECURITY_ROOLI_PREFIX +"APP_HAKEMUS_CRUD", "APP_HAKEMUS_CRUD_1.2.246.562.10.240484683010", SECURITY_ROOLI_PREFIX +"APP_HAKEMUS_CRUD_1.2.246.562.10.73999728683")
      .build()
    val testi = User.withDefaultPasswordEncoder()
      .username("1.2.3.4.0")
      .password("password")
      .authorities(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL, SecurityConstants.SECURITY_ROOLI_KATSELU_FULL)
      .build()
    val lahetys = User.withDefaultPasswordEncoder()
      .username("lahetys")
      .password("password")
      .authorities(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL, SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL+"_1.2.246.562.10.240484683010")
      .build()
    val katselu = User.withDefaultPasswordEncoder()
      .username("katselu")
      .password("password")
      .authorities(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL, SecurityConstants.SECURITY_ROOLI_KATSELU_FULL+"_1.2.246.562.10.240484683010")
      .build()
    new InMemoryUserDetailsManager(admin, user, testi, lahetys, katselu)
  }

  @Bean
  @Order(2)
  def loginFilterChain(http: HttpSecurity): SecurityFilterChain = {
    http
      .securityMatcher("/login")
      .csrf(c => c.disable())
      .formLogin(c => {
        // ohjataan lokaaliympäristön raportointikäliin
        c.defaultSuccessUrl("http://localhost:3000/raportointi")
      })
      .build()
  }

  @Bean
  @Order(2)
  def lahetysApiFilterChain(http: HttpSecurity): SecurityFilterChain = {
    http
      .securityMatcher("/**")
      .authorizeHttpRequests(requests => requests
        .requestMatchers(HttpMethod.GET, "/openapi/**", "/static/**")
        .permitAll()
        .anyRequest
        .fullyAuthenticated)
      .csrf(c => c.disable())
      .exceptionHandling(c => c.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
      .build()
  }

  @Bean
  def cookieSerializer(): CookieSerializer = {
    val serializer = new DefaultCookieSerializer();
    serializer.setCookieName("JSESSIONID");
    serializer;
  }
}
