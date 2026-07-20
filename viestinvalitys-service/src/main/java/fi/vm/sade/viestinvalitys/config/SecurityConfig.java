package fi.vm.sade.viestinvalitys.config;

import fi.vm.sade.viestinvalitys.security.OpintopolkuUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apereo.cas.client.session.SessionMappingStorage;
import org.apereo.cas.client.session.SingleSignOutFilter;
import org.apereo.cas.client.validation.Cas30ProxyTicketValidator;
import org.apereo.cas.client.validation.TicketValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.cas.ServiceProperties;
import org.springframework.security.cas.authentication.CasAuthenticationProvider;
import org.springframework.security.cas.web.CasAuthenticationEntryPoint;
import org.springframework.security.cas.web.CasAuthenticationFilter;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = false, prePostEnabled = true, securedEnabled = true)
public class SecurityConfig {

    private static final String APP_PREFIX = "/viestinvalityspalvelu";
    private static final String CAS_LOGIN_PATH = "/login/j_spring_cas_security_check";
    private static final String CAS_CALLBACK = APP_PREFIX + CAS_LOGIN_PATH;

    @Bean
    public ServiceProperties serviceProperties(@Value("${cas.service}") String service) {
        ServiceProperties sp = new ServiceProperties();
        sp.setService(service + CAS_LOGIN_PATH);
        sp.setSendRenew(false);
        sp.setAuthenticateAllArtifacts(true);
        return sp;
    }

    @Bean
    public TicketValidator ticketValidator(@Value("${cas.base}") String casUrl) {
        Cas30ProxyTicketValidator validator = new Cas30ProxyTicketValidator(casUrl);
        validator.setAcceptAnyProxy(true);
        return validator;
    }

    @Bean
    public CasAuthenticationProvider casAuthenticationProvider(
            ServiceProperties serviceProperties,
            TicketValidator ticketValidator,
            @Value("${cas.key}") String key) {
        CasAuthenticationProvider provider = new CasAuthenticationProvider();
        provider.setAuthenticationUserDetailsService(new OpintopolkuUserDetailsService());
        provider.setServiceProperties(serviceProperties);
        provider.setTicketValidator(ticketValidator);
        provider.setKey(key);
        return provider;
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CasAuthenticationFilter casAuthenticationFilter(
            AuthenticationManager authenticationManager,
            ServiceProperties serviceProperties,
            SecurityContextRepository securityContextRepository,
            @Value("${raportointi.login-success-url:/viestinvalityspalvelu}") String loginSuccessUrl) {
        CasAuthenticationFilter filter = new CasAuthenticationFilter();
        filter.setAuthenticationManager(authenticationManager);
        filter.setServiceProperties(serviceProperties);
        filter.setFilterProcessesUrl(CAS_CALLBACK);
        filter.setSecurityContextRepository(securityContextRepository);
        SimpleUrlAuthenticationSuccessHandler successHandler =
                new SimpleUrlAuthenticationSuccessHandler(loginSuccessUrl);
        successHandler.setAlwaysUseDefaultTargetUrl(true);
        filter.setAuthenticationSuccessHandler(successHandler);
        return filter;
    }

    @Bean
    public CasAuthenticationEntryPoint casAuthenticationEntryPoint(
            @Value("${cas.base}") String casUrl,
            ServiceProperties serviceProperties) {
        CasAuthenticationEntryPoint entryPoint = new CasAuthenticationEntryPoint();
        entryPoint.setLoginUrl(casUrl + "/login");
        entryPoint.setServiceProperties(serviceProperties);
        return entryPoint;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            HttpSecurity http,
            CasAuthenticationProvider casAuthenticationProvider) throws Exception {
        return http.getSharedObject(AuthenticationManagerBuilder.class)
                .authenticationProvider(casAuthenticationProvider)
                .build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain oauth2FilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(this::isOauth2Request)
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(oauth2JwtConverter())))
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain casFilterChain(
            HttpSecurity http,
            CasAuthenticationEntryPoint casAuthenticationEntryPoint,
            CasAuthenticationFilter casAuthenticationFilter,
            SingleSignOutFilter singleSignOutFilter,
            SecurityContextRepository securityContextRepository) throws Exception {
        return http
                .csrf(CsrfConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(CAS_CALLBACK).permitAll()
                        .anyRequest().authenticated())
                .addFilterAt(casAuthenticationFilter, CasAuthenticationFilter.class)
                .addFilterBefore(singleSignOutFilter, CasAuthenticationFilter.class)
                .securityContext(sc -> sc
                        .requireExplicitSave(true)
                        .securityContextRepository(securityContextRepository))
                .exceptionHandling(e -> e.authenticationEntryPoint(casAuthenticationEntryPoint))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .deleteCookies("JSESSIONID"))
                .build();
    }

    @Bean
    public SingleSignOutFilter singleSignOutFilter(SessionMappingStorage sessionMappingStorage) {
        SingleSignOutFilter.setSessionMappingStorage(sessionMappingStorage);
        SingleSignOutFilter filter = new SingleSignOutFilter();
        filter.setIgnoreInitConfiguration(true);
        return filter;
    }

    @Bean
    public CookieSerializer cookieSerializer(
            @Value("${cas.cookie-secure:true}") boolean secureCookie) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setUseSecureCookie(secureCookie);
        serializer.setCookieName("JSESSIONID");
        serializer.setCookiePath(APP_PREFIX);
        return serializer;
    }

    private Converter<Jwt, AbstractAuthenticationToken> oauth2JwtConverter() {
        return new Converter<Jwt, AbstractAuthenticationToken>() {
            final JwtGrantedAuthoritiesConverter delegate = new JwtGrantedAuthoritiesConverter();

            @Override
            public AbstractAuthenticationToken convert(Jwt source) {
                var authorityList = extractRoles(source);
                var delegateAuthorities = delegate.convert(source);
                if (delegateAuthorities != null) {
                    authorityList.addAll(delegateAuthorities);
                }
                return new JwtAuthenticationToken(source, authorityList);
            }

            private List<GrantedAuthority> extractRoles(Jwt jwt) {
                Map<String, List<String>> roleClaim = extractRoleClaim(jwt);
                return roleClaim.keySet().stream()
                        .map(oid -> {
                            var orgRoles = roleClaim.get(oid);
                            return orgRoles.stream().map(role -> List.of(
                                    "ROLE_APP_" + role,
                                    "ROLE_APP_" + role + "_" + oid
                            )).toList();
                        })
                        .flatMap(List::stream)
                        .flatMap(List::stream)
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.<GrantedAuthority>toList());
            }

            private Map<String, List<String>> extractRoleClaim(Jwt jwt) {
                Object rolesClaim = jwt.getClaims().get("roles");
                if (!(rolesClaim instanceof Map<?, ?> roleClaim)) {
                    return Map.of();
                }
                return roleClaim.entrySet().stream()
                        .filter(entry -> entry.getKey() instanceof String && entry.getValue() instanceof List<?>)
                        .collect(Collectors.toMap(
                                entry -> (String) entry.getKey(),
                                entry -> ((List<?>) entry.getValue()).stream().map(String.class::cast).toList()
                        ));
            }
        };
    }

    private boolean isOauth2Request(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        return authorization != null && authorization.startsWith("Bearer ");
    }
}
