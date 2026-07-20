package fi.vm.sade.viestinvalitys.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    private static final String INDEX = "forward:/viestinvalityspalvelu/index.html";

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/viestinvalityspalvelu").setViewName(INDEX);
        registry.addViewController("/viestinvalityspalvelu/").setViewName(INDEX);
        registry.addViewController("/viestinvalityspalvelu/lahetys/**").setViewName(INDEX);
        registry.addViewController("/viestinvalityspalvelu/404").setViewName(INDEX);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
                .addResourceHandler("/viestinvalityspalvelu/**")
                .addResourceLocations("classpath:/static/viestinvalityspalvelu/");
    }
}
