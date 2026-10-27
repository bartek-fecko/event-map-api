package pl.eventmap.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import pl.eventmap.common.RateLimitFilter;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties properties;

    public WebConfig(AppProperties properties) {
        this.properties = properties;
    }

    /**
     * Serves uploaded event covers straight from disk. In production Caddy
     * short-circuits /media/** before it ever reaches the JVM.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = properties.media().storageDir().toAbsolutePath().toUri().toString();
        registry.addResourceHandler(properties.media().publicBaseUrl() + "/**")
                .addResourceLocations(location);
    }

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(AppProperties properties) {
        var registration = new FilterRegistrationBean<>(
                new RateLimitFilter(properties.auth().rateLimitPerMinute()));
        registration.addUrlPatterns("/api/v1/auth/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
