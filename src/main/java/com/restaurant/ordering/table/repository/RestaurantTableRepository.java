package com.restaurant.ordering.table.repository;
import com.restaurant.ordering.table.entity.RestaurantTable;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {
    List<RestaurantTable> findAllByOrderByNumberAsc();
    Optional<RestaurantTable> findByNumber(int number);
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select t from RestaurantTable t where t.number = :number")
    Optional<RestaurantTable> lockByNumber(int number);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RestaurantTable t where t.id = :id")
    Optional<RestaurantTable> lockById(Long id);
}
