package com.restaurant.ordering;
import com.restaurant.ordering.category.entity.Category;
import com.restaurant.ordering.common.exception.ApiException;
import com.restaurant.ordering.menu.entity.MenuItem;
import com.restaurant.ordering.menu.repository.MenuItemRepository;
import com.restaurant.ordering.order.dto.*;
import com.restaurant.ordering.order.entity.Order;
import com.restaurant.ordering.order.repository.OrderRepository;
import com.restaurant.ordering.order.service.OrderService;
import com.restaurant.ordering.table.entity.RestaurantTable;
import com.restaurant.ordering.table.service.TableService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock OrderRepository orders; @Mock MenuItemRepository menu; @Mock TableService tables;
    @Mock JdbcTemplate jdbc; @Mock ApplicationEventPublisher events;
    OrderService service;
    final String key = "e9cc352d-a196-4b92-b534-925d5f548db5";
    @BeforeEach void setup() { service = new OrderService(orders,menu,tables,jdbc,events,new SimpleMeterRegistry()); }
    CreateOrderRequest request() { return new CreateOrderRequest(3,"token",List.of(new CreateOrderRequest.Item(1L,2))); }
    MenuItem available(boolean value) {
        var m = new MenuItem("Soup","",new BigDecimal("12.35"),null,new Category("Hot",0),value,true);
        ReflectionTestUtils.setField(m,"id",1L); return m;
    }
    void table() {
        var t = new RestaurantTable(3); ReflectionTestUtils.setField(t,"id",3L);
        when(tables.validateForOrder(3,"token")).thenReturn(t);
    }
    @Test void usesDatabasePricesAndSavesAtomically() {
        table(); when(menu.lockAllById(any())).thenReturn(List.of(available(true)));
        when(orders.saveAndFlush(any())).thenAnswer(i -> { ReflectionTestUtils.setField((Object) i.getArgument(0),"id",1L); return i.getArgument(0); });
        var result = service.create(request(),key);
        assertThat(result.order().totalAmount()).isEqualByComparingTo("24.70"); assertThat(result.replayed()).isFalse();
        verify(orders).saveAndFlush(any(Order.class)); verify(events).publishEvent(any(Object.class));
    }
    @Test void unavailableDishDoesNotSaveOrPublish() {
        table(); when(menu.lockAllById(any())).thenReturn(List.of(available(false)));
        assertThatThrownBy(() -> service.create(request(),key)).isInstanceOf(ApiException.class).hasMessageContaining("unavailable");
        verify(orders,never()).saveAndFlush(any()); verifyNoInteractions(events);
    }
    @Test void nonexistentDishDoesNotSave() {
        table(); when(menu.lockAllById(any())).thenReturn(List.of());
        assertThatThrownBy(() -> service.create(request(),key)).isInstanceOf(ApiException.class).hasMessageContaining("do not exist");
        verify(orders,never()).saveAndFlush(any());
    }
    @Test void tableAndQrValidationErrorsPropagate() {
        when(tables.validateForOrder(3,"token")).thenThrow(ApiException.notFound("TABLE_NOT_FOUND","Missing table"));
        assertThatThrownBy(() -> service.create(request(),key)).isInstanceOf(ApiException.class).hasMessage("Missing table");
        verifyNoInteractions(menu,events);
    }
    @Test void duplicateItemsAreRejectedBeforeDatabaseWork() {
        var r = new CreateOrderRequest(3,"token",List.of(new CreateOrderRequest.Item(1L,1),new CreateOrderRequest.Item(1L,2)));
        assertThatThrownBy(() -> service.create(r,key)).isInstanceOf(ApiException.class).hasMessageContaining("Duplicate");
        verifyNoInteractions(orders,menu,tables,events);
    }
    @Test void idempotencyKeyMustBeUuid() {
        assertThatThrownBy(() -> service.create(request(),"bad")).isInstanceOf(ApiException.class).hasMessageContaining("UUID");
        verifyNoInteractions(orders,menu,tables,events);
    }
}
