package io.github.outbox.order.config;

import io.github.outbox.order.api.Problem;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    private static final String SERVICE_PACKAGE = "io.github.outbox";

    @Bean
    public OpenAPI orderServiceOpenApi(@Value("${spring.application.name}") String applicationName) {
        Components components = new Components();
        ModelConverters.getInstance().readAll(Problem.class).forEach(components::addSchemas);
        return new OpenAPI()
                .info(new Info()
                        .title(applicationName + " API")
                        .description("Places and cancels orders; every change is published as an event through a "
                                + "transactional outbox")
                        .version("v1")
                        .license(new License().name("Apache-2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .components(components);
    }

    /**
     * Documents every 4xx answer of the service's own endpoints as a {@link Problem} in
     * {@code application/problem+json}, replacing the success body springdoc infers from the return type. Endpoints
     * of libraries, such as the outbox DLQ API, answer through their own advice and keep their own documentation.
     */
    @Bean
    public OperationCustomizer problemResponsesCustomizer() {
        return (operation, handlerMethod) -> {
            if (operation.getResponses() == null
                    || !handlerMethod.getBeanType().getPackageName().startsWith(SERVICE_PACKAGE)) {
                return operation;
            }
            operation.getResponses().forEach((code, response) -> {
                if (code.startsWith("4")) {
                    response.setContent(new Content().addMediaType(
                            org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/Problem"))
                    ));
                }
            });
            return operation;
        };
    }
}
