package com.restaurant.ordering.realtime.security;
import com.restaurant.ordering.realtime.event.Events;
import com.restaurant.ordering.security.service.StaffJwtAuthentication;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component @RequiredArgsConstructor
public class StompSecurityInterceptor implements ChannelInterceptor {
    private final JwtDecoder decoder;
    private final StaffJwtAuthentication authentication;
    private final StompSessions sessions;
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var h = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (h == null || h.getCommand() == null) return message;
        if (h.getCommand() == StompCommand.CONNECT) {
            var authorization = h.getFirstNativeHeader("Authorization");
            if (authorization != null) {
                if (!authorization.startsWith("Bearer ")) throw new AccessDeniedException("Invalid authorization");
                try {
                    var principal = (JwtAuthenticationToken) authentication.convert(decoder.decode(authorization.substring(7)));
                    h.setUser(principal); sessions.register(h.getSessionId(), principal);
                }
                catch (Exception ex) { throw new AccessDeniedException("Invalid or expired staff token"); }
            }
            return message;
        }
        if (h.getUser() instanceof JwtAuthenticationToken jwt && !valid(jwt))
            throw new AccessDeniedException("Staff token expired; reconnect");
        if (h.getCommand() == StompCommand.SUBSCRIBE) {
            if (Events.MENU.equals(h.getDestination())) return message;
            if (Events.KITCHEN.equals(h.getDestination()) && h.getUser() instanceof JwtAuthenticationToken jwt &&
                    jwt.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_KITCHEN")))
                return message;
            throw new AccessDeniedException("Subscription denied");
        }
        if (h.getCommand() == StompCommand.DISCONNECT || h.getCommand() == StompCommand.UNSUBSCRIBE) return message;
        throw new AccessDeniedException("Client messages are not allowed");
    }
    public static boolean valid(JwtAuthenticationToken auth) {
        return auth.getToken().getExpiresAt() != null && auth.getToken().getExpiresAt().isAfter(Instant.now());
    }
}
