package com.restaurant.ordering.category.service;
import com.restaurant.ordering.category.dto.*;
import com.restaurant.ordering.category.entity.Category;
import com.restaurant.ordering.category.repository.CategoryRepository;
import com.restaurant.ordering.menu.repository.MenuItemRepository;
import com.restaurant.ordering.common.exception.ApiException;
import com.restaurant.ordering.realtime.event.Events;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categories;
    private final MenuItemRepository menu;
    private final ApplicationEventPublisher events;
    @Transactional(readOnly = true)
    public List<CategoryResponse> list() { return categories.findAllByOrderBySortOrderAscIdAsc().stream().map(CategoryResponse::from).toList(); }
    @Transactional
    public CategoryResponse create(CategoryRequest r) {
        var c = categories.saveAndFlush(new Category(r.name(), r.sortOrder()));
        events.publishEvent(Events.categoriesChanged()); return CategoryResponse.from(c);
    }
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest r) {
        var c = required(id); c.update(r.name(), r.sortOrder()); categories.flush();
        events.publishEvent(Events.categoriesChanged()); return CategoryResponse.from(c);
    }
    @Transactional
    public void delete(Long id) {
        var c = required(id);
        if (menu.existsByCategoryId(id)) throw ApiException.conflict("CATEGORY_IN_USE", "Category has menu items, including inactive ones");
        categories.delete(c); categories.flush(); events.publishEvent(Events.categoriesChanged());
    }
    private Category required(Long id) { return categories.lockById(id).orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND", "Category does not exist")); }
}
