package com.restaurant.ordering.menu.repository;
import com.restaurant.ordering.menu.entity.MenuItem;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    @Query("select m from MenuItem m join fetch m.category c where m.active = true and (:includeUnavailable = true or m.available = true) order by c.sortOrder, c.id, m.id")
    List<MenuItem> publicMenu(boolean includeUnavailable);
    @Query("select m from MenuItem m join fetch m.category c order by c.sortOrder, c.id, m.id")
    List<MenuItem> staffMenu();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MenuItem m where m.id in :ids order by m.id")
    List<MenuItem> lockAllById(Collection<Long> ids);
    boolean existsByCategoryId(Long id);
    Optional<MenuItem> findByName(String name);
}
