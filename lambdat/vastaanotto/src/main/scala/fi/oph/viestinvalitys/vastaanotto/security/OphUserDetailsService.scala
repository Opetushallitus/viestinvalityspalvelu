package fi.oph.viestinvalitys.vastaanotto.security

import org.springframework.security.cas.authentication.CasAssertionAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.{AuthenticationUserDetailsService, User, UserDetails}

import scala.jdk.CollectionConverters.*

/** Builds the application user from the identity and roles in the CAS assertion. */
class OphUserDetailsService extends AuthenticationUserDetailsService[CasAssertionAuthenticationToken] {
  override def loadUserDetails(token: CasAssertionAuthenticationToken): UserDetails = {
    val attributes = token.getAssertion.getPrincipal.getAttributes
    val username = attributes.get("oidHenkilo").asInstanceOf[String]
    val roles = Option(attributes.get("roles"))
      .map(_.asInstanceOf[java.util.List[String]].asScala.toSeq)
      .getOrElse(Seq.empty)
    val authorities = roles.map(role => new SimpleGrantedAuthority(role)).asJava
    new User(username, null, authorities)
  }
}
