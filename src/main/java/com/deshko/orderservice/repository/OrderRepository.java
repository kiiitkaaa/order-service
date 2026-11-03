package com.deshko.orderservice.repository;

import com.deshko.orderservice.entity.Order;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    @Override
    @EntityGraph(attributePaths = {"orderItems", "orderItems.item"})
    Optional<Order> findById(Long id);

    @EntityGraph(attributePaths = {"orderItems", "orderItems.item"})
    List<Order> findAllByUserId(Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Order o SET o.deleted = true, o.updatedAt = CURRENT_TIMESTAMP WHERE o.id = :id")
    int softDeleteById(@Param("id") Long id);

    boolean existsByIdAndUserId(Long id, Long userId);
}
