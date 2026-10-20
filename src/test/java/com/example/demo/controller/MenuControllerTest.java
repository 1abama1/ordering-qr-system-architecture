package com.example.demo.controller;

import com.example.demo.event.MenuItemAvailabilityChangedEvent;
import com.example.demo.repository.MenuItemRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.event.EventListener;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test") // тесты работают на H2 из application-test.properties, Postgres не нужен
@Transactional // после каждого теста изменения в базе откатываются
@Import(MenuControllerTest.EventCatcher.class)
class MenuControllerTest {

    @Autowired MockMvc mvc;
    @Autowired MenuItemRepository repo;
    @Autowired EventCatcher events;

    @BeforeEach
    void resetEvents() {
        events.received.clear();
    }

    @Test
    void seederFillsMenuWith15DishesIn3Categories() throws Exception {
        mvc.perform(get("/api/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(15)))
                .andExpect(jsonPath("$[0].isAvailable").value(true))
                .andExpect(jsonPath("$[0].category").exists());

        mvc.perform(get("/api/menu/categories"))
                .andExpect(jsonPath("$", containsInAnyOrder("Напитки", "Горячее", "Десерты")));
    }

    @Test
    void toggleHidesDishFromClientsButNotFromKitchen() throws Exception {
        Long id = repo.findAll().get(0).getId();

        mvc.perform(patch("/api/menu/{id}/toggle-availability", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.isAvailable").value(false));

        mvc.perform(get("/api/menu")).andExpect(jsonPath("$", hasSize(14)));
        mvc.perform(get("/api/menu?all=true")).andExpect(jsonPath("$", hasSize(15)));

        // событие для WebSocket опубликовано
        assertThat(events.received).hasSize(1);
        assertThat(events.received.get(0).isAvailable()).isFalse();

        // второй клик возвращает блюдо (через альтернативный путь из спецификации)
        mvc.perform(patch("/api/menu/{id}/toggle", id))
                .andExpect(jsonPath("$.isAvailable").value(true));
        mvc.perform(get("/api/menu")).andExpect(jsonPath("$", hasSize(15)));
    }

    @Test
    void toggleUnknownIdReturns404() throws Exception {
        mvc.perform(patch("/api/menu/{id}/toggle-availability", 99999))
                .andExpect(status().isNotFound());
    }

    @Test
    void createAddsDishAndNewCategory() throws Exception {
        String body = """
                {"name":"Самса","description":"С говядиной","price":120,
                 "imageUrl":null,"category":"Выпечка"}
                """;
        mvc.perform(post("/api/menu").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.category").value("Выпечка"))
                .andExpect(jsonPath("$.isAvailable").value(true));

        mvc.perform(get("/api/menu")).andExpect(jsonPath("$", hasSize(16)));
    }

    @Test
    void createRejectsInvalidData() throws Exception {
        String body = """
                {"name":"","price":-5,"category":"Горячее"}
                """;
        mvc.perform(post("/api/menu").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EventCatcher {
        final List<MenuItemAvailabilityChangedEvent> received = new ArrayList<>();

        @EventListener
        public void on(MenuItemAvailabilityChangedEvent e) {
            received.add(e);
        }
    }
}
