# API contract v1

Base URL: http://localhost:8080; production — ваш HTTPS origin. JSON UTF-8, timestamps ISO-8601 UTC. Денежные значения — JSON numbers с точностью 2 знака, currency=KGS; authoritative итог всегда рассчитывает сервер. Неизвестные JSON поля возвращают 400.

## Endpoints

| Method | Path | Доступ | Ответ |
|---|---|---|---|
| GET | /api/menu?includeUnavailable=false | Public | MenuItem[]; только active; true включает стоп-лист |
| GET | /api/categories | Public | Category[] по sortOrder, id |
| GET | /api/tables/resolve?tableNumber=3&tableToken=... | Public | Table |
| POST | /api/orders | Public + QR | 201 Order; replay 200 |
| POST | /api/staff/auth/login | Public | Token |
| GET | /api/staff/auth/me | ADMIN,KITCHEN | Staff |
| POST | /api/staff/users | ADMIN | 201 Staff |
| GET | /api/orders/active?page=0&size=100 | ADMIN,KITCHEN | Order[] по createdAt,id ASC; size 1..200 |
| GET | /api/orders/{id} | ADMIN,KITCHEN | Order, включая историю |
| PATCH | /api/orders/{id}/status | ADMIN,KITCHEN | Order |
| PATCH | /api/orders/{id}/complete | ADMIN,KITCHEN | Order |
| GET | /api/staff/menu | ADMIN,KITCHEN | Все MenuItem[], включая inactive |
| POST | /api/menu | ADMIN | 201 MenuItem |
| PUT | /api/menu/{id} | ADMIN | MenuItem |
| DELETE | /api/menu/{id} | ADMIN | 204; безопасная деактивация |
| PATCH | /api/menu/{id}/availability | ADMIN,KITCHEN | MenuItem |
| PATCH | /api/menu/{id}/toggle | ADMIN,KITCHEN | MenuItem; compatibility only |
| POST | /api/categories | ADMIN | 201 Category |
| PUT | /api/categories/{id} | ADMIN | Category |
| DELETE | /api/categories/{id} | ADMIN | 204; 409 если есть любые блюда |
| GET | /api/staff/tables | ADMIN | Table[] |
| POST | /api/staff/tables | ADMIN | 201 Table |
| PATCH | /api/staff/tables/{id} | ADMIN | Table |
| GET | /api/staff/tables/{id}/qr | ADMIN | QR metadata |
| GET | /api/staff/tables/{id}/qr?format=png | ADMIN | image/png |
| POST | /api/staff/tables/{id}/regenerate-token | ADMIN | Новые QR metadata |
| GET | /actuator/health | Public | {"status":"UP"} |
| GET | /v3/api-docs; /swagger-ui/index.html | dev | OpenAPI / UI |

Активные заказы выдаются ограниченными страницами. Клиент загружает следующие страницы до пустого массива и периодически повторяет синхронизацию, поскольку активный набор меняется.

## Вход и персонал

POST /api/staff/auth/login:

```json
{"username":"kitchen","password":"<your password>"}
```

```json
{"accessToken":"<JWT>","tokenType":"Bearer","expiresAt":"2026-10-19T00:15:00Z","staff":{"id":2,"username":"kitchen","role":"KITCHEN"}}
```

JWT HS256, issuer restaurant-ordering, audience ordering-staff; срок по умолчанию 15 минут. Для REST — Authorization: Bearer <JWT>; для STOMP — CONNECT header Authorization. Refresh token отсутствует: повторный login. Роль/активность аккаунта проверяются в БД для REST и при STOMP CONNECT. POST /api/staff/users: `{"username":"kitchen","password":"<12+ chars>","role":"KITCHEN"}`; роль ADMIN также допустима. Пароль: <=72 UTF-8 байта.

## Создание заказа

Обязателен header `Idempotency-Key: 60a4c3b8-49f0-4e17-a95a-3c403c3f8806`.

```json
{"tableNumber":3,"tableToken":"<from QR>","items":[{"menuItemId":1,"quantity":2},{"menuItemId":10,"quantity":1}]}
```

Пример ответа (id зависят от БД):

```json
{
  "id":42,"tableNumber":3,"status":"PENDING","totalAmount":720.00,"currency":"KGS",
  "createdAt":"2026-10-19T00:00:00Z","updatedAt":"2026-10-19T00:00:00Z",
  "completedAt":null,"version":0,
  "items":[
    {"menuItemId":1,"menuItemName":"Лагман","quantity":2,"priceAtOrder":320.00,"lineTotal":640.00},
    {"menuItemId":10,"menuItemName":"Чай","quantity":1,"priceAtOrder":80.00,"lineTotal":80.00}
  ]
}
```

Location: /api/orders/42 (GET доступен персоналу), Idempotency-Replayed: false. Повтор того же ключа/содержимого возвращает существующий заказ, включая его текущий статус, header true. Ключи глобальны, нормализуются к lowercase UUID и хранятся столько же, сколько заказ; автоматического TTL нет. Другая корзина или другой столик с тем же ключом — 409. Порядок items не влияет на fingerprint. При retry сохраняйте исходный body. QR снова проверяется: отозванный токен или inactive table не принимается даже для replay.

1..50 уникальных позиций; quantity 1..100. Повтор одного menuItemId — 400 DUPLICATE_MENU_ITEM. Лишний price — 400. Любое отсутствующее/неактивное/недоступное блюдо отклоняет весь заказ. Цена >=0, <=9999999.99; итог <=9999999999.99.

PATCH /api/orders/42/status: `{"status":"COOKING"}`. Допустимы PENDING→COOKING, PENDING→COMPLETED, COOKING→COMPLETED. Статус COMPLETED терминальный. Повтор того же статуса — безопасный no-op. PATCH /complete не требует body.

## Меню и категории

POST/PUT menu body (все Boolean обязательны):

```json
{"name":"Лагман","description":"Лапша с мясом и овощами","price":320.00,"imageUrl":null,"categoryId":1,"isAvailable":true,"isActive":true}
```

Ответ:

```json
{"id":1,"name":"Лагман","description":"Лапша с мясом и овощами","price":320.00,"currency":"KGS","imageUrl":null,"categoryId":1,"isAvailable":true,"isActive":true,"updatedAt":"2026-10-19T00:00:00Z","version":0}
```

name 1..200, description <=4000, imageUrl null или HTTP(S) URL <=2000. PATCH availability: `{"isAvailable":false}`. Повтор того же значения не повышает version и не создаёт событие. Toggle не идемпотентен: потеря ответа и повтор могут снова включить блюдо.

POST/PUT category: `{"name":"Горячие блюда","sortOrder":0}`. Ответ: `{"id":1,"name":"Горячие блюда","sortOrder":0}`. Имена уникальны, sortOrder >=0. Удаление категории с inactive блюдами также запрещено.

## Столики

POST table: `{"number":3}`; номер 1..10000, уникален и после создания неизменяем. PATCH: `{"isActive":false}`.

Table: `{"id":3,"number":3,"isActive":true,"qrTokenVersion":1}`.

QR metadata: `{"url":"https://restaurant.example.com/menu?table=3&token=<SIGNED_TOKEN>","tableToken":"<SIGNED_TOKEN>","tableNumber":3,"qrTokenVersion":1}`.

Гостевой URL использует table/token. REST resolve использует tableNumber/tableToken. Клиент не должен строить или разбирать внутренний формат подписи.

## Ошибки

Content-Type: application/problem+json.

```json
{"type":"about:blank","title":"Conflict","status":409,"detail":"Menu item 1 is unavailable","instance":"/api/orders","code":"MENU_ITEM_UNAVAILABLE","timestamp":"2026-10-19T00:00:00Z","path":"/api/orders","correlationId":"988cc946-9890-46d4-8946-dd42d28f9503"}
```

Validation дополнительно содержит errors: `[{"field":"items[0].quantity","message":"must be greater than or equal to 1"}]`. Ошибочные значения и секреты не возвращаются. Nginx 413/429 возвращает тот же набор обязательных custom полей, без type/title/instance.

| HTTP | Основные code |
|---|---|
| 400 | VALIDATION_ERROR, INVALID_REQUEST, DUPLICATE_MENU_ITEM, INVALID_IDEMPOTENCY_KEY, ORDER_TOTAL_TOO_LARGE |
| 401 | UNAUTHORIZED |
| 403 | FORBIDDEN, INVALID_TABLE_TOKEN |
| 404 | MENU_ITEM_NOT_FOUND, TABLE_NOT_FOUND, ORDER_NOT_FOUND, CATEGORY_NOT_FOUND |
| 409 | MENU_ITEM_UNAVAILABLE, TABLE_INACTIVE, IDEMPOTENCY_CONFLICT, INVALID_ORDER_STATUS_TRANSITION, CATEGORY_IN_USE, DATA_CONFLICT, CONCURRENT_UPDATE |
| 413 | REQUEST_TOO_LARGE |
| 429 | RATE_LIMITED; Retry-After: 60 |
| 500 | INTERNAL_ERROR |

Корреляция: X-Correlation-ID в ответе; допустимый входящий идентификатор — 1..64 латинских букв/цифр/дефисов. За Nginx он заменяется серверным request_id. Request body <=32 KiB. Лимиты приложения: orders 30/min/IP, login 10/min/IP, resolve/WS connect 60/min/IP. Nginx добавляет сглаженные лимиты с burst.

## Миграция исходного API

| Раньше | Сейчас |
|---|---|
| items[].price от клиента | Поле запрещено; цена из БД |
| tableNumber без доказательства | tableNumber + tableToken из QR |
| POST orders без ключа | Обязательный Idempotency-Key UUID |
| POST orders → Entity/200 | DTO/201; replay DTO/200 |
| PATCH status?status=COOKING | PATCH status с JSON {"status":"COOKING"} |
| active/status публичные | Нужен staff Bearer JWT |
| Double, LocalDateTime | BigDecimal, ISO UTC timestamps |
| Неограниченные active orders | Ограниченные страницы page/size |
