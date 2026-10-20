# Аудит исходного состояния

База: main, commit f3fcfebf31b097724b16f99a90bbc1b5fb828bad. Проверка remote main подтвердила тот же SHA. Изменения выполняются в feature/restaurant-backend-mvp; main не изменялась.

Исходно: 7 Java production-файлов и один пустой context test. Единственный commit вводил базовые Order/OrderItem, repository и controller. Дополнительных AGENTS.md не найдено.

## Воспроизведённые проблемы

- mvnw.cmd clean verify останавливался в Windows wrapper: обращение к null Target[0] обычного .m2 каталога.
- Запуск установленным Maven прошёл компиляцию, но contextLoads упал: Failed to determine a suitable driver class, конфигурации datasource не было.
- Boot 4.1.1 и Boot 4 webmvc/test starters не соответствовали требуемому Boot 3.5.
- Бизнес-логика в controller; прямое доверие items[].price, double arithmetic.
- Не было доменных сущностей меню/категорий/столиков/персонала, FK к MenuItem, снимка названия.
- Order items сохранялись через односторонний join column без полноценной модели владельца связи.
- Нет транзакционной координации проверки наличия и сохранения, серверной валидации, QR, idempotency.
- Все KDS операции были открыты, CORS wildcard, произвольные обратные переходы статуса.
- Возвращались JPA entities, неограниченная история/active загрузка, отсутствовали timestamp UTC/completedAt/version.
- Нет миграций, безопасной конфигурации production, Docker/CI, STOMP, метрик и интеграционных проверок.

Сохранены бизнес-смысл заказа, три статуса, пути /api/orders, /active и /{id}/status. Маленький исходный модуль заменён feature-модулями, поскольку его модели и controller не обеспечивали нужных инвариантов. Изменения контрактов перечислены в API_CONTRACT.md.

## Версии

Проверены в Maven Central: Boot 3.5.16, springdoc 2.8.17, ZXing 3.5.4. Maven Wrapper использует существующий Maven 3.9.14. Управляемые Boot зависимости (Hibernate, Flyway, PostgreSQL JDBC, Security, Micrometer, Testcontainers, Lombok) не получают самостоятельных версий.

Источники: [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html), [springdoc compatibility](https://springdoc.org/v2/), [Maven Central Boot metadata](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml), [springdoc metadata](https://repo.maven.apache.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/maven-metadata.xml), [ZXing metadata](https://repo.maven.apache.org/maven2/com/google/zxing/javase/maven-metadata.xml).

## Существующая БД

В репозитории не было миграций/конфигурации БД и данных. V1 предназначена для новой базы. Для неизвестной внешней БД со старой ручной схемой автоматический destructive upgrade не выполняется: перед подключением production базы нужны backup, инвентаризация старой схемы и отдельная reviewed data migration. Flyway baseline-on-migrate не включён.
