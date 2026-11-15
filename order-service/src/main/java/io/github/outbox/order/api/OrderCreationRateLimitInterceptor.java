package io.github.outbox.order.api;

import io.github.outbox.order.ratelimit.OrderCreationRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Applies the order creation rate limits before {@code POST /api/v1/orders} reaches the controller. A refusal is
 * thrown, so the advice answers it like every other failure of the API.
 */
@RequiredArgsConstructor
public class OrderCreationRateLimitInterceptor implements HandlerInterceptor {

    private final OrderCreationRateLimiter rateLimiter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.POST.matches(request.getMethod())) {
            rateLimiter.acquire();
        }
        return true;
    }
}
