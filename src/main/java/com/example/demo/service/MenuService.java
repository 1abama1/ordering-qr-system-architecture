package com.example.demo.service;

import com.example.demo.dto.AvailabilityResponse;
import com.example.demo.dto.CreateMenuItemRequest;
import com.example.demo.dto.MenuItemDto;
import com.example.demo.entity.Category;
import com.example.demo.entity.MenuItem;
import com.example.demo.event.MenuItemAvailabilityChangedEvent;
import com.example.demo.exception.MenuItemNotFoundException;
import com.example.demo.repository.CategoryRepository;
import com.example.demo.repository.MenuItemRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MenuService {

    private final MenuItemRepository menuItemRepository;
    private final CategoryRepository categoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    public MenuService(MenuItemRepository menuItemRepository,
                       CategoryRepository categoryRepository,
                       ApplicationEventPublisher eventPublisher) {
        this.menuItemRepository = menuItemRepository;
        this.categoryRepository = categoryRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * @param includeUnavailable false — для клиентов (только в наличии),
     *                           true  — для кухни (все блюда, включая стоп-лист)
     */
    @Transactional(readOnly = true)
    public List<MenuItemDto> getMenu(boolean includeUnavailable) {
        List<MenuItem> items = includeUnavailable
                ? menuItemRepository.findAllByOrderByCategoryNameAscNameAsc()
                : menuItemRepository.findByAvailableTrueOrderByCategoryNameAscNameAsc();
        return items.stream().map(MenuItemDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<String> getCategories() {
        return categoryRepository.findAll().stream().map(Category::getName).sorted().toList();
    }

    @Transactional
    public MenuItemDto create(CreateMenuItemRequest request) {
        Category category = findOrCreateCategory(request.category().trim());
        MenuItem item = new MenuItem(
                request.name().trim(),
                request.description(),
                request.price(),
                request.imageUrl(),
                category
        );
        if (request.isAvailable() != null) {
            item.setAvailable(request.isAvailable());
        }
        return MenuItemDto.from(menuItemRepository.save(item));
    }

    @Transactional
    public AvailabilityResponse toggleAvailability(Long id) {
        MenuItem item = menuItemRepository.findById(id)
                .orElseThrow(() -> new MenuItemNotFoundException(id));
        boolean nowAvailable = item.toggleAvailability();
        // Сохранение произойдёт автоматически при коммите транзакции.
        eventPublisher.publishEvent(new MenuItemAvailabilityChangedEvent(id, nowAvailable));
        return new AvailabilityResponse(id, nowAvailable);
    }

    public Category findOrCreateCategory(String name) {
        return categoryRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> categoryRepository.save(new Category(name)));
    }
}
