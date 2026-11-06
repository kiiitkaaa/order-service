package com.deshko.orderservice.service;

import com.deshko.orderservice.client.UserServiceClient;
import com.deshko.orderservice.dto.*;
import com.deshko.orderservice.entity.*;
import com.deshko.orderservice.exception.*;
import com.deshko.orderservice.mapper.OrderItemMapper;
import com.deshko.orderservice.mapper.OrderMapper;
import com.deshko.orderservice.repository.ItemRepository;
import com.deshko.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock private OrderRepository orderRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private OrderMapper orderMapper;
    @Mock private OrderItemMapper orderItemMapper;
    @Mock private UserServiceClient userClient;
    @InjectMocks private OrderService orderService;

    private static final LocalDate BIRTH = LocalDate.of(1995, 5, 20);
    private static final UserInfoDto USER = new UserInfoDto(
            1L, "Ivan", "Petrov", "ivan@mail.com", BIRTH);

    @BeforeEach
    void setUp() {
        lenient().when(orderMapper.toDto(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            return new OrderResponseDto(o.getId(), o.getStatus(), o.getTotalPrice(),
                    null, null, List.of(), null);
        });
        lenient().when(orderItemMapper.toEntity(any(OrderItemRequestDto.class))).thenAnswer(inv -> {
            OrderItemRequestDto dto = inv.getArgument(0);
            OrderItem orderItem = new OrderItem();
            orderItem.setQuantity(dto.quantity());
            return orderItem;
        });
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv ->
                inv.getArgument(0));
    }

    @Test
    void create_savesNewOrderWithAggregatedItemsAndTotal() {
        Item keyboard = item(1L, "49.90");
        Item mouse = item(2L, "19.90");
        when(userClient.getByEmail("ivan@mail.com")).thenReturn(USER);
        when(itemRepository.findAllById(any())).thenReturn(List.of(keyboard, mouse));

        OrderCreateRequestDto dto = new OrderCreateRequestDto(List.of(
                new OrderItemRequestDto(1L, 1),
                new OrderItemRequestDto(1L, 2),
                new OrderItemRequestDto(2L, 1)));

        OrderResponseDto result = orderService.create(dto, "ivan@mail.com");

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(saved.getOrderItems()).extracting(OrderItem::getQuantity).containsExactly(3, 1);
        assertThat(saved.getTotalPrice()).isEqualByComparingTo("169.60");
        assertThat(result.user()).isEqualTo(USER);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void create_withoutEmail_throwsAndDoesNotCallAnything(String email) {
        OrderCreateRequestDto dto = new OrderCreateRequestDto(List.of(new OrderItemRequestDto(1L, 1)));

        assertThatThrownBy(() -> orderService.create(dto, email))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(userClient, itemRepository, orderRepository);
    }

    @Test
    void create_unknownItem_throwsItemNotFoundAndDoesNotSave() {
        when(userClient.getByEmail("ivan@mail.com")).thenReturn(USER);
        when(itemRepository.findAllById(any())).thenReturn(List.of(item(1L, "49.90")));
        OrderCreateRequestDto dto = new OrderCreateRequestDto(List.of(
                new OrderItemRequestDto(1L, 1), new OrderItemRequestDto(99L, 1)));

        assertThatThrownBy(() -> orderService.create(dto, "ivan@mail.com"))
                .isInstanceOf(ItemNotFoundException.class)
                .hasMessageContaining("99");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void create_userNotFound_propagatesAndSkipsEverythingElse() {
        when(userClient.getByEmail("ghost@mail.com")).thenThrow(new UserNotFoundException("ghost@mail.com"));
        OrderCreateRequestDto dto = new OrderCreateRequestDto(List.of(new OrderItemRequestDto(1L, 1)));

        assertThatThrownBy(() -> orderService.create(dto, "ghost@mail.com"))
                .isInstanceOf(UserNotFoundException.class);

        verifyNoInteractions(itemRepository, orderRepository);
    }

    @Test
    void create_userServiceUnavailable_propagates() {
        when(userClient.getByEmail("ivan@mail.com"))
                .thenThrow(new UserServiceUnavailableException(new RuntimeException("down")));
        OrderCreateRequestDto dto = new OrderCreateRequestDto(List.of(new OrderItemRequestDto(1L, 1)));

        assertThatThrownBy(() -> orderService.create(dto, "ivan@mail.com"))
                .isInstanceOf(UserServiceUnavailableException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void getById_returnsOrderWithUser() {
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order(5L, 1L, OrderStatus.NEW)));
        when(userClient.getById(1L)).thenReturn(USER);

        OrderResponseDto result = orderService.getById(5L);

        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.user()).isEqualTo(USER);
    }

    @Test
    void getById_unknownOrder_throwsNotFound() {
        when(orderRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getById(5L)).isInstanceOf(OrderNotFoundException.class);

        verifyNoInteractions(userClient);
    }

    @Test
    void getById_userDeletedInUserService_returnsPlaceholderUser() {
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order(5L, 1L, OrderStatus.NEW)));
        when(userClient.getById(1L)).thenThrow(new UserNotFoundException("1"));

        OrderResponseDto result = orderService.getById(5L);

        assertThat(result.user()).isEqualTo(UserInfoDto.unavailable(1L));
    }

    @Test
    void getAll_loadsEachDistinctUserOnlyOnce() {
        Pageable pageable = PageRequest.of(0, 10);
        List<Order> orders = List.of(
                order(1L, 1L, OrderStatus.NEW), order(2L, 1L,
                        OrderStatus.NEW), order(3L, 2L, OrderStatus.NEW));
        when(orderRepository.findAll(ArgumentMatchers.<Specification<Order>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(orders, pageable, 3));
        when(userClient.getById(1L)).thenReturn(USER);
        when(userClient.getById(2L)).thenReturn(new UserInfoDto(2L,
                "Anna", "Ivanova", "anna@mail.com", BIRTH));

        Page<OrderResponseDto> result = orderService.getAll(null, null, Set.of(OrderStatus.NEW), pageable);

        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent()).extracting(r ->
                r.user().id()).containsExactly(1L, 1L, 2L);
        verify(userClient, times(1)).getById(1L);
        verify(userClient, times(1)).getById(2L);
    }

    @Test
    void getAll_fromAfterTo_throwsBeforeQuerying() {
        LocalDateTime now = LocalDateTime.now();

        assertThatThrownBy(() -> orderService.getAll(now, now.minusDays(1),
                null, PageRequest.of(0, 10)))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(orderRepository, userClient);
    }

    @Test
    void getByUserId_noOrders_returnsEmptyWithoutCallingUserService() {
        when(orderRepository.findAllByUserId(1L)).thenReturn(List.of());

        assertThat(orderService.getByUserId(1L)).isEmpty();

        verifyNoInteractions(userClient);
    }

    @Test
    void getByUserId_returnsOrdersAndFetchesUserOnce() {
        when(orderRepository.findAllByUserId(1L)).thenReturn(
                List.of(order(1L, 1L, OrderStatus.NEW), order(2L, 1L, OrderStatus.PAID)));
        when(userClient.getById(1L)).thenReturn(USER);

        List<OrderResponseDto> result = orderService.getByUserId(1L);

        assertThat(result).hasSize(2).allSatisfy(r -> assertThat(r.user()).isEqualTo(USER));
        verify(userClient, times(1)).getById(1L);
    }

    @Test
    void update_validStatusTransition_appliesStatus() {
        Order order = order(5L, 1L, OrderStatus.NEW);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(userClient.getById(1L)).thenReturn(USER);
        OrderUpdateRequestDto dto = new OrderUpdateRequestDto(OrderStatus.PENDING_PAYMENT, null);

        orderService.update(5L, dto);

        verify(orderMapper).updateEntity(dto, order);
        verify(orderRepository).save(order);
    }

    @Test
    void update_items_updatesExistingAddsNewRemovesMissingAndRecalculatesTotal() {
        Item keyboard = item(1L, "49.90");
        Item mouse = item(2L, "19.90");
        Item monitor = item(3L, "189.00");
        OrderItem keyboardLine = orderItem(keyboard);
        OrderItem mouseLine = orderItem(mouse);
        Order order = order(5L, 1L, OrderStatus.NEW, keyboardLine, mouseLine);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(itemRepository.findAllById(any())).thenReturn(List.of(keyboard, monitor));
        when(userClient.getById(1L)).thenReturn(USER);

        orderService.update(5L, new OrderUpdateRequestDto(null, List.of(
                new OrderItemRequestDto(1L, 5), new OrderItemRequestDto(3L, 1))));

        assertThat(order.getOrderItems()).extracting(oi -> oi.getItem().getId())
                .containsExactlyInAnyOrder(1L, 3L);
        assertThat(keyboardLine.getQuantity()).isEqualTo(5);
        assertThat(order.getTotalPrice()).isEqualByComparingTo("438.50");
        verify(orderMapper, never()).updateEntity(any(), any());
    }

    @Test
    void update_itemsOfNotNewOrder_throwsNotEditable() {
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order(5L, 1L, OrderStatus.PAID)));
        OrderUpdateRequestDto dto = new OrderUpdateRequestDto(null,
                List.of(new OrderItemRequestDto(1L, 1)));

        assertThatThrownBy(() -> orderService.update(5L, dto)).isInstanceOf(OrderNotEditableException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void update_unknownOrder_throwsNotFound() {
        when(orderRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.update(5L,
                new OrderUpdateRequestDto(OrderStatus.CANCELLED, null)))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void delete_existingOrder_softDeletes() {
        when(orderRepository.softDeleteById(5L)).thenReturn(1);

        orderService.delete(5L);

        verify(orderRepository).softDeleteById(5L);
    }

    @Test
    void delete_unknownOrder_throwsNotFound() {
        when(orderRepository.softDeleteById(5L)).thenReturn(0);

        assertThatThrownBy(() -> orderService.delete(5L)).isInstanceOf(OrderNotFoundException.class);
    }

    @ParameterizedTest
    @CsvSource({
            "NEW, SUCCESS, PAID",
            "NEW, FAILED, PENDING_PAYMENT",
            "PENDING_PAYMENT, SUCCESS, PAID",
            "PENDING_PAYMENT, FAILED, PENDING_PAYMENT"})
    void applyPaymentResult_updatesOrderStatus(OrderStatus before, PaymentStatus payment, OrderStatus expected) {
        Order order = order(5L, 1L, before);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        orderService.applyPaymentResult(5L, payment);

        assertThat(order.getStatus()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "PAID, SUCCESS", "PAID, FAILED",
            "CANCELLED, SUCCESS", "CANCELLED, FAILED"})
    void applyPaymentResult_orderInFinalStatus_isIgnored(OrderStatus status, PaymentStatus payment) {
        Order order = order(5L, 1L, status);
        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));

        orderService.applyPaymentResult(5L, payment);

        assertThat(order.getStatus()).isEqualTo(status);
        verifyNoInteractions(userClient);
    }

    @Test
    void applyPaymentResult_unknownOrder_doesNothingAndDoesNotThrow() {
        when(orderRepository.findById(5L)).thenReturn(Optional.empty());

        assertThatCode(() -> orderService.applyPaymentResult(5L, PaymentStatus.SUCCESS))
                .doesNotThrowAnyException();

        verifyNoMoreInteractions(orderRepository);
        verifyNoInteractions(userClient);
    }

    private static Item item(Long id, String price) {
        Item item = new Item();
        item.setId(id);
        item.setName("item-" + id);
        item.setPrice(new BigDecimal(price));
        return item;
    }

    private static OrderItem orderItem(Item item) {
        OrderItem orderItem = new OrderItem();
        orderItem.setItem(item);
        orderItem.setQuantity(1);
        return orderItem;
    }

    private static Order order(Long id, Long userId, OrderStatus status, OrderItem... lines) {
        Order order = new Order();
        order.setId(id);
        order.setUserId(userId);
        order.setStatus(status);
        for (OrderItem line : lines) {
            order.addItem(line);
        }
        return order;
    }
}
