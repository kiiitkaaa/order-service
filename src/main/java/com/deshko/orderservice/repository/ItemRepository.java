package com.deshko.orderservice.repository;

import com.deshko.orderservice.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, Long> {
    @Query("SELECT COUNT(oi) > 0 FROM OrderItem oi WHERE oi.item.id = :itemId")
    boolean isUsedInOrders(@Param("itemId") Long itemId);
}
