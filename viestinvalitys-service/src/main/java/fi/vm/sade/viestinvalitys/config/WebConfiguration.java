package fi.vm.sade.viestinvalitys.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    private static final String INDEX = "forward:/raportointi/index.html";

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/raportointi").setViewName(INDEX);
        registry.addViewController("/raportointi/").setViewName(INDEX);
        registry.addViewController("/raportointi/lahetys/**").setViewName(INDEX);
        registry.addViewController("/raportointi/404").setViewName(INDEX);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry
                .addResourceHandler("/raportointi/**")
                .addResourceLocations("classpath:/static/raportointi/");
    }
}
