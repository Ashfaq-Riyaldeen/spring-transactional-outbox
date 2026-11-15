package io.github.outbox.order.api;

import io.github.outbox.order.bulkhead.OrderCreationBulkhead;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Holds a bulkhead slot for the whole of {@code POST /api/v1/orders}, from before the controller until the response
 * is complete, whatever its outcome. A refusal is thrown, so the advice answers it like every other failure.
 */
@RequiredArgsConstructor
public class OrderCreationBulkheadInterceptor implements HandlerInterceptor {

    private static final String SLOT_HELD = OrderCreationBulkheadInterceptor.class.getName() + ".SLOT_HELD";

    private final OrderCreationBulkhead bulkhead;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.POST.matches(request.getMethod())) {
            bulkhead.acquire();
            request.setAttribute(SLOT_HELD, Boolean.TRUE);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception exception) {
        if (request.getAttribute(SLOT_HELD) != null) {
            request.removeAttribute(SLOT_HELD);
            bulkhead.release();
        }
    }
}
