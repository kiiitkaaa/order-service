package com.deshko.orderservice.util;

import com.deshko.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.security.core.Authentication;

@Component("orderAccess")
@RequiredArgsConstructor
public class OrderAccess {
    private final OrderRepository orderRepository;

    public boolean isOwner(Long orderId, Authentication authentication) {
        try {
            Long userId = Long.valueOf(authentication.getName());
            return orderRepository.existsByIdAndUserId(orderId, userId);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
