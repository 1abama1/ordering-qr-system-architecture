package com.restaurant.ordering.realtime.security;
import com.restaurant.ordering.realtime.event.Events;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class StompSessions {
    private final ConcurrentHashMap<String, JwtAuthenticationToken> staff = new ConcurrentHashMap<>();
    public void register(String sessionId, JwtAuthenticationToken authentication) {
        if (sessionId == null) throw new IllegalArgumentException("STOMP session ID is required");
        staff.put(sessionId, authentication);
    }
    public boolean canDeliver(Message<?> message) {
        String sessionId = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        var authentication = sessionId == null ? null : staff.get(sessionId);
        if (authentication != null && !StompSecurityInterceptor.valid(authentication)) return false;
        if (Events.KITCHEN.equals(SimpMessageHeaderAccessor.getDestination(message.getHeaders()))) {
            return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_KITCHEN"));
        }
        return true;
    }
    @EventListener
    public void disconnected(SessionDisconnectEvent event) { staff.remove(event.getSessionId()); }
}
