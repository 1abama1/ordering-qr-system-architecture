package com.example.demo.config;

import com.example.demo.entity.Category;
import com.example.demo.entity.MenuItem;
import com.example.demo.repository.MenuItemRepository;
import com.example.demo.service.MenuService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Тестовые данные: 15 блюд в 3 категориях.
 * Запускается при старте приложения и только если таблица меню пустая,
 * поэтому повторный запуск ничего не задублирует.
 */
@Component
public class MenuDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MenuDataSeeder.class);

    private final MenuItemRepository menuItemRepository;
    private final MenuService menuService;

    public MenuDataSeeder(MenuItemRepository menuItemRepository, MenuService menuService) {
        this.menuItemRepository = menuItemRepository;
        this.menuService = menuService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (menuItemRepository.count() > 0) {
            return;
        }

        Category drinks = menuService.findOrCreateCategory("Напитки");
        Category hot = menuService.findOrCreateCategory("Горячее");
        Category desserts = menuService.findOrCreateCategory("Десерты");

        // Горячее
        add("Плов по-узбекски", "Рис, говядина, морковь, нут, чеснок", 350, hot);
        add("Лагман", "Домашняя лапша, говядина, овощи", 320, hot);
        add("Манты", "5 шт., говядина с луком, подаются со сметаной", 300, hot);
        add("Бешбармак", "Отварное мясо, домашняя лапша, бульон", 450, hot);
        add("Куурдак", "Обжаренная говядина с картофелем и луком", 380, hot);
        add("Шашлык из курицы", "Куриное бедро на мангале, лук, лаваш", 290, hot);

        // Напитки
        add("Чай чёрный", "Чайник 0,8 л", 100, drinks);
        add("Чай зелёный", "Чайник 0,8 л", 100, drinks);
        add("Компот домашний", "Из сухофруктов, 0,5 л", 120, drinks);
        add("Максым", "Традиционный злаковый напиток, 0,5 л", 90, drinks);
        add("Американо", "Кофе, 250 мл", 150, drinks);

        // Десерты
        add("Чак-чак", "Тесто в медовом сиропе", 180, desserts);
        add("Медовик", "Медовый торт со сметанным кремом", 220, desserts);
        add("Чизкейк", "Классический нью-йорк", 250, desserts);
        add("Мороженое", "3 шарика: ваниль, шоколад, клубника", 160, desserts);

        log.info("Меню заполнено тестовыми данными: {} блюд", menuItemRepository.count());
    }

    private void add(String name, String description, int price, Category category) {
        // Заглушка-картинка с названием блюда. Потом заменить на реальные фото.
        String imageUrl = "https://placehold.co/400x300?text=" + name.replace(' ', '+');
        menuItemRepository.save(new MenuItem(name, description, BigDecimal.valueOf(price), imageUrl, category));
    }
}
