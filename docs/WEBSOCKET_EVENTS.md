# WebSocket / STOMP v1

Native WebSocket endpoint: ws://localhost:8080/ws; production wss://<host>/ws. SockJS не используется. Протокол STOMP 1.2, heartbeat 10000/10000 ms, автоматический reconnect на клиенте.

CONNECT без JWT разрешён для гостевого menu channel. Персонал передаёт STOMP header `Authorization: Bearer <JWT>`; браузерный HTTP handshake не требует этого заголовка. JWT в URL не передавайте. Origin проверяется по точному списку ALLOWED_ORIGINS.

| Destination | SUBSCRIBE |
|---|---|
| /topic/menu/updates | Public |
| /topic/kitchen/orders | ADMIN/KITCHEN |
| Любые другие | Запрещены |

Все клиентские SEND, BEGIN/COMMIT/ABORT и ACK/NACK запрещены. Используйте REST для изменений. Нарушение прав возвращает STOMP ERROR/закрытие соединения. Для SUBSCRIBE с header receipt сервер возвращает RECEIPT **после** регистрации подписки во встроенном broker; это дополнительный server interceptor, а не гарантия доставки будущих событий. Остальные receipts/acks не реализованы.

JWT проверяется при CONNECT. Просроченные staff сессии не получают исходящие данные и не могут выполнять новые подписки. Изменение роли/disabled в БД учитывается при следующем CONNECT; до истечения короткого JWT текущая WS сессия сохраняет прежнюю роль. При истечении staff JWT выполняйте повторный login и reconnect; silent refresh отсутствует.

## NEW_ORDER

```json
{"eventId":"f463574b-cfd6-48e8-9e56-1de24e777dda","type":"NEW_ORDER","occurredAt":"2026-10-19T00:00:00Z","orderId":42,"order":{"id":42,"tableNumber":3,"status":"PENDING","totalAmount":720.00,"currency":"KGS","createdAt":"2026-10-19T00:00:00Z","updatedAt":"2026-10-19T00:00:00Z","completedAt":null,"version":0,"items":[{"menuItemId":1,"menuItemName":"Лагман","quantity":2,"priceAtOrder":320.00,"lineTotal":640.00},{"menuItemId":10,"menuItemName":"Чай","quantity":1,"priceAtOrder":80.00,"lineTotal":80.00}]}}
```

## ORDER_STATUS_CHANGED

```json
{"eventId":"5c32e83f-cb35-42e1-93e6-22ead0a90ef7","type":"ORDER_STATUS_CHANGED","occurredAt":"2026-10-19T00:01:00Z","orderId":42,"previousStatus":"COOKING","newStatus":"COMPLETED","completedAt":"2026-10-19T00:01:00Z","version":2,"order":{"id":42,"tableNumber":3,"status":"COMPLETED","totalAmount":720.00,"currency":"KGS","createdAt":"2026-10-19T00:00:00Z","updatedAt":"2026-10-19T00:01:00Z","completedAt":"2026-10-19T00:01:00Z","version":2,"items":[]}}
```

В настоящем событии order содержит все items; пустой массив выше сокращает пример.

## MENU_ITEM_AVAILABILITY_CHANGED

```json
{"eventId":"58f5f607-3dfd-4ccb-9fb9-9b046ac502f9","type":"MENU_ITEM_AVAILABILITY_CHANGED","occurredAt":"2026-10-19T00:02:00Z","menuItemId":1,"isAvailable":false,"isActive":true,"updatedAt":"2026-10-19T00:02:00Z","version":3}
```

MENU_CHANGED имеет те же поля и отправляется при создании/редактировании/деактивации блюда. CATEGORIES_CHANGED имеет eventId/type/occurredAt, остальные поля null; перечитайте категории и меню. Повторная установка той же availability, повторное complete и replay заказа не создают события.

## Доставка и восстановление

Событие становится доступным для публикации только после commit. Rollback не отправляет событие. Публикация через bounded executor; ошибка не меняет результат сохранения. Simple broker и очередь недолговечны: restart/disconnect/переполнение могут привести к потере. Порядок независимых async задач не гарантирован.

Надёжный порядок клиента:

1. CONNECT → SUBSCRIBE с receipt → дождаться RECEIPT (при timeout reconnect).
2. С первого события считать snapshot устаревшим. Во время загрузки ставить dirty=true.
3. После RECEIPT получить все страницы GET /api/orders/active (KDS) или GET /api/menu?includeUnavailable=true и /api/categories (гость).
4. Если во время fetch пришли события, повторить fetch. Хранить заказы по id, а не append в массив.
5. После каждого reconnect повторить последовательность. Дополнительно refetch каждые 15 секунд и при возврате вкладки в foreground: это исправляет пропущенное событие без reconnect.
6. При локальном merge использовать eventId для дедупликации и version для защиты от старого события. Простой рекомендуемый MVP-клиент использует события как invalidation, затем refetch.

WebSocket не является журналом заказов и не обеспечивает exactly-once delivery.
