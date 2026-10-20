package com.example.demo.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Событие "блюдо попало в стоп-лист / вернулось".
 *
 * Модуль меню ничего не знает про WebSocket — он просто публикует это событие.
 * Шах в своём коде ловит его и пересылает в /topic/menu/updates:
 *
 *   @TransactionalEventListener
 *   public void on(MenuItemAvailabilityChangedEvent e) {
 *       messagingTemplate.convertAndSend("/topic/menu/updates", e);
 *   }
 *
 * Так наши части не зависят друг от друга, и мерж проходит без конфликтов.
 */
public record MenuItemAvailabilityChangedEvent(
        String type,
        Long id,
        @JsonProperty("isAvailable") boolean isAvailable
) {
    public static final String TYPE = "MENU_ITEM_AVAILABILITY_CHANGED";

    public MenuItemAvailabilityChangedEvent(Long id, boolean isAvailable) {
        this(TYPE, id, isAvailable);
    }
}
