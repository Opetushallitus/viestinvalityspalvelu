package fi.vm.sade.viestinvalitys.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    private static final String INDEX = "forward:/index.html";

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName(INDEX);
        registry.addViewController("/lahetys/**").setViewName(INDEX);
        registry.addViewController("/404").setViewName(INDEX);
    }
}
