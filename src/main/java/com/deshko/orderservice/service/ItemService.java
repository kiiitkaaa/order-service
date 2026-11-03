package com.deshko.orderservice.service;

import com.deshko.orderservice.dto.ItemRequestDto;
import com.deshko.orderservice.dto.ItemResponseDto;
import com.deshko.orderservice.entity.Item;
import com.deshko.orderservice.exception.ItemInUseException;
import com.deshko.orderservice.exception.ItemNotFoundException;
import com.deshko.orderservice.mapper.ItemMapper;
import com.deshko.orderservice.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ItemService {
    private final ItemRepository itemRepository;
    private final ItemMapper itemMapper;

    public ItemResponseDto create(ItemRequestDto dto) {
        return itemMapper.toDto(itemRepository.save(itemMapper.toEntity(dto)));
    }

    public ItemResponseDto getById(Long id) {
        return itemMapper.toDto(findItem(id));
    }

    @Transactional
    public ItemResponseDto update(Long id, ItemRequestDto dto) {
        Item item = findItem(id);
        itemMapper.updateEntity(dto, item);
        return itemMapper.toDto(itemRepository.save(item));
    }

    @Transactional
    public void delete(Long id) {
        Item item = findItem(id);
        if (itemRepository.isUsedInOrders(id)) {
            throw new ItemInUseException(id);
        }
        itemRepository.delete(item);
    }

    private Item findItem(Long id) {
        return itemRepository.findById(id).orElseThrow(() -> new ItemNotFoundException(id));
    }
}