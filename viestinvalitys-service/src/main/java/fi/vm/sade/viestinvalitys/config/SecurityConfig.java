package fi.vm.sade.viestinvalitys.config;

import fi.vm.sade.javautils.kayttooikeusclient.OphUserDetailsServiceImpl;
import org.apereo.cas.client.session.SingleSignOutFilter;
import org.apereo.cas.client.validation.Cas20ProxyTicketValidator;
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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String RAPORTOINTI_PREFIX = "/raportointi";
    private static final String CAS_CALLBACK = RAPORTOINTI_PREFIX + "/login/j_spring_cas_security_check";

    @Bean
    public ServiceProperties serviceProperties(
            @Value("${cas-service.service}") String service,
            @Value("${cas-service.sendRenew:false}") boolean sendRenew) {
        ServiceProperties sp = new ServiceProperties();
        sp.setService(service + CAS_CALLBACK);
        sp.setSendRenew(sendRenew);
        sp.setAuthenticateAllArtifacts(true);
        return sp;
    }

    @Bean
    public TicketValidator ticketValidator(@Value("${web.url.cas}") String casUrl) {
        Cas20ProxyTicketValidator validator = new Cas20ProxyTicketValidator(casUrl);
        validator.setAcceptAnyProxy(true);
        return validator;
    }

    @Bean
    public CasAuthenticationProvider casAuthenticationProvider(
            ServiceProperties serviceProperties,
            TicketValidator ticketValidator,
            @Value("${cas-service.key}") String key) {
        CasAuthenticationProvider provider = new CasAuthenticationProvider();
        provider.setAuthenticationUserDetailsService(new OphUserDetailsServiceImpl());
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
        // After a successful CAS login, send the browser to the raportointi UI. Locally
        // the UI runs on a separate dev-server origin (:3000), so this must be absolute.
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
            CasAuthenticationEntryPoint casAuthenticationEntryPoint) throws Exception {
        return http
                .securityMatcher(RAPORTOINTI_PREFIX + "/login", RAPORTOINTI_PREFIX + "/login/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(CAS_CALLBACK).permitAll()
                        .anyRequest().fullyAuthenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(casAuthenticationEntryPoint))
                .build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiFilterChain(
            HttpSecurity http,
            CasAuthenticationFilter casAuthenticationFilter,
            SecurityContextRepository securityContextRepository,
            SingleSignOutFilter singleSignOutFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/raportointi/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterAt(casAuthenticationFilter, CasAuthenticationFilter.class)
                .addFilterBefore(singleSignOutFilter, CasAuthenticationFilter.class)
                .securityContext(sc -> sc
                        .requireExplicitSave(true)
                        .securityContextRepository(securityContextRepository))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .deleteCookies("JSESSIONID"))
                .build();
    }

    @Bean
    public SingleSignOutFilter singleSignOutFilter() {
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
