package com.restaurant.ordering.bootstrap.seed;
import com.restaurant.ordering.category.entity.Category;
import com.restaurant.ordering.category.repository.CategoryRepository;
import com.restaurant.ordering.menu.entity.MenuItem;
import com.restaurant.ordering.menu.repository.MenuItemRepository;
import com.restaurant.ordering.table.entity.RestaurantTable;
import com.restaurant.ordering.table.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Component @Profile("dev & !prod") @ConditionalOnProperty(name="app.demo.enabled", havingValue="true") @RequiredArgsConstructor
public class DemoSeeder implements CommandLineRunner {
    private final CategoryRepository categories;
    private final MenuItemRepository menu;
    private final RestaurantTableRepository tables;
    private final JdbcTemplate jdbc;
    @Override @Transactional
    public void run(String... args) {
        jdbc.queryForList("select pg_advisory_xact_lock(191022)");
        var hot = category("Горячие блюда",0);
        var drinks = category("Напитки",1);
        var desserts = category("Десерты",2);
        var starters = category("Закуски",3);
        item("Лагман","Домашняя лапша с говядиной и овощами", "320.00", hot);
        item("Плов","Рис, морковь и говядина со специями", "280.00", hot);
        item("Манты","Пять мантов с мясом и луком", "300.00", hot);
        item("Шашлык","Говядина на углях с маринованным луком", "450.00", hot);
        item("Куурдак","Жаркое из мяса и картофеля", "420.00", hot);
        item("Бешбармак","Мясо с лапшой и луковым соусом", "390.00", hot);
        item("Самсы","Две печёные самсы с мясом", "140.00", starters);
        item("Греческий салат","Овощи, сыр, оливки и зелень", "240.00", starters);
        item("Картофель фри","Картофель с томатным соусом", "150.00", starters);
        item("Чай","Чёрный чай, чайник 500 мл", "80.00", drinks);
        item("Кофе","Свежесваренный американо 200 мл", "120.00", drinks);
        item("Лимонад","Домашний лимонад с лимоном 400 мл", "160.00", drinks);
        item("Чизкейк","Классический чизкейк с ягодным соусом", "220.00", desserts);
        item("Тирамису","Десерт с кофе и маскарпоне", "250.00", desserts);
        item("Мороженое","Три шарика ванильного мороженого", "130.00", desserts);
        for (int n=1; n<=10; n++) if (tables.findByNumber(n).isEmpty()) tables.save(new RestaurantTable(n));
    }
    private Category category(String name, int sort) { return categories.findByName(name).orElseGet(() -> categories.save(new Category(name,sort))); }
    private void item(String name, String description, String price, Category category) {
        if (menu.findByName(name).isEmpty()) menu.save(new MenuItem(name,description,new BigDecimal(price),null,category,true,true));
    }
}
