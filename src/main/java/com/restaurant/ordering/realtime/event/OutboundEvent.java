package com.restaurant.ordering.realtime.event;
public record OutboundEvent(String destination, Object payload) {}
