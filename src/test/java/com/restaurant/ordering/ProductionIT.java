package com.restaurant.ordering;
import com.restaurant.ordering.menu.repository.MenuItemRepository;
import com.restaurant.ordering.table.repository.RestaurantTableRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev","prod"})
class ProductionIT {
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        TestDatabase.configure(r,"ordering_production");
        r.add("app.jwt-secret",() -> "prod-integration-jwt-secret-with-32-bytes");
        r.add("app.qr-secret",() -> "prod-integration-qr-secret-with-32-bytes");
        r.add("app.demo.enabled",() -> true);
    }
    @Autowired TestRestTemplate http;
    @Autowired MenuItemRepository menu;
    @Autowired RestaurantTableRepository tables;
    @Test void productionDisablesDemoEvenWhenDevIsAlsoSelected() {
        assertThat(menu.count()).isZero(); assertThat(tables.count()).isZero();
        assertThat(http.getForEntity("/actuator/health",String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
    @Test void swaggerIsDisabledInProduction() {
        assertThat(http.getForEntity("/v3/api-docs",String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(http.getForEntity("/swagger-ui/index.html",String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
