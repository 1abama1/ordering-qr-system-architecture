package com.restaurant.ordering;
import com.restaurant.ordering.common.config.AppProperties;
import com.restaurant.ordering.table.entity.RestaurantTable;
import com.restaurant.ordering.table.qr.TableTokenService;
import com.restaurant.ordering.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.URI;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class TableTokenTest {
    static AppProperties properties() { return new AppProperties("jwt-test-secret-with-at-least-32-bytes","qr-test-secret-with-at-least-32-bytes",
        URI.create("http://localhost/menu"), List.of("http://localhost"),15,30,10,60); }
    @Test void tokenIsBoundToTableAndVersionAndSurvivesServiceRestart() {
        var one = new RestaurantTable(3); ReflectionTestUtils.setField(one,"id",1L);
        var two = new RestaurantTable(4); ReflectionTestUtils.setField(two,"id",2L);
        var service = new TableTokenService(properties()); var token = service.issue(one);
        new TableTokenService(properties()).validate(one,token);
        assertThatThrownBy(() -> service.validate(two,token)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.validate(one,token + "x")).isInstanceOf(ApiException.class);
        one.regenerateToken();
        assertThatThrownBy(() -> service.validate(one,token)).isInstanceOf(ApiException.class);
    }
}
