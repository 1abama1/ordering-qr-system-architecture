package com.restaurant.ordering;
import com.restaurant.ordering.category.entity.Category;
import com.restaurant.ordering.menu.entity.MenuItem;
import com.restaurant.ordering.order.dto.CreateOrderRequest;
import com.restaurant.ordering.order.entity.*;
import com.restaurant.ordering.order.entity.Order;
import com.restaurant.ordering.table.entity.RestaurantTable;
import com.restaurant.ordering.common.exception.ApiException;
import com.fasterxml.jackson.databind.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class OrderRulesTest {
    private MenuItem item() { return new MenuItem("Лагман", "", new BigDecimal("320.10"), null, new Category("Hot",0), true,true); }
    @Test void snapshotsAndExactDecimalArithmeticSurviveMenuEdits() {
        var menu = item(); var order = new Order(new RestaurantTable(3), UUID.randomUUID().toString(), "fingerprint");
        order.addItem(new OrderItem(menu,3));
        menu.update("New name","",new BigDecimal("999.00"),null, menu.getCategory(),true,true);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("960.30");
        assertThat(order.getItems().getFirst().getPriceAtOrder()).isEqualByComparingTo("320.10");
        assertThat(order.getItems().getFirst().getMenuItemNameSnapshot()).isEqualTo("Лагман");
    }
    @Test void cookingThenCompleteAndRepeatIsSafe() {
        var order = new Order(new RestaurantTable(3),"key","fingerprint");
        assertThat(order.changeStatus(OrderStatus.COOKING)).isTrue();
        assertThat(order.changeStatus(OrderStatus.COMPLETED)).isTrue();
        var completed = order.getCompletedAt();
        assertThat(order.changeStatus(OrderStatus.COMPLETED)).isFalse();
        assertThat(order.getCompletedAt()).isEqualTo(completed);
        assertThatThrownBy(() -> order.changeStatus(OrderStatus.COOKING)).isInstanceOf(ApiException.class);
    }
    @Test void directCompletionIsSupported() {
        var order = new Order(new RestaurantTable(3),"key","fingerprint");
        assertThat(order.changeStatus(OrderStatus.COMPLETED)).isTrue();
        assertThat(order.getCompletedAt()).isNotNull();
    }
    @Test void cannotReturnToPending() {
        var order = new Order(new RestaurantTable(3),"key","fingerprint"); order.changeStatus(OrderStatus.COOKING);
        assertThatThrownBy(() -> order.changeStatus(OrderStatus.PENDING)).isInstanceOf(ApiException.class);
    }
    @ParameterizedTest @ValueSource(ints={-1,0,101})
    void rejectsInvalidQuantities(int quantity) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var request = new CreateOrderRequest(3,"token",List.of(new CreateOrderRequest.Item(1L,quantity)));
            assertThat(factory.getValidator().validate(request)).isNotEmpty();
        }
    }
    @Test void emptyAndNullBasketAndTableAreInvalid() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new CreateOrderRequest(3,"token",List.of()))).isNotEmpty();
            assertThat(validator.validate(new CreateOrderRequest(null,null,null))).isNotEmpty();
            assertThat(validator.validate(new CreateOrderRequest(0,"token",List.of(new CreateOrderRequest.Item(null,null))))).isNotEmpty();
        }
    }
    @Test void clientPriceIsRejectedByStrictDto() {
        var mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        assertThatThrownBy(() -> mapper.readValue("""
            {"tableNumber":3,"tableToken":"token","items":[{"menuItemId":1,"quantity":2,"price":0.01}]}
            """, CreateOrderRequest.class)).isInstanceOf(com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException.class);
    }
}
