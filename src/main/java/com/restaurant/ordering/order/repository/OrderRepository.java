package com.restaurant.ordering.order.repository;
import com.restaurant.ordering.order.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface OrderRepository extends JpaRepository<Order, Long> {
    @EntityGraph(attributePaths = "items")
    Optional<Order> findByIdempotencyKey(String key);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> lockById(Long id);
    @EntityGraph(attributePaths = "items")
    @Query("select o from Order o where o.id in :ids order by o.createdAt, o.id")
    List<Order> findDetailedByIds(Collection<Long> ids);
    @Query("select o.id from Order o where o.status in :statuses order by o.createdAt, o.id")
    List<Long> activeIds(Collection<OrderStatus> statuses, Pageable page);
}
