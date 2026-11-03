package com.deshko.orderservice.service;

import com.deshko.orderservice.dto.*;
import com.deshko.orderservice.entity.Item;
import com.deshko.orderservice.exception.*;
import com.deshko.orderservice.mapper.ItemMapper;
import com.deshko.orderservice.repository.ItemRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {
    @Mock
    private ItemRepository itemRepository;
    @Mock private ItemMapper itemMapper;

    @InjectMocks private ItemService itemService;

    private final ItemRequestDto request = new
            ItemRequestDto("Keyboard", new BigDecimal("49.90"));
    private final ItemResponseDto response =
            new ItemResponseDto(1L, "Keyboard", new BigDecimal("49.90"));

    @Test
    void create_mapsSavesAndReturnsDto() {
        Item entity = new Item();
        Item saved = new Item();
        when(itemMapper.toEntity(request)).thenReturn(entity);
        when(itemRepository.save(entity)).thenReturn(saved);
        when(itemMapper.toDto(saved)).thenReturn(response);

        assertThat(itemService.create(request)).isEqualTo(response);
    }

    @Test
    void getById_returnsItem() {
        Item item = new Item();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemMapper.toDto(item)).thenReturn(response);

        assertThat(itemService.getById(1L)).isEqualTo(response);
    }

    @Test
    void getById_unknown_throwsNotFound() {
        when(itemRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.getById(1L)).isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void update_appliesDtoToExistingItem() {
        Item item = new Item();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemRepository.save(item)).thenReturn(item);
        when(itemMapper.toDto(item)).thenReturn(response);

        assertThat(itemService.update(1L, request)).isEqualTo(response);

        verify(itemMapper).updateEntity(request, item);
    }

    @Test
    void update_unknown_throwsNotFound() {
        when(itemRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.update(1L, request)).isInstanceOf(ItemNotFoundException.class);

        verify(itemRepository, never()).save(any());
    }

    @Test
    void delete_unusedItem_deletes() {
        Item item = new Item();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemRepository.isUsedInOrders(1L)).thenReturn(false);

        itemService.delete(1L);

        verify(itemRepository).delete(item);
    }

    @Test
    void delete_itemUsedInOrders_throwsConflictAndKeepsItem() {
        when(itemRepository.findById(1L)).thenReturn(Optional.of(new Item()));
        when(itemRepository.isUsedInOrders(1L)).thenReturn(true);

        assertThatThrownBy(() -> itemService.delete(1L)).isInstanceOf(ItemInUseException.class);

        verify(itemRepository, never()).delete(any());
    }

    @Test
    void delete_unknown_throwsNotFound() {
        when(itemRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> itemService.delete(1L)).isInstanceOf(ItemNotFoundException.class);
    }
}
