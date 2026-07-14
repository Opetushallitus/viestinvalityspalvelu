package fi.vm.sade.viestinvalitys.config;

import fi.vm.sade.viestinvalitys.security.OpintopolkuUserDetailsService;
import org.apereo.cas.client.session.SessionMappingStorage;
import org.apereo.cas.client.session.SingleSignOutFilter;
import org.apereo.cas.client.validation.Cas30ProxyTicketValidator;
import org.apereo.cas.client.validation.TicketValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(jsr250Enabled = false, prePostEnabled = true, securedEnabled = true)
public class SecurityConfig {

    private static final String RAPORTOINTI_PREFIX = "/raportointi";
    private static final String CAS_CALLBACK = RAPORTOINTI_PREFIX + "/login/j_spring_cas_security_check";

    @Bean
    public ServiceProperties serviceProperties(@Value("${cas-service.service}") String service) {
        ServiceProperties sp = new ServiceProperties();
        sp.setService(service + CAS_CALLBACK);
        sp.setSendRenew(false);
        sp.setAuthenticateAllArtifacts(true);
        return sp;
    }

    @Bean
    public TicketValidator ticketValidator(@Value("${web.url.cas}") String casUrl) {
        Cas30ProxyTicketValidator validator = new Cas30ProxyTicketValidator(casUrl);
        validator.setAcceptAnyProxy(true);
        return validator;
    }

    @Bean
    public CasAuthenticationProvider casAuthenticationProvider(
            ServiceProperties serviceProperties,
            TicketValidator ticketValidator,
            @Value("${cas-service.key}") String key) {
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
            @Value("${raportointi.login-success-url:/raportointi}") String loginSuccessUrl) {
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
            @Value("${web.url.cas}") String casUrl,
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
    public SecurityFilterChain loginFilterChain(
            HttpSecurity http,
            CasAuthenticationEntryPoint casAuthenticationEntryPoint,
            CasAuthenticationFilter casAuthenticationFilter,
            SingleSignOutFilter singleSignOutFilter,
            SecurityContextRepository securityContextRepository) throws Exception {
        return http
                .securityMatcher(RAPORTOINTI_PREFIX + "/login", RAPORTOINTI_PREFIX + "/login/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(CAS_CALLBACK).permitAll()
                        .anyRequest().fullyAuthenticated())
                .addFilterAt(casAuthenticationFilter, CasAuthenticationFilter.class)
                .addFilterBefore(singleSignOutFilter, CasAuthenticationFilter.class)
                .securityContext(sc -> sc
                        .requireExplicitSave(true)
                        .securityContextRepository(securityContextRepository))
                .exceptionHandling(e -> e.authenticationEntryPoint(casAuthenticationEntryPoint))
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository) throws Exception {
        return http
                .csrf(CsrfConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/raportointi/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .securityContext(sc -> sc
                        .requireExplicitSave(true)
                        .securityContextRepository(securityContextRepository))
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
            @Value("${cas-service.cookie-secure:true}") boolean secureCookie) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setUseSecureCookie(secureCookie);
        serializer.setCookieName("JSESSIONID");
        serializer.setCookiePath(RAPORTOINTI_PREFIX);
        return serializer;
    }
}
