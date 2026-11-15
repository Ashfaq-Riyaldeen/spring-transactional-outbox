package io.github.outbox.order.api;

import io.github.outbox.order.ratelimit.OrderCreationRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies the global and the per-address order creation limits before {@code POST /api/v1/orders} reaches the
 * controller; the per-customer limit needs the request body and is applied by the order service. A refusal is
 * thrown, so the advice answers it like every other failure of the API.
 */
@RequiredArgsConstructor
public class OrderCreationRateLimitInterceptor implements HandlerInterceptor {

    private final OrderCreationRateLimiter rateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.POST.matches(request.getMethod())) {
            rateLimiter.acquire(request.getRemoteAddr());
        }
        return true;
    }
}
