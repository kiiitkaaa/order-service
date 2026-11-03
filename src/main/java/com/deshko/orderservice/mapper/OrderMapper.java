package com.deshko.orderservice.mapper;

import com.deshko.orderservice.dto.OrderResponseDto;
import com.deshko.orderservice.dto.OrderUpdateRequestDto;
import com.deshko.orderservice.entity.Order;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring", uses = OrderItemMapper.class)
public interface OrderMapper {
    @Mapping(target = "items", source = "orderItems")
    @Mapping(target = "user", ignore = true)
    OrderResponseDto toDto(Order order);

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "status", source = "status")
    void updateEntity(OrderUpdateRequestDto dto, @MappingTarget Order order);
}
