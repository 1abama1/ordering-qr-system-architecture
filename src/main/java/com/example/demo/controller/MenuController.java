package com.example.demo.controller;

import com.example.demo.dto.AvailabilityResponse;
import com.example.demo.dto.CreateMenuItemRequest;
import com.example.demo.dto.MenuItemDto;
import com.example.demo.service.MenuService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/menu")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * GET /api/menu          — для клиентов: только блюда в наличии
     * GET /api/menu?all=true — для кухни: все блюда (для панели стоп-листа)
     */
    @GetMapping
    public List<MenuItemDto> getMenu(@RequestParam(defaultValue = "false") boolean all) {
        return menuService.getMenu(all);
    }

    /** GET /api/menu/categories — список категорий для табов на фронте. */
    @GetMapping("/categories")
    public List<String> getCategories() {
        return menuService.getCategories();
    }

    /** POST /api/menu — добавить блюдо. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MenuItemDto create(@Valid @RequestBody CreateMenuItemRequest request) {
        return menuService.create(request);
    }

    /**
     * PATCH /api/menu/{id}/toggle-availability — переключить стоп-лист.
     * Второй путь /toggle — из раздела "Спецификация API" в пайплайне,
     * там написано по-другому. Работают оба, чтобы фронт не сломался.
     */
    @PatchMapping({"/{id}/toggle-availability", "/{id}/toggle"})
    public AvailabilityResponse toggleAvailability(@PathVariable Long id) {
        return menuService.toggleAvailability(id);
    }
}
