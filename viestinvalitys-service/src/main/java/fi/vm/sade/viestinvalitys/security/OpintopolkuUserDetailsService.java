package fi.vm.sade.viestinvalitys.security;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.cas.authentication.CasAssertionAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class OpintopolkuUserDetailsService
        implements AuthenticationUserDetailsService<CasAssertionAuthenticationToken> {

    @Override
    @SuppressWarnings("unchecked")
    public UserDetails loadUserDetails(CasAssertionAuthenticationToken token)
            throws UsernameNotFoundException {
        var attributes = token.getAssertion().getPrincipal().getAttributes();
        var roles = attributes.containsKey("roles")
                ? (List<String>) attributes.get("roles")
                : List.<String>of();
        return new OpintopolkuUserDetails((String) attributes.get("oidHenkilo"), roles);
    }

    public static final class OpintopolkuUserDetails implements UserDetails {
        @Serial private static final long serialVersionUID = 1L;

        private final String oidHenkilo;
        private final Collection<SimpleGrantedAuthority> authorities;

        public OpintopolkuUserDetails(String oidHenkilo, List<String> authorities) {
            this.oidHenkilo = oidHenkilo;
            this.authorities =
                    authorities.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
        }

        @Override
        public Collection<SimpleGrantedAuthority> getAuthorities() {
            return authorities;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public String getUsername() {
            return oidHenkilo;
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;
        }

        @Override
        public boolean isAccountNonLocked() {
            return true;
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }
}
