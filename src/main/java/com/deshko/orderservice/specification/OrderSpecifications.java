package com.deshko.orderservice.specification;

import com.deshko.orderservice.entity.Order;
import com.deshko.orderservice.entity.OrderStatus;
import org.springframework.data.jpa.domain.Specification;
import java.time.LocalDateTime;
import java.util.Collection;

public final class OrderSpecifications {
    private OrderSpecifications() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Specification<Order> createdFrom(LocalDateTime from) {
        return (root, _, cb) ->
                from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Order> createdTo(LocalDateTime to) {
        return (root, _, cb) ->
                to == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    public static Specification<Order> statusIn(Collection<OrderStatus> statuses) {
        return (root, _, _) ->
                statuses == null || statuses.isEmpty() ? null : root.get("status").in(statuses);
    }

    public static Specification<Order> filter(LocalDateTime from,
                                              LocalDateTime to,
                                              Collection<OrderStatus> statuses) {
        return Specification.allOf(createdFrom(from), createdTo(to), statusIn(statuses));
    }
}
