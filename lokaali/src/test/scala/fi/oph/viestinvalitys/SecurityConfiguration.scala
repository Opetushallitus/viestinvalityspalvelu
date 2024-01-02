package fi.oph.viestinvalitys

import fi.oph.viestinvalitys.vastaanotto.security.{SecurityConstants, SecurityOperaatiot}
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
import org.springframework.http.HttpMethod

import scala.jdk.CollectionConverters.*

@Configuration
@Order(2)
@EnableWebSecurity
class SecurityConfiguration2 {

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
  def filterChain(http: HttpSecurity): SecurityFilterChain = {
    http.headers()
      .disable()
      .csrf()
      .disable()
      .authorizeHttpRequests()
      .anyRequest()
      .authenticated()
      .and()
      .exceptionHandling()
      .and()
      .formLogin(Customizer.withDefaults())
    http.build();
  }

  @Bean
  def cookieSerializer(): CookieSerializer = {
    val serializer = new DefaultCookieSerializer();
    serializer.setCookieName("JSESSIONID");
    serializer;
  }
}
