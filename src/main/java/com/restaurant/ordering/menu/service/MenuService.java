package com.restaurant.ordering.menu.service;
import com.restaurant.ordering.menu.dto.*;
import com.restaurant.ordering.menu.entity.MenuItem;
import com.restaurant.ordering.menu.repository.MenuItemRepository;
import com.restaurant.ordering.category.repository.CategoryRepository;
import com.restaurant.ordering.common.exception.ApiException;
import com.restaurant.ordering.realtime.event.Events;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor @Slf4j
public class MenuService {
    private final MenuItemRepository menu;
    private final CategoryRepository categories;
    private final ApplicationEventPublisher events;
    @Transactional(readOnly = true)
    public List<MenuItemResponse> publicMenu(boolean includeUnavailable) { return menu.publicMenu(includeUnavailable).stream().map(MenuItemResponse::from).toList(); }
    @Transactional(readOnly = true)
    public List<MenuItemResponse> staffMenu() { return menu.staffMenu().stream().map(MenuItemResponse::from).toList(); }
    @Transactional
    public MenuItemResponse create(MenuItemRequest r) {
        var category = categories.findById(r.categoryId()).orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND", "Category does not exist"));
        var m = menu.saveAndFlush(new MenuItem(r.name(), r.description(), r.price(), r.imageUrl(), category, r.isAvailable(), r.isActive()));
        return publish(m, false);
    }
    @Transactional
    public MenuItemResponse update(Long id, MenuItemRequest r) {
        var m = required(id);
        var category = categories.findById(r.categoryId()).orElseThrow(() -> ApiException.notFound("CATEGORY_NOT_FOUND", "Category does not exist"));
        m.update(r.name(), r.description(), r.price(), r.imageUrl(), category, r.isAvailable(), r.isActive()); menu.flush();
        return publish(m, false);
    }
    @Transactional
    public void deactivate(Long id) {
        var m = required(id);
        if (m.isActive()) { m.deactivate(); menu.flush(); publish(m, false); }
    }
    @Transactional
    public MenuItemResponse availability(Long id, boolean available) {
        var m = required(id);
        if (m.isAvailable() == available) return MenuItemResponse.from(m);
        m.setAvailability(available); menu.flush(); return publish(m, true);
    }
    @Transactional
    public MenuItemResponse toggle(Long id) {
        var m = required(id); m.setAvailability(!m.isAvailable()); menu.flush(); return publish(m, true);
    }
    private MenuItem required(Long id) {
        return menu.lockAllById(List.of(id)).stream().findFirst().orElseThrow(() -> ApiException.notFound("MENU_ITEM_NOT_FOUND", "Menu item does not exist"));
    }
    private MenuItemResponse publish(MenuItem m, boolean availabilityOnly) {
        var result = MenuItemResponse.from(m); events.publishEvent(Events.menuChanged(result, availabilityOnly));
        log.info("Menu updated menuItemId={} available={} active={} version={}", m.getId(), m.isAvailable(), m.isActive(), m.getVersion()); return result;
    }
}
