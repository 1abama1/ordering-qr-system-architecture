package com.example.demo.repository;

import com.example.demo.entity.MenuItem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    /** Для клиентов: только то, что в наличии. */
    List<MenuItem> findByAvailableTrueOrderByCategoryNameAscNameAsc();

    /** Для кухни: все блюда, включая стоп-лист. */
    List<MenuItem> findAllByOrderByCategoryNameAscNameAsc();
}
