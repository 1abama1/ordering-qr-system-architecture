# Модуль «Меню и Стоп-лист» (Эрбол)

## Что внутри

Код разложен по слоям проекта `com.example.demo`, чужие файлы не изменены.

```
config/MenuDataSeeder.java          15 блюд: Напитки, Горячее, Десерты
controller/MenuController.java       REST API
controller/MenuExceptionHandler.java 404 / 400 только для MenuController
dto/MenuItemDto, CreateMenuItemRequest, AvailabilityResponse
entity/Category, MenuItem
event/MenuItemAvailabilityChangedEvent   событие для WebSocket
exception/MenuItemNotFoundException
repository/CategoryRepository, MenuItemRepository
service/MenuService
test: controller/MenuControllerTest  5 интеграционных тестов
```

## API

| Метод | Путь | Что делает |
|---|---|---|
| GET | `/api/menu` | блюда в наличии (для клиентов) |
| GET | `/api/menu?all=true` | все блюда, включая стоп-лист (для кухни) |
| GET | `/api/menu/categories` | список категорий для табов |
| POST | `/api/menu` | добавить блюдо, ответ 201 |
| PATCH | `/api/menu/{id}/toggle-availability` | переключить стоп-лист |
| PATCH | `/api/menu/{id}/toggle` | то же самое (так написано в разделе «Спецификация API») |

Пример блюда в ответе GET:
```json
{ "id": 1, "name": "Плов по-узбекски", "description": "...", "price": 350.00,
  "imageUrl": "https://placehold.co/...", "categoryId": 2, "category": "Горячее", "isAvailable": true }
```

Тело POST:
```json
{ "name": "Самса", "description": "С говядиной", "price": 120, "imageUrl": null, "category": "Выпечка" }
```
Если категории нет, она создастся.

Ответ PATCH: `{ "id": 1, "isAvailable": false }`

## Как подключить WebSocket (для Шаха)

Модуль меню не зависит от WebSocket. При каждом переключении стоп-листа он публикует Spring-событие
`MenuItemAvailabilityChangedEvent { type: "MENU_ITEM_AVAILABILITY_CHANGED", id, isAvailable }`.
Чтобы оно ушло на клиентов, достаточно в твоём коде добавить:

```java
@Component
public class MenuWebSocketBridge {
    private final SimpMessagingTemplate ws;
    public MenuWebSocketBridge(SimpMessagingTemplate ws) { this.ws = ws; }

    @TransactionalEventListener // отправит только после успешного сохранения в БД
    public void on(MenuItemAvailabilityChangedEvent e) {
        ws.convertAndSend("/topic/menu/updates", e);
    }
}
```

## Зависимости

Нужны spring-boot-starter-web, -data-jpa, -validation и драйвер БД. Таблицы `categories` и `menu_items` создаются через `ddl-auto=update`.
