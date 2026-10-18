package com.restaurant.ordering.category.repository;
import com.restaurant.ordering.category.entity.Category;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOrderBySortOrderAscIdAsc();
    Optional<Category> findByName(String name);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Category c where c.id = :id")
    Optional<Category> lockById(Long id);
}
