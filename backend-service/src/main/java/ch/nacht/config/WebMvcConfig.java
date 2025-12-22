package ch.nacht.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC Konfiguration für Interceptors.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final OrganizationInterceptor organizationInterceptor;

    public WebMvcConfig(OrganizationInterceptor organizationInterceptor) {
        this.organizationInterceptor = organizationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(organizationInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/public/**", "/actuator/**", "/ping");
    }
}
