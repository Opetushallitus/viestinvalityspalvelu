package fi.vm.sade.viestinvalitys.logging;

import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class FilterConfiguration {

    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        var bean = new FilterRegistrationBean<>(new RequestIdFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    FilterRegistrationBean<RequestCallerFilter> requestCallerFilter() {
        var bean = new FilterRegistrationBean<>(new RequestCallerFilter());
        bean.setOrder(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1);
        return bean;
    }
}
