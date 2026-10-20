# Environment и эксплуатация

| Переменная | Назначение |
|---|---|
| DB_PASSWORD | Обязательный пароль PostgreSQL |
| DB_URL | JDBC для Maven; внутри Compose задан postgres:5432 |
| DB_USERNAME | ordering по умолчанию |
| JWT_SECRET | Обязательный независимый случайный secret >=32 символа |
| QR_SECRET | Обязательный постоянный secret >=32 символа; потеря/ротация отзывает все QR |
| ALLOWED_ORIGINS | Точные origins через запятую, без wildcard/path |
| GUEST_MENU_URL | Полный HTTP(S) URL страницы меню без query/fragment |
| APP_PROFILE | Для Compose: dev или prod; default prod |
| SPRING_PROFILES_ACTIVE | Для Maven: dev/prod |
| HTTP_PORT | Публичный Nginx порт, default 8080 |
| SERVER_PORT | Для запуска Java на хосте, default 8080 |
| BOOTSTRAP_ADMIN_USERNAME/PASSWORD | Первичное provisioning ADMIN; убрать после создания |

В dev меню/столики создаёт идемпотентный seeder, отключаемый app.demo.enabled=false. Prod профиль исключает этот класс независимо от значения флага. Production не создаёт demo credentials/заказы.

JWT_SECRET и QR_SECRET храните независимо и постоянно в secret storage/ENV. Ротация JWT_SECRET немедленно инвалидирует REST токены и новые CONNECT; существующие WS соединения следует перезапустить. TTL настраивается app.jwt-ttl-minutes (1..60). Отзыв QR одного стола повышает qrTokenVersion в БД. QR можно сфотографировать и переслать; он подтверждает владение ссылкой, а не физическое присутствие. Лимиты и отзыв уменьшают риск, но не заменяют присутствие/персонал.

## Reverse proxy и TLS

Compose публикует только Nginx. Backend/PostgreSQL доступны внутренней Docker-сети. Не публикуйте backend порт напрямую при prod native forwarded headers. Tomcat доверяет X-Forwarded-* только источникам из private Docker диапазона 172.16/12; для иной сети задайте точный server.tomcat.remoteip.internal-proxies. Nginx перезаписывает X-Forwarded-For, X-Forwarded-Proto и correlation ID, не доверяя клиентским значениям.

Локальный Compose использует HTTP. Перед реальным размещением настройте TLS/домен/сертификаты по infra/nginx/tls.conf.example, HTTPS GUEST_MENU_URL и ALLOWED_ORIGINS. При внешнем TLS ingress завершайте доверие к forwarded headers на контролируемой границе и настройте Nginx для вашей схемы; не принимайте произвольный X-Forwarded-Proto от интернета.

Nginx access log исключает query string, Authorization и body. Backend не логирует QR/JWT/пароли. Не включайте HTTP body/STOMP debug logging на production.

## Сохранность и миграции

Именованный volume postgres-data сохраняется при restart и down. Делайте pg_dump в защищённое хранилище, проверяйте восстановление. Не меняйте применённые Flyway файлы; добавляйте новые V3/V4. ddl-auto=validate не изменяет схему. Не указывайте эту конфигурацию на неизвестную старую БД без отдельной миграции (AUDIT.md).

## Наблюдаемость

Public /actuator/health раскрывает только статус. Другие actuator endpoints не публикуются SecurityFilterChain. Micrometer meters: ordering.order.create (включая lock wait, проверку и сохранение до возврата service; commit выполняет Spring interceptor после метода), ordering.realtime.published и ordering.realtime.failed. Registry остаётся внутренним для MVP; Prometheus/export не подключён.

SLF4J фиксирует orderId, tableNumber, сумму, переходы статусов, availability/version и ошибки публикации. X-Correlation-ID помогает найти HTTP запрос. Async publisher переносит correlation MDC. Hikari pool=15, DB lock_timeout=5s, connection timeout=10s. При lock conflict API возвращает 409 CONCURRENT_UPDATE, повтор заказа сохраняет прежний key.

## Ограничения

Один instance, встроенный недолговечный broker, bounded очередь публикации. Нет durable outbox, гарантированного event replay, refresh tokens, frontend build, оплаты или складского учёта. Rate limiting в приложении локальное, в Nginx по IP: общий NAT может делить лимит. Выберите значения app.order-rate-limit/app.login-rate-limit/app.qr-rate-limit и Nginx rates под ресторан.

Включение/роль staff меняются администрацией БД в текущем MVP; публичного password reset/disable UI нет. Удаление/отключение account влияет на REST немедленно; существующий WS использует права CONNECT до истечения JWT (максимум TTL). Авторизованные JWT храните в памяти клиента, используйте HTTPS.

Production выкладка, DNS/TLS, backup automation и проверка настоящих React приложений на смартфоне/кухонном экране требуют инфраструктуры и frontend команды; автоматического deployment в CI нет.

## Integration tests без Docker

По умолчанию `clean verify` использует Testcontainers PostgreSQL. Для уже подготовленного отдельного тестового сервера можно явно задать:

```powershell
$env:IT_DB_SERVER = 'jdbc:postgresql://127.0.0.1:55432'
$env:IT_DB_USERNAME = '<test user>'
$env:IT_DB_PASSWORD = '<test password>'
.\mvnw.cmd clean verify
```

На этом сервере заранее создайте две **пустые тестовые** базы `ordering_integration` и `ordering_production`. Не используйте рабочие ресторанные базы. Тесты создают настоящие данные и выполняют Flyway; native режим не использует H2 и не имитирует PostgreSQL. Для повторного полного запуска подготовьте пустые базы заново. При отсутствии IT_DB_SERVER используется обычный Testcontainers путь, ошибки Docker не скрываются.