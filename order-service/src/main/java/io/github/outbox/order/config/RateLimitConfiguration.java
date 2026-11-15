package io.github.outbox.order.config;

import io.github.outbox.order.api.OrderCreationRateLimitInterceptor;
import io.github.outbox.order.ratelimit.OrderCreationRateLimiter;
import io.github.outbox.order.ratelimit.RateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfiguration implements WebMvcConfigurer {

    static final int INTERCEPTOR_ORDER = 0;

    private final OrderCreationRateLimiter rateLimiter;

    public RateLimitConfiguration(RateLimitProperties properties) {
        this.rateLimiter = new OrderCreationRateLimiter(properties);
    }

    @Bean
    public OrderCreationRateLimiter orderCreationRateLimiter() {
        return rateLimiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new OrderCreationRateLimitInterceptor(rateLimiter))
                .addPathPatterns("/api/v1/orders")
                .order(INTERCEPTOR_ORDER);
    }
}
