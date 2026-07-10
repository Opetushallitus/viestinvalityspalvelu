package fi.vm.sade.viestinvalitys.resource;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;

@Controller
public class SpaController {

    private final Resource indexHtml = new ClassPathResource("static/raportointi/index.html");

    @GetMapping(value = "/raportointi", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public byte[] raportointiRoot() throws IOException {
        return indexHtml.getContentAsByteArray();
    }

    @GetMapping(value = "/raportointi/", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public byte[] raportointiRootSlash() throws IOException {
        return indexHtml.getContentAsByteArray();
    }

    @GetMapping(value = {
            "/raportointi/lahetys/**",
            "/raportointi/404"
    }, produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public byte[] spaRoutes() throws IOException {
        return indexHtml.getContentAsByteArray();
    }
}
