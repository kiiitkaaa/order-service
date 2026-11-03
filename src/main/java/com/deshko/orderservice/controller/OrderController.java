package com.deshko.orderservice.controller;

import com.deshko.orderservice.dto.*;
import com.deshko.orderservice.entity.OrderStatus;
import com.deshko.orderservice.security.GatewayUser;
import com.deshko.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponseDto> create(@Valid @RequestBody OrderCreateRequestDto dto,
                                                   @AuthenticationPrincipal GatewayUser user) {
        OrderResponseDto created = orderService.create(dto, user.email());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/me")
    public ResponseEntity<List<OrderResponseDto>> getMine(@AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.ok(orderService.getByUserId(user.id()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @orderAccess.isOwner(#id, authentication)")
    public ResponseEntity<OrderResponseDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(orderService.getById(id));
    }

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId.toString() == authentication.name")
    public ResponseEntity<List<OrderResponseDto>> getByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(orderService.getByUserId(userId));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageResponseDto<OrderResponseDto>> getAll(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) Set<OrderStatus> statuses,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(PageResponseDto.of(
                orderService.getAll(from, to, statuses, pageable)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @orderAccess.isOwner(#id, authentication)")
    public ResponseEntity<OrderResponseDto> update(@PathVariable Long id,
                                                   @Valid @RequestBody OrderUpdateRequestDto dto,
                                                   @AuthenticationPrincipal GatewayUser user) {
        if (!user.isAdmin() && dto.status() != null && dto.status() != OrderStatus.CANCELLED) {
            throw new AccessDeniedException("Only admin can set status " + dto.status());
        }
        return ResponseEntity.ok(orderService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @orderAccess.isOwner(#id, authentication)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        orderService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
