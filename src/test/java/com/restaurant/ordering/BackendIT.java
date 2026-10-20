package com.restaurant.ordering;

import com.fasterxml.jackson.databind.*;
import com.restaurant.ordering.category.entity.Category;
import com.restaurant.ordering.category.repository.CategoryRepository;
import com.restaurant.ordering.menu.entity.MenuItem;
import com.restaurant.ordering.menu.repository.MenuItemRepository;
import com.restaurant.ordering.menu.service.MenuService;
import com.restaurant.ordering.order.dto.CreateOrderRequest;
import com.restaurant.ordering.order.entity.*;
import com.restaurant.ordering.order.repository.OrderRepository;
import com.restaurant.ordering.order.service.OrderService;
import com.restaurant.ordering.security.dto.AuthDtos;
import com.restaurant.ordering.security.entity.StaffUser;
import com.restaurant.ordering.security.service.AuthService;
import com.restaurant.ordering.table.entity.RestaurantTable;
import com.restaurant.ordering.table.repository.RestaurantTableRepository;
import com.restaurant.ordering.table.qr.TableTokenService;
import com.restaurant.ordering.common.exception.ApiException;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.StreamSupport;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackendIT {
    @DynamicPropertySource static void configuration(DynamicPropertyRegistry r) {
        TestDatabase.configure(r,"ordering_integration");
        r.add("app.jwt-secret", () -> "integration-jwt-secret-at-least-32-bytes-long");
        r.add("app.qr-secret", () -> "integration-qr-secret-at-least-32-bytes-long");
        r.add("BOOTSTRAP_ADMIN_USERNAME", () -> "it-admin"); r.add("BOOTSTRAP_ADMIN_PASSWORD", () -> "Integration-Admin-Password");
        r.add("app.order-rate-limit", () -> 10000); r.add("app.login-rate-limit", () -> 10); r.add("app.qr-rate-limit", () -> 10000);
        r.add("spring.jpa.properties.hibernate.generate_statistics", () -> true);
        r.add("logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener", () -> "OFF");
    }
    @LocalServerPort int port;
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper mapper;
    @Autowired CategoryRepository categories;
    @Autowired MenuItemRepository menu;
    @Autowired RestaurantTableRepository tables;
    @Autowired OrderRepository orders;
    @Autowired TableTokenService qr;
    @Autowired OrderService orderService;
    @Autowired MenuService menuService;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.restaurant.ordering.bootstrap.seed.DemoSeeder demoSeeder;
    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("stompHeartbeatScheduler") org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler testScheduler;
    @Autowired EntityManagerFactory emf;
    String admin, kitchen;
    RestaurantTable table;
    MenuItem item;
    String tableToken;
    static final AtomicInteger NUMBERS = new AtomicInteger(100);
    @BeforeAll void accounts() {
        admin = login("it-admin","Integration-Admin-Password");
        auth.create(new AuthDtos.CreateStaff("it-cook","Integration-Cook-Password", StaffUser.Role.KITCHEN));
        kitchen = login("it-cook","Integration-Cook-Password");
    }
    @BeforeEach void fixture() {
        int n = NUMBERS.incrementAndGet();
        var c = categories.saveAndFlush(new Category("IT category " + n,10));
        item = menu.saveAndFlush(new MenuItem("IT dish " + n,"test",new BigDecimal("320.10"),null,c,true,true));
        table = tables.saveAndFlush(new RestaurantTable(n)); tableToken = qr.issue(table);
    }
    String login(String username, String password) {
        var result = call(HttpMethod.POST,"/api/staff/auth/login",Map.of("username",username,"password",password),null,null);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK); return result.getBody().path("accessToken").asText();
    }
    ResponseEntity<JsonNode> call(HttpMethod method, String path, Object body, String token, String key) {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token); if (key != null) headers.set("Idempotency-Key",key);
        return http.exchange(path,method,new HttpEntity<>(body,headers),JsonNode.class);
    }
    Map<String,Object> request(int quantity) {
        return Map.of("tableNumber",table.getNumber(),"tableToken",tableToken,"items",List.of(Map.of("menuItemId",item.getId(),"quantity",quantity)));
    }
    CreateOrderRequest domainRequest(int quantity) { return new CreateOrderRequest(table.getNumber(),tableToken,List.of(new CreateOrderRequest.Item(item.getId(),quantity))); }
    JsonNode create() {
        var result = call(HttpMethod.POST,"/api/orders",request(2),null,UUID.randomUUID().toString());
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED); return result.getBody();
    }
    List<Long> activeIds() {
        var result = call(HttpMethod.GET,"/api/orders/active?size=200",null,kitchen,null);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        return StreamSupport.stream(result.getBody().spliterator(),false).map(n -> n.path("id").asLong()).toList();
    }
    @Test void realHttpHappyPathWithSnapshotsAndIdempotentCompletion() {
        var created = create(); long id = created.path("id").asLong();
        assertThat(created.path("totalAmount").decimalValue()).isEqualByComparingTo("640.20");
        assertThat(activeIds()).contains(id);
        assertThat(call(HttpMethod.PATCH,"/api/orders/"+id+"/status",Map.of("status","COOKING"),kitchen,null).getStatusCode()).isEqualTo(HttpStatus.OK);
        var done = call(HttpMethod.PATCH,"/api/orders/"+id+"/complete",null,kitchen,null);
        assertThat(done.getBody().path("completedAt").asText()).isNotBlank();
        var repeat = call(HttpMethod.PATCH,"/api/orders/"+id+"/complete",null,kitchen,null);
        assertThat(repeat.getBody().path("version")).isEqualTo(done.getBody().path("version"));
        assertThat(activeIds()).doesNotContain(id);
        assertThat(call(HttpMethod.PATCH,"/api/orders/"+id+"/status",Map.of("status","PENDING"),kitchen,null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var update = Map.of("name","Changed dish","description","changed","price",new BigDecimal("990.00"),"categoryId",item.getCategory().getId(),"isActive",true,"isAvailable",true);
        assertThat(call(HttpMethod.PUT,"/api/menu/"+item.getId(),update,admin,null).getStatusCode()).isEqualTo(HttpStatus.OK);
        var history = call(HttpMethod.GET,"/api/orders/"+id,null,kitchen,null).getBody();
        assertThat(history.path("items").get(0).path("menuItemName").asText()).isEqualTo(item.getName());
        assertThat(history.path("items").get(0).path("priceAtOrder").decimalValue()).isEqualByComparingTo("320.10");
        assertThat(jdbc.queryForObject("select count(*) from order_items where order_id=?",Integer.class,id)).isEqualTo(1);
    }
    @Test void duplicateRetriesAreSerializedAndDifferentPayloadConflicts() throws Exception {
        String key = UUID.randomUUID().toString(); long before = orders.count();
        try (var pool = Executors.newFixedThreadPool(6)) {
            var gate = new CountDownLatch(1); var jobs = new ArrayList<Future<ResponseEntity<JsonNode>>>();
            for (int n=0;n<6;n++) jobs.add(pool.submit(() -> { gate.await(); return call(HttpMethod.POST,"/api/orders",request(2),null,key); }));
            gate.countDown(); var results = new ArrayList<ResponseEntity<JsonNode>>();
            for (var job : jobs) results.add(job.get(15,TimeUnit.SECONDS));
            assertThat(results).allSatisfy(r -> assertThat(r.getStatusCode()).isIn(HttpStatus.CREATED,HttpStatus.OK));
            assertThat(results.stream().filter(r -> r.getStatusCode() == HttpStatus.CREATED).count()).isEqualTo(1);
            assertThat(results.stream().map(r -> r.getBody().path("id").asLong()).distinct().count()).isEqualTo(1);
            assertThat(orders.count()).isEqualTo(before+1);
        }
        assertThat(call(HttpMethod.POST,"/api/orders",request(3),null,key).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
    @Test void validationTamperedPricesAndMissingItemsCannotPersistPartialOrder() {
        long before = orders.count();
        var fake = new HashMap<String,Object>(request(2)); fake.put("items",List.of(Map.of("menuItemId",item.getId(),"quantity",2,"price",0.01)));
        assertThat(call(HttpMethod.POST,"/api/orders",fake,null,UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var empty = new HashMap<String,Object>(request(2)); empty.put("items",List.of());
        assertThat(call(HttpMethod.POST,"/api/orders",empty,null,UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        for (int quantity : new int[]{0,-1,101}) assertThat(call(HttpMethod.POST,"/api/orders",request(quantity),null,UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var missing = new HashMap<String,Object>(request(2)); missing.put("items",List.of(Map.of("menuItemId",item.getId(),"quantity",1),Map.of("menuItemId",9999999,"quantity",1)));
        assertThat(call(HttpMethod.POST,"/api/orders",missing,null,UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var duplicate = new HashMap<String,Object>(request(2)); duplicate.put("items",List.of(Map.of("menuItemId",item.getId(),"quantity",1),Map.of("menuItemId",item.getId(),"quantity",1)));
        assertThat(call(HttpMethod.POST,"/api/orders",duplicate,null,UUID.randomUUID().toString()).getBody().path("code").asText()).isEqualTo("DUPLICATE_MENU_ITEM");
        assertThat(call(HttpMethod.POST,"/api/orders",request(1),null,null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(orders.count()).isEqualTo(before);
    }
    @Test void stopListAndSoftDeletionAreEnforcedOnServer() {
        assertThat(call(HttpMethod.PATCH,"/api/menu/"+item.getId()+"/availability",Map.of("isAvailable",false),kitchen,null).getStatusCode()).isEqualTo(HttpStatus.OK);
        var denied = call(HttpMethod.POST,"/api/orders",request(1),null,UUID.randomUUID().toString());
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.CONFLICT); assertThat(denied.getBody().path("code").asText()).isEqualTo("MENU_ITEM_UNAVAILABLE");
        assertThat(http.getForObject("/api/menu",JsonNode.class).toString()).doesNotContain(item.getName());
        assertThat(http.getForObject("/api/menu?includeUnavailable=true",JsonNode.class).toString()).contains(item.getName());
        menuService.availability(item.getId(),true); var saved = create();
        assertThat(call(HttpMethod.DELETE,"/api/menu/"+item.getId(),null,admin,null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(http.getForObject("/api/menu?includeUnavailable=true",JsonNode.class).toString()).doesNotContain(item.getName());
        assertThat(call(HttpMethod.POST,"/api/orders",request(1),null,UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(HttpMethod.GET,"/api/orders/"+saved.path("id").asLong(),null,kitchen,null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void qrResolutionBindingRevocationPngAndInactiveTable() {
        String resolve = "/api/tables/resolve?tableNumber="+table.getNumber()+"&tableToken="+tableToken;
        assertThat(call(HttpMethod.GET,resolve,null,null,null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET,"/api/tables/resolve?tableNumber=3&tableToken="+tableToken,null,null,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var headers = new HttpHeaders(); headers.setBearerAuth(admin);
        var png = http.exchange("/api/staff/tables/"+table.getId()+"/qr?format=png",HttpMethod.GET,new HttpEntity<>(headers),byte[].class);
        assertThat(png.getStatusCode()).isEqualTo(HttpStatus.OK); assertThat(png.getBody()).startsWith(new byte[]{(byte)137,80,78,71});
        var metadata = call(HttpMethod.GET,"/api/staff/tables/"+table.getId()+"/qr",null,admin,null).getBody();
        assertThat(metadata.path("url").asText()).contains("/menu?table="+table.getNumber()+"&token=");
        var regenerated = call(HttpMethod.POST,"/api/staff/tables/"+table.getId()+"/regenerate-token",null,admin,null);
        assertThat(regenerated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.GET,resolve,null,null,null).getBody().path("code").asText()).isEqualTo("INVALID_TABLE_TOKEN");
        tableToken = regenerated.getBody().path("tableToken").asText();
        call(HttpMethod.PATCH,"/api/staff/tables/"+table.getId(),Map.of("isActive",false),admin,null);
        assertThat(call(HttpMethod.POST,"/api/orders",request(1),null,UUID.randomUUID().toString()).getBody().path("code").asText()).isEqualTo("TABLE_INACTIVE");
        var nonexistent = new HashMap<String,Object>(request(1)); nonexistent.put("tableNumber",999999);
        assertThat(call(HttpMethod.POST,"/api/orders",nonexistent,null,UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
    @Test void rolesErrorsCorsAndRequestSize() {
        assertThat(call(HttpMethod.GET,"/api/orders/active",null,null,null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.GET,"/api/orders/active",null,"broken-token",null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var forbidden = call(HttpMethod.DELETE,"/api/menu/"+item.getId(),null,kitchen,null);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(forbidden.getBody().path("code").asText()).isEqualTo("FORBIDDEN");
        assertThat(forbidden.getBody().path("correlationId").asText()).isNotBlank();
        assertThat(call(HttpMethod.GET,"/api/staff/tables",null,kitchen,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.POST,"/api/categories",Map.of("name","new category","sortOrder",1),kitchen,null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET,"/api/orders/active?size=201",null,kitchen,null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var huge = Map.of("padding","x".repeat(33000));
        assertThat(call(HttpMethod.POST,"/api/orders",huge,null,UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        var headers = new HttpHeaders(); headers.setOrigin("https://hostile.example"); headers.set("Access-Control-Request-Method","POST");
        assertThat(http.exchange("/api/orders",HttpMethod.OPTIONS,new HttpEntity<>(headers),String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
    @Test void migrationsConstraintsDemoIdempotencyAndBoundedKdsQueries() throws Exception {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success",Integer.class)).isEqualTo(2);
        long menuCount = menu.count(), tableCount = tables.count(), categoryCount = categories.count();
        demoSeeder.run();
        assertThat(menu.count()).isEqualTo(menuCount); assertThat(tables.count()).isEqualTo(tableCount); assertThat(categories.count()).isEqualTo(categoryCount);
        assertThat(tables.findAll().size()).isGreaterThanOrEqualTo(10); assertThat(menu.findAll().size()).isGreaterThanOrEqualTo(15);
        assertThatThrownBy(() -> jdbc.update("insert into menu_items(name,price,category_id) values ('bad',-1,?)",item.getCategory().getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("insert into order_items(order_id,menu_item_id,menu_item_name_snapshot,quantity,price_at_order,line_total) values (9999999,9999999,'bad',1,1,1)")).isInstanceOf(DataIntegrityViolationException.class);
        for (int i=0;i<3;i++) create();
        var statistics = emf.unwrap(SessionFactory.class).getStatistics(); statistics.clear();
        var active = orderService.active(0,200); assertThat(active).isNotEmpty();
        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
    }
    @Test void stopListCommitWinsBeforeOrder() throws Exception {
        long before = orders.count(); var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var stop = pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
                menuService.availability(item.getId(),false); locked.countDown(); awaitLatch(release);
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var order = pool.submit(() -> orderService.create(domainRequest(1),UUID.randomUUID().toString()));
            try {
                await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(waitingMenuLocks()).isPositive());
                assertThat(order.isDone()).isFalse();
            } finally { release.countDown(); }
            stop.get(10,TimeUnit.SECONDS);
            assertThatThrownBy(() -> order.get(10,TimeUnit.SECONDS)).hasCauseInstanceOf(ApiException.class);
            assertThat(orders.count()).isEqualTo(before);
        } finally { release.countDown(); }
    }
    @Test void orderCommitWinsBeforeStopListAndSubsequentOrdersAreRejected() throws Exception {
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var order = pool.submit(() -> new TransactionTemplate(transactionManager).execute(s -> {
                var result = orderService.create(domainRequest(1),UUID.randomUUID().toString()); locked.countDown(); awaitLatch(release); return result;
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var stop = pool.submit(() -> menuService.availability(item.getId(),false));
            try { await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(waitingMenuLocks()).isPositive()); }
            finally { release.countDown(); }
            assertThat(order.get(10,TimeUnit.SECONDS).order().totalAmount()).isEqualByComparingTo("320.10");
            assertThat(stop.get(10,TimeUnit.SECONDS).isAvailable()).isFalse();
            assertThatThrownBy(() -> orderService.create(domainRequest(1),UUID.randomUUID().toString())).isInstanceOf(ApiException.class);
        } finally { release.countDown(); }
    }
    int waitingMenuLocks() {
        return jdbc.queryForObject("select count(*) from pg_stat_activity where wait_event_type='Lock' and query like '%menu_items%'",Integer.class);
    }
    void awaitLatch(CountDownLatch latch) {
        try { if (!latch.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Latch timeout"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
    @Test void eventsAreAfterCommitRollbackIsSilentAndReconnectRestoresState() throws Exception {
        try (var ws = new Socket(kitchen)) {
            ws.subscribe("/topic/kitchen/orders");
            var key = UUID.randomUUID().toString(); long before = orders.count();
            assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
                orderService.create(domainRequest(1),key); throw new IllegalStateException("intentional rollback");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(orders.count()).isEqualTo(before); assertThat(ws.events.poll(400,TimeUnit.MILLISECONDS)).isNull();
            long start = System.nanoTime(); var order = create(); var event = ws.events.poll(3,TimeUnit.SECONDS);
            long latency = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
            assertThat(event).isNotNull(); assertThat(event.path("type").asText()).isEqualTo("NEW_ORDER");
            assertThat(event.path("orderId").asLong()).isEqualTo(order.path("id").asLong());
            assertThat(orders.existsById(event.path("orderId").asLong())).isTrue();
            assertThat(latency).isLessThan(1000);
            LoggerFactory.getLogger(getClass()).info("Measured HTTP creation to STOMP reception={} ms",latency);
            ws.session.disconnect(); var missed = create();
            try (var reconnect = new Socket(kitchen)) {
                reconnect.subscribe("/topic/kitchen/orders");
                assertThat(activeIds()).contains(missed.path("id").asLong());
                call(HttpMethod.PATCH,"/api/orders/"+missed.path("id").asLong()+"/status",Map.of("status","COOKING"),kitchen,null);
                assertThat(reconnect.events.poll(3,TimeUnit.SECONDS).path("type").asText()).isEqualTo("ORDER_STATUS_CHANGED");
            }
        }
    }
    @Test void publicMenuReceivesStopListButAnonymousKitchenAndSendAreDenied() throws Exception {
        try (var guest = new Socket(null)) {
            guest.subscribe("/topic/menu/updates");
            menuService.availability(item.getId(),false);
            var event = guest.events.poll(3,TimeUnit.SECONDS);
            assertThat(event).isNotNull(); assertThat(event.path("type").asText()).isEqualTo("MENU_ITEM_AVAILABILITY_CHANGED");
            assertThat(event.path("isAvailable").asBoolean()).isFalse();
            menuService.availability(item.getId(),false);
            assertThat(guest.events.poll(300,TimeUnit.MILLISECONDS)).isNull();
        }
        try (var guest = new Socket(null)) {
            guest.session.subscribe("/topic/kitchen/orders",guest.handler());
            await().atMost(Duration.ofSeconds(3)).until(() -> guest.error.get() != null || !guest.session.isConnected());
        }
        try (var guest = new Socket(null)) {
            guest.session.send("/topic/menu/updates",Map.of("type","FAKE_EVENT"));
            await().atMost(Duration.ofSeconds(3)).until(() -> guest.error.get() != null || !guest.session.isConnected());
        }
    }
    @Test void twentyConnectedClientsAndConcurrentOrdersSmoke() throws Exception {
        var clients = new ArrayList<Socket>();
        try {
            for (int n=0;n<20;n++) { var socket = new Socket(null); clients.add(socket); socket.subscribe("/topic/menu/updates"); }
            try (var pool = Executors.newFixedThreadPool(8)) {
                var work = new ArrayList<Future<Long>>();
                for (int n=0;n<30;n++) work.add(pool.submit(() -> {
                    long start = System.nanoTime();
                    assertThat(call(HttpMethod.POST,"/api/orders",request(1),null,UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.CREATED);
                    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
                }));
                var times = new ArrayList<Long>(); for (var job : work) times.add(job.get(15,TimeUnit.SECONDS));
                times.sort(Long::compareTo);
                LoggerFactory.getLogger(getClass()).info("Load smoke: clients=20 orders=30 workers=8 p50={}ms p95={}ms max={}ms",times.get(15),times.get(28),times.get(29));
            }
            menuService.availability(item.getId(),false);
            for (var socket : clients) assertThat(socket.events.poll(3,TimeUnit.SECONDS)).isNotNull();
        } finally { clients.forEach(Socket::close); }
    }
    @Test void loginRateLimitReturns429() {
        ResponseEntity<JsonNode> result = null;
        for (int i=0;i<11;i++) result = call(HttpMethod.POST,"/api/staff/auth/login",Map.of("username","missing-user","password","invalid-password"),null,null);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(result.getHeaders().getFirst("Retry-After")).isEqualTo("60");
    }
    @Test void healthAndSwaggerReallyOpen() {
        assertThat(http.getForEntity("/actuator/health",String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/swagger-ui/index.html",String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        var spec = http.getForObject("/v3/api-docs",JsonNode.class);
        assertThat(spec.path("paths").has("/api/orders")).isTrue();
        assertThat(spec.path("components").path("securitySchemes").has("bearerAuth")).isTrue();
    }

    @Test void disabledAccountsAndExpiredJwtAreRejected() throws Exception {
        var username = "disabled-" + table.getNumber();
        var user = auth.create(new AuthDtos.CreateStaff(username,"Integration-User-Password",StaffUser.Role.KITCHEN));
        var token = auth.login(new AuthDtos.Login(username,"Integration-User-Password")).accessToken();
        jdbc.update("update staff_users set enabled=false where id=?",user.id());
        assertThat(call(HttpMethod.GET,"/api/staff/auth/me",null,token,null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var now = Instant.now();
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer("restaurant-ordering")
            .audience(List.of("ordering-staff")).subject("it-admin").issuedAt(now.minusSeconds(60)).expiresAt(now.minusSeconds(1)).build();
        var expired = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
            org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(),claims)).getTokenValue();
        assertThat(call(HttpMethod.GET,"/api/orders/active",null,expired,null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    @Test void connectedStaffStopsReceivingEventsAtJwtExpiry() throws Exception {
        var now = Instant.now();
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer("restaurant-ordering")
            .audience(List.of("ordering-staff")).subject("it-admin").issuedAt(now).expiresAt(now.plusSeconds(2)).build();
        var token = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
            org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(),claims)).getTokenValue();
        try (var socket = new Socket(token)) {
            socket.subscribe("/topic/kitchen/orders");
            await().atMost(Duration.ofSeconds(4)).until(() -> Instant.now().isAfter(claims.getExpiresAt()));
            create(); assertThat(socket.events.poll(400,TimeUnit.MILLISECONDS)).isNull();
        }
    }
    class Socket implements AutoCloseable {
        final WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        final BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
        final AtomicReference<Throwable> error = new AtomicReference<>();
        final StompSession session;
        Socket(String token) throws Exception {
            client.setTaskScheduler(testScheduler);
            var json = new MappingJackson2MessageConverter(); json.setObjectMapper(mapper);
            client.setMessageConverter(new CompositeMessageConverter(List.of(json,new StringMessageConverter(),new ByteArrayMessageConverter())));
            var handshake = new WebSocketHttpHeaders(); handshake.setOrigin("http://localhost:5173");
            var headers = new StompHeaders(); if (token != null) headers.set("Authorization","Bearer "+token);
            session = client.connectAsync("ws://localhost:"+port+"/ws",handshake,headers,new StompSessionHandlerAdapter() {
                @Override public Type getPayloadType(StompHeaders h) { return byte[].class; }
                @Override public void handleFrame(StompHeaders h, Object p) { error.set(new IllegalStateException("STOMP error")); }
                @Override public void handleException(StompSession s, StompCommand c, StompHeaders h, byte[] p, Throwable e) { error.set(e); }
                @Override public void handleTransportError(StompSession s, Throwable e) { error.set(e); }
            }).get(5,TimeUnit.SECONDS);
        }
        StompFrameHandler handler() {
            return new StompFrameHandler() {
                @Override public Type getPayloadType(StompHeaders h) { return JsonNode.class; }
                @Override public void handleFrame(StompHeaders h, Object payload) { events.add((JsonNode)payload); }
            };
        }
        void subscribe(String destination) throws Exception {
            session.setAutoReceipt(true); var ready = new CompletableFuture<Void>();
            var subscription = session.subscribe(destination,handler());
            subscription.addReceiptTask(() -> ready.complete(null));
            subscription.addReceiptLostTask(() -> ready.completeExceptionally(new IllegalStateException("Subscription receipt timed out")));
            ready.get(5,TimeUnit.SECONDS); session.setAutoReceipt(false);
        }
        @Override public void close() {
            try { if (session.isConnected()) session.disconnect(); } catch (Exception ignored) { /* transport already closed by server */ }
            client.stop();
        }
    }
}
