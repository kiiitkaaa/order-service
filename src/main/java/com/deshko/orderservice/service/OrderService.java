package com.deshko.orderservice.service;

import com.deshko.orderservice.client.UserServiceClient;
import com.deshko.orderservice.dto.*;
import com.deshko.orderservice.entity.*;
import com.deshko.orderservice.exception.*;
import com.deshko.orderservice.mapper.OrderItemMapper;
import com.deshko.orderservice.mapper.OrderMapper;
import com.deshko.orderservice.repository.ItemRepository;
import com.deshko.orderservice.repository.OrderRepository;
import com.deshko.orderservice.specification.OrderSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final ItemRepository itemRepository;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final UserServiceClient userClient;

    public OrderResponseDto create(OrderCreateRequestDto dto,  String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is missing for the authenticated user");
        }
        UserInfoDto user = userClient.getByEmail(email);

        Order order = new Order();
        order.setUserId(user.id());
        order.setStatus(OrderStatus.NEW);
        syncItems(order, aggregate(dto.items()));

        Order saved = orderRepository.save(order);
        return orderMapper.toDto(saved).withUser(user);
    }

    public OrderResponseDto getById(Long id) {
        Order order = findOrder(id);
        return orderMapper.toDto(order).withUser(fetchUser(order.getUserId()));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponseDto> getAll(LocalDateTime from, LocalDateTime to,
                                         Set<OrderStatus> statuses, Pageable pageable) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }

        Page<Order> page = orderRepository.findAll(
                OrderSpecifications.filter(from, to, statuses), pageable);

        Map<Long, UserInfoDto> users = new HashMap<>();
        page.forEach(o -> users.computeIfAbsent(o.getUserId(), this::fetchUser));

        return page.map(o -> orderMapper.toDto(o).withUser(users.get(o.getUserId())));
    }

    public List<OrderResponseDto> getByUserId(Long userId) {
        List<Order> orders = orderRepository.findAllByUserId(userId);
        if (orders.isEmpty()) {
            return List.of();
        }
        UserInfoDto user = fetchUser(userId);
        return orders.stream().map(o -> orderMapper.toDto(o).withUser(user)).toList();
    }

    @Transactional
    public OrderResponseDto update(Long id, OrderUpdateRequestDto dto) {
        Order order = findOrder(id);

        if (dto.items() != null && order.getStatus() != OrderStatus.NEW) {
            throw new OrderNotEditableException(id, order.getStatus());
        }
        if (dto.status() != null) {
            if (!order.getStatus().canTransitionTo(dto.status())) {
                throw new InvalidOrderStatusTransitionException(order.getStatus(), dto.status());
            }
            orderMapper.updateEntity(dto, order);
        }
        if (dto.items() != null) {
            syncItems(order, aggregate(dto.items()));
        }

        Order saved = orderRepository.save(order);
        return orderMapper.toDto(saved).withUser(fetchUser(saved.getUserId()));
    }

    @Transactional
    public void delete(Long id) {
        if (orderRepository.softDeleteById(id) == 0) {
            throw new OrderNotFoundException(id);
        }
    }

    @Transactional
    public void applyPaymentResult(Long orderId, PaymentStatus paymentStatus) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Order {} not found for payment event", orderId);
            return;
        }

        OrderStatus current = order.getStatus();
        if (current != OrderStatus.NEW && current != OrderStatus.PENDING_PAYMENT) {
            log.warn("Ignoring payment event for order {} in status {}", orderId, current);
            return;
        }

        if (current == OrderStatus.NEW) {
            order.setStatus(OrderStatus.PENDING_PAYMENT);
        }
        if (paymentStatus == PaymentStatus.SUCCESS) {
            order.setStatus(OrderStatus.PAID);
        }
    }

    private Order findOrder(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    private UserInfoDto fetchUser(Long userId) {
        try {
            return userClient.getById(userId);
        } catch (UserNotFoundException e) {
            return UserInfoDto.unavailable(userId);
        }
    }

    private Map<Long, OrderItemRequestDto> aggregate(List<OrderItemRequestDto> items) {
        return items.stream().collect(Collectors.toMap(
                OrderItemRequestDto::itemId,
                Function.identity(),
                (a, b) ->
                        new OrderItemRequestDto(a.itemId(), a.quantity() + b.quantity()),
                LinkedHashMap::new));
    }

    private void syncItems(Order order, Map<Long, OrderItemRequestDto> requested) {
        Map<Long, Item> items = new HashMap<>();
        itemRepository.findAllById(requested.keySet()).forEach(i -> items.put(i.getId(), i));
        requested.keySet().forEach(itemId -> {
            if (!items.containsKey(itemId)) {
                throw new ItemNotFoundException(itemId);
            }
        });

        Map<Long, OrderItem> existing = order.getOrderItems().stream()
                .collect(Collectors.toMap(oi -> oi.getItem().getId(), Function.identity()));

        order.getOrderItems().removeIf(oi -> !requested.containsKey(oi.getItem().getId()));

        requested.forEach((itemId, req) -> {
            OrderItem current = existing.get(itemId);
            if (current != null) {
                current.setQuantity(req.quantity());
            } else {
                OrderItem created = orderItemMapper.toEntity(req);
                created.setItem(items.get(itemId));
                order.addItem(created);
            }
        });

        order.setTotalPrice(order.getOrderItems().stream()
                .map(oi -> oi.getItem()
                        .getPrice()
                        .multiply(BigDecimal.valueOf(oi.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
