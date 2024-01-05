package fi.oph.viestinvalitys

import fi.oph.viestinvalitys.vastaanotto.resource.APIConstants
import fi.oph.viestinvalitys.vastaanotto.security.{SecurityConstants, SecurityOperaatiot}
import jakarta.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.Customizer
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.RegexRequestMatcher
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.User
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.session.web.http.CookieSerializer
import org.springframework.session.web.http.DefaultCookieSerializer
import org.springframework.http.{HttpMethod, HttpStatus}
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.csrf.{CsrfToken, CsrfTokenRequestHandler}

import java.util.function.Supplier
import scala.jdk.CollectionConverters.*

@Configuration
@Order(2)
@EnableWebSecurity
class SecurityConfiguration {

  @Bean
  def users(): UserDetailsService = {
    val user = User.withDefaultPasswordEncoder()
      .username("user")
      .password("password")
      .authorities(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL, SecurityConstants.SECURITY_ROOLI_KATSELU_FULL)
      .build()
    val lahetys = User.withDefaultPasswordEncoder()
      .username("lahetys")
      .password("password")
      .authorities(SecurityConstants.SECURITY_ROOLI_LAHETYS_FULL)
      .build()
    val katselu = User.withDefaultPasswordEncoder()
      .username("katselu")
      .password("password")
      .authorities(SecurityConstants.SECURITY_ROOLI_KATSELU_FULL)
      .build()
    new InMemoryUserDetailsManager(user, lahetys, katselu)
  }

  @Bean
  @Order(2)
  def loginFilterChain(http: HttpSecurity): SecurityFilterChain = {
    http
      .securityMatcher("/login")
      .csrf(c => c.disable())
      .formLogin(c => {
        c.defaultSuccessUrl(APIConstants.HEALTHCHECK_PATH)
      })
      .build()
  }

  @Bean
  @Order(2)
  def lahetysApiFilterChain(http: HttpSecurity): SecurityFilterChain = {
    http
      .securityMatcher("/**")
      .authorizeHttpRequests(requests => requests.anyRequest.fullyAuthenticated)
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
