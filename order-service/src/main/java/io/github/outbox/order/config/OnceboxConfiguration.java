package io.github.outbox.order.config;

import io.github.dmitriyiliyov.oncebox.starter.EnableOncebox;
import org.springframework.context.annotation.Configuration;

// Kept off the application class, so that test slices such as @WebMvcTest do not start the outbox.
@Configuration(proxyBeanMethods = false)
@EnableOncebox
public class OnceboxConfiguration {
}
