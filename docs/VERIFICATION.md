# Проверка реализации

Дата: 19 октября 2026, Asia/Omsk. Feature branch: feature/restaurant-backend-mvp. Main не изменялась.

## Итоговые проверки без Docker

Окружение: Windows, Java 21.0.12, Maven Wrapper 3.9.14, отдельный PostgreSQL 17.6 на loopback:55432. Рабочая установленная служба PostgreSQL не изменялась. После просьбы пользователя Docker больше не использовался.

| Проверка | Реальный результат |
|---|---|
| mvnw.cmd -B -o clean verify в рабочем проекте | BUILD SUCCESS |
| Unit tests | 18 passed, 0 failures/errors/skipped |
| BackendIT | 16 passed, 0 failures/errors/skipped |
| ProductionIT | 2 passed, 0 failures/errors/skipped |
| Всего | 36 passed, без пропусков |
| Flyway / Hibernate validate | V1/V2 применены, схема валидна |
| HTTP security | 401/403, роли, invalid/expired JWT, disabled staff проверены |
| KDS/JWT expiry | Истёкший JWT прекращает доставку в уже открытую подписку |
| Идемпотентность | 6 параллельных HTTP попыток с одним ключом создают один заказ; иной body →409 |
| Concurrency stop-list | Оба порядка commit проверены настоящими конкурирующими транзакциями и DB locks |
| Rollback | Ни заказа, ни STOMP события после rollback |
| KDS fetch | Не более 2 SQL statements при загрузке active с items |
| Reconnect | Подписка/receipt + REST возвращают пропущенный заказ |
| Native JAR | Запускается, health=UP, Swagger/OpenAPI HTTP 200 |
| Production profile | Demo не запускается даже с dev+prod; Swagger возвращает 404 |

Полный запуск выполнен с IT_DB_SERVER / IT_DB_USERNAME / IT_DB_PASSWORD, указывающими на две пустые временные тестовые БД. По умолчанию тестовый код сохраняет Testcontainers PostgreSQL. Ранее 14 integration tests были успешно выполнены с Testcontainers; финальная расширенная suite выполнена на native PostgreSQL после отключения Docker по указанию пользователя.

## Измерения финального запуска

- HTTP create → получение NEW_ORDER по настоящему WebSocket: **81 мс**.
- 20 одновременно подключённых STOMP клиентов, 30 реальных заказов, 8 параллельных HTTP workers: **p50 56 мс, p95 96 мс, max 105 мс**.
- Лимиты rate limiting в load-тесте явно подняты, чтобы измерять обработку, а не 429. Это локальный smoke, не оценка Internet latency или производственная гарантия.

## Сквозной native smoke

scripts/smoke.ps1 выполнен против отдельного запущенного JAR и настоящей demo PostgreSQL:

- QR столика 3 разрешён, PNG получен.
- Два лагмана по 320 и чай за 80: серверный итог **720 KGS**.
- Сохранён заказ id=2, KDS получил NEW_ORDER за **66 мс**.
- Replay того же ключа вернул исходный заказ.
- COOKING и COMPLETED сохранены и получены по STOMP; completed исключён из active.
- Stop-list отправлен гостю; новый заказ с лагманом отклонён 409; наличие восстановлено.
- Admin CRUD меню/категорий, деактивация и запрет удаления используемой категории проверены HTTP.
- Создание/деактивация дополнительного столика проверены HTTP.
- Anonymous KDS REST вернул 401.

Демо содержит 2 заказа: один был сохранён во время отладки PowerShell smoke, второй — окончательный успешный сценарий. История не удалялась. Цены демонстрационные, не принадлежат конкретному ресторану.

После остановки JAR и restart отдельного PostgreSQL кластера, затем повторного старта JAR:

- Количества orders/items/menu/tables/categories до и после: **2 / 4 / 16 / 11 / 5**.
- Заказ 2 сохранил COMPLETED и сумму 720.
- QR token не изменился.
- Повтор того же Idempotency-Key вернул HTTP 200 / Idempotency-Replayed=true.
- Seeder не продублировал demo данные.
- Health и Swagger снова HTTP 200; OpenAPI POST orders включает 201 и 200.

Локальные артефакты: target/ordering-1.0.0-SNAPSHOT.jar, target/surefire-reports, target/failsafe-reports, .local-final-verify.log, .local/smoke.json, .local/restart-verification.json, .local/table-3.png. Секреты и эти временные файлы не коммитятся.

## Docker / CI: что не подтверждено

- docker compose config --quiet — успешно.
- Multi-stage Docker image был успешно собран **до последних изменений**.
- docker compose up -d --build не смог создать PostgreSQL container: Docker filesystem стал read-only при исчерпании C:.
- docker compose ps -a тогда не показал работающий стек.
- Финальный Docker image, healthchecks Compose, Nginx HTTP/WS и сохранность named volume в работающем Compose **не проверены**.
- После просьбы «не используй пока что докер» повторные Docker команды не выполнялись.
- GitHub Actions workflow подготовлен. Текущая публикация использует [skip ci], чтобы пока не запускать Docker через Testcontainers в CI; CI success не заявляется.

## Оставшаяся приёмка

1. Когда Docker снова будет разрешён и доступен: config, up -d --build, ps, smoke через Nginx, restart и повторная проверка данных.
2. Запустить GitHub Actions без [skip ci] и подтвердить runner/Testcontainers path финальной версии.
3. Подключить реальные React Guest/KDS builds, настроить HTTPS/origins/GUEST_MENU_URL и проверить физический смартфон/кухонный экран.
4. Для реального сервера подготовить секреты, DNS/TLS, backup/restore и необходимые меню/столики.

Backend реализован и прошёл native проверки. Полная production/Compose приёмка не объявляется завершённой.
