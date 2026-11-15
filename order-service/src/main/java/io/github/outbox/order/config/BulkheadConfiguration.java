package io.github.outbox.order.config;

import io.github.outbox.order.api.OrderCreationBulkheadInterceptor;
import io.github.outbox.order.bulkhead.BulkheadProperties;
import io.github.outbox.order.bulkhead.OrderCreationBulkhead;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BulkheadProperties.class)
public class BulkheadConfiguration implements WebMvcConfigurer {

    // After the rate limits, so that a request they refuse never takes a slot.
    static final int INTERCEPTOR_ORDER = RateLimitConfiguration.INTERCEPTOR_ORDER + 1;

    private final OrderCreationBulkhead bulkhead;

    public BulkheadConfiguration(BulkheadProperties properties) {
        this.bulkhead = new OrderCreationBulkhead(properties);
    }

    @Bean
    public OrderCreationBulkhead orderCreationBulkhead() {
        return bulkhead;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new OrderCreationBulkheadInterceptor(bulkhead))
                .addPathPatterns("/api/v1/orders")
                .order(INTERCEPTOR_ORDER);
    }
}
