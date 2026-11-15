package io.github.outbox.order.config;

import io.github.outbox.order.api.OrderCreationBulkheadInterceptor;
import io.github.outbox.order.bulkhead.BulkheadProperties;
import io.github.outbox.order.bulkhead.OrderCreationBulkhead;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
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

    // A test slice without metrics gets a registry of its own, so the counters are always there to increment.
    public BulkheadConfiguration(BulkheadProperties properties, ObjectProvider<MeterRegistry> meterRegistry) {
        this.bulkhead = new OrderCreationBulkhead(properties, meterRegistry.getIfAvailable(SimpleMeterRegistry::new));
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
