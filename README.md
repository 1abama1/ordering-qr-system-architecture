# Restaurant QR Ordering Backend

Spring Boot 3.5.16 / Java 21, PostgreSQL 17, Flyway, JWT, native WebSocket/STOMP. Валюта — KGS. Один ресторан, один backend instance со встроенным STOMP broker.

## Быстрый запуск

Требуются Docker Engine/Compose. Скопируйте `.env.example` в `.env`. Заполните DB_PASSWORD, независимые JWT_SECRET и QR_SECRET (минимум 32 случайных символа каждый), BOOTSTRAP_ADMIN_USERNAME и BOOTSTRAP_ADMIN_PASSWORD (12+ символов, максимум 72 UTF-8 байта).

PowerShell: безопасная генерация значения секрета:

```powershell
[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Для demo: APP_PROFILE=dev. В production установите APP_PROFILE=prod, HTTPS URL меню и точные ALLOWED_ORIGINS. Dev создаёт 4 категории, 15 блюд и 10 столиков; пароли для demo в коде отсутствуют.

```powershell
docker compose config --quiet
docker compose up -d --build
docker compose ps
Invoke-RestMethod http://localhost:8080/actuator/health
```

Backend доступен через Nginx на [localhost:8080](http://localhost:8080). [Swagger UI](http://localhost:8080/swagger-ui/index.html) и [OpenAPI JSON](http://localhost:8080/v3/api-docs) доступны в dev. В prod отключены. PostgreSQL не публикует порт на хост; данные находятся в named volume. Перезапуск/обычный `docker compose down` их сохраняют. Команда `down -v` удаляет volume и для обычного перезапуска не нужна.

## Локальный Maven

Установите JDK 21. Wrapper скачивает Maven 3.9.14; отдельная установка Maven не требуется.

```powershell
docker compose -f compose.yml -f compose.local.yml up -d postgres
# Задайте DB_PASSWORD, JWT_SECRET, QR_SECRET, BOOTSTRAP_ADMIN_USERNAME,
# BOOTSTRAP_ADMIN_PASSWORD в окружении текущего процесса.
$env:SPRING_PROFILES_ACTIVE = 'dev'
.\mvnw.cmd spring-boot:run
```

На Linux/macOS: `./mvnw spring-boot:run`. Maven не читает `.env` автоматически; Compose читает его. DB_URL по умолчанию — jdbc:postgresql://localhost:5432/ordering, DB_USERNAME — ordering. Чтобы работать с Docker backend одновременно, смените локальный SERVER_PORT.

## Персонал и QR

1. Первый ADMIN создаётся из BOOTSTRAP_ADMIN_USERNAME/PASSWORD. Существующий аккаунт не перезаписывается. После создания уберите эти переменные из deployment.
2. Выполните `POST /api/staff/auth/login`, затем передавайте `Authorization: Bearer <accessToken>`.
3. ADMIN создаёт KITCHEN через `POST /api/staff/users`: `{"username":"kitchen","password":"<свой пароль>","role":"KITCHEN"}`.
4. Получите `GET /api/staff/tables`, затем `GET /api/staff/tables/{id}/qr`. JSON содержит URL и tableToken. Для печати: тот же endpoint с `?format=png`.
5. GUEST_MENU_URL — адрес реального React frontend, например https://restaurant.example.com/menu. QR кодирует этот URL с `?table=3&token=...`.
6. Перевыпуск: `POST /api/staff/tables/{id}/regenerate-token`; прежние QR перестают приниматься.

React приложения находятся вне этого репозитория. Nginx обслуживает API и WS; пример подключения frontend build: [infra/nginx/frontend.conf.example](infra/nginx/frontend.conf.example). Для смартфона укажите доступный по LAN/HTTPS адрес frontend и API; localhost на телефоне означает сам телефон.

## Проверки и демонстрация

```powershell
.\mvnw.cmd clean verify
.\scripts\smoke.ps1
docker compose restart postgres backend
# После восстановления health проверьте сохранённый заказ из .local/smoke.json.
```

`clean verify` запускает unit + integration тесты с настоящей PostgreSQL Testcontainers, HTTP и WebSocket. По умолчанию нужен Docker; его отсутствие завершает проверку ошибкой. Для явно выбранной внешней PostgreSQL доступен native режим из OPERATIONS.md. CI использует GitHub-hosted Ubuntu runner с Docker. `-DskipITs` допустим только для локальной частичной проверки; это не полная verification.

Smoke script использует администратора из локального .env, оформляет два лагмана и чай, проверяет NEW_ORDER, статусы, stop-list, повторный запрос, REST и QR PNG. Он добавляет реальный заказ и тестовую позицию меню; не запускайте его на рабочем ресторане.

Реальные результаты и ограничения: [docs/VERIFICATION.md](docs/VERIFICATION.md).

## Документация

- [API и миграция контрактов](docs/API_CONTRACT.md)
- [STOMP события](docs/WEBSOCKET_EVENTS.md)
- [Интеграция React/Axios](docs/FRONTEND_INTEGRATION.md)
- [Архитектура, ERD и блокировки](docs/ARCHITECTURE.md)
- [Настройки и production](docs/OPERATIONS.md)
- [Аудит исходного проекта](docs/AUDIT.md)
- [Задачи команды](docs/TEAM_TASKS.md)
