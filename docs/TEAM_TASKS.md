# Границы модулей и задачи команды

Все backend пути ниже относительны к src/main/java/com/restaurant/ordering. Источник контрактов — API_CONTRACT.md, WEBSOCKET_EVENTS.md и DTO; изменения контрактов согласовывайте до изменения frontend.

| Разработчик | Зона | Файлы и публичный контракт |
|---|---|---|
| Шах | Architecture, integration, security, real-time, DevOps | common/, security/, realtime/, table/qr/, bootstrap/, Dockerfile, compose.yml, infra/, .github/. Staff auth, QR, /ws, after-commit publication |
| Эрбол | Menu, Categories, Stop-list | menu/, category/. MenuService.publicMenu/staffMenu/create/update/deactivate/availability/toggle; CategoryService.list/create/update/delete. MenuItemRequest/Response, availability JSON |
| Алексей | Orders, Items, Statuses | order/. OrderService.create/active/get/status, DTO и PostgreSQL transaction invariants. Idempotency-Key, snapshots, PENDING/COOKING/COMPLETED |
| Марьям | Guest React | GET menu?includeUnavailable=true, categories, tables/resolve; POST orders с QR и UUID key. Public /topic/menu/updates, disabled блюда, восстановление корзины и retries |
| Атай | Kitchen React | login/me, active pages, order details/status/complete; GET staff/menu, PATCH availability. Protected /topic/kitchen/orders; menu updates при stop-list, reconnect snapshot |

## Интеграционные задачи спринта

1. Марьям: прочитать table/token из QR, вызвать resolve, показать table number; idempotency attempt сохранить до POST; server 409 отобразить как обновлённую корзину.
2. Атай: JWT держать в памяти; дождаться SUBSCRIBE receipt, загрузить active pages, refetch при событиях/reconnect/каждые 15 секунд. Карточка key=order.id, завершённые исчезают из snapshot.
3. Эрбол: тестировать UI disabled и admin price/edit отдельно от KITCHEN availability; не удалять историю, не менять @Version/порядок locks без согласования.
4. Алексей: сохранять ordered row locks, advisory idempotency и единый transaction; добавлять бизнес-изменения через сервис с PostgreSQL concurrency tests.
5. Шах: подключить реальные frontend builds/origins, HTTPS домен и GUEST_MENU_URL, распечатать QR, проверить сценарий на смартфоне и кухонном экране; настроить backup и секреты.

Схема V1/V2 уже реализована. Каждый следующий DB change — новый номер миграции, согласованный владельцем интеграции. Не редактируйте чужие применённые migrations и shared DTO без совместного review.

Для минимизации merge conflicts держите UI в своих frontend репозиториях, backend задачи — в feature пакетах. Шах владеет общими config/POM/Compose; тесты можно разделять по именам feature. CI clean verify обязателен перед объединением. Отдельные интерфейсы на каждый CRUD не требуются.
