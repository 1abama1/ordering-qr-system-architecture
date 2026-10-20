package com.restaurant.ordering.realtime.config;
import com.restaurant.ordering.common.config.AppProperties;
import com.restaurant.ordering.realtime.security.StompSecurityInterceptor;
import com.restaurant.ordering.realtime.security.SubscriptionReceiptInterceptor;
import com.restaurant.ordering.realtime.security.StompSessions;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.config.*;
import org.springframework.messaging.support.*;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.*;

@Configuration @EnableWebSocketMessageBroker @RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final AppProperties properties;
    private final StompSecurityInterceptor security;
    private final SubscriptionReceiptInterceptor receipts;
    private final StompSessions sessions;
    @Bean public ThreadPoolTaskScheduler stompHeartbeatScheduler() {
        var scheduler = new ThreadPoolTaskScheduler(); scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("stomp-heartbeat-"); return scheduler;
    }
    @Override public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins(properties.allowedOrigins().toArray(String[]::new));
    }
    @Override public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic").setTaskScheduler(stompHeartbeatScheduler()).setHeartbeatValue(new long[]{10000, 10000});
        registry.setApplicationDestinationPrefixes("/app"); registry.setPreservePublishOrder(true);
    }
    @Override public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(security, receipts);
        registration.taskExecutor().corePoolSize(2).maxPoolSize(4).queueCapacity(256);
    }
    @Override public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
                return sessions.canDeliver(message) ? message : null;
            }
        });
        registration.taskExecutor().corePoolSize(2).maxPoolSize(4).queueCapacity(256);
    }
    @Override public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(16384).setSendBufferSizeLimit(262144).setSendTimeLimit(10000).setTimeToFirstMessage(10000);
    }
}
