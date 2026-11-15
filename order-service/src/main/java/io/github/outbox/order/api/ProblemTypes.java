package io.github.outbox.order.api;

import java.net.URI;

/**
 * The {@code type} of every problem the order API answers with. A client branches on these, so they are public
 * contract: a type is never renamed, and one type always has the same status and title.
 */
public final class ProblemTypes {

    private static final String BASE = "https://spring-transactional-outbox/errors/";

    public static final URI VALIDATION_FAILED = URI.create(BASE + "validation-failed");
    public static final URI ORDER_NOT_FOUND = URI.create(BASE + "order-not-found");
    public static final URI ORDER_ALREADY_CANCELLED = URI.create(BASE + "order-already-cancelled");
    public static final URI RATE_LIMIT_EXCEEDED = URI.create(BASE + "rate-limit-exceeded");
    public static final URI SERVICE_BUSY = URI.create(BASE + "service-busy");
    public static final URI INTERNAL_ERROR = URI.create(BASE + "internal-error");

    private ProblemTypes() {
    }
}
