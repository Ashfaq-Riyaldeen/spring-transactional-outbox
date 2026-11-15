package io.github.outbox.order.api;

import io.github.outbox.order.api.OrderProblems.FieldViolation;
import io.swagger.v3.oas.annotations.media.Schema;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * The body of every error response as the OpenAPI document shows it. The API answers with a {@code ProblemDetail}
 * whose extra properties Jackson lifts to the top level; springdoc does not see that and would document them as a
 * nested {@code properties} object, so the document describes the body through this record instead. Nothing
 * constructs it.
 */
@Schema(description = "Error response (RFC 9457 problem detail)")
public record Problem(

        @Schema(
                description = "URI identifying the kind of problem, stable for clients to branch on",
                example = "https://spring-transactional-outbox/errors/validation-failed",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        URI type,

        @Schema(
                description = "Short summary of the kind of problem",
                example = "Validation failed",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String title,

        @Schema(description = "HTTP status code", example = "400", requiredMode = Schema.RequiredMode.REQUIRED)
        int status,

        @Schema(
                description = "Explanation of this occurrence of the problem",
                example = "Request validation failed",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String detail,

        @Schema(
                description = "Path of the request that failed",
                example = "/api/v1/orders",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        URI instance,

        @Schema(
                description = "Path of the request that failed, the same as instance",
                example = "/api/v1/orders",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String path,

        @Schema(
                description = "Moment the problem occurred",
                example = "2026-11-14T10:15:30Z",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        Instant timestamp,

        @Schema(
                description = "Rejected values of the request, present only when validation failed",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED
        )
        List<FieldViolation> errors
) {
}
