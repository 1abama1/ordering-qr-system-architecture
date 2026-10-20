package com.restaurant.ordering;
import com.restaurant.ordering.realtime.security.StompSecurityInterceptor;
import com.restaurant.ordering.security.service.StaffJwtAuthentication;
import org.junit.jupiter.api.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StompSecurityTest {
    private final StompSecurityInterceptor security = new StompSecurityInterceptor(mock(JwtDecoder.class),mock(StaffJwtAuthentication.class), new com.restaurant.ordering.realtime.security.StompSessions());
    @Test void onlyMenuIsPublicAndSendIsForbidden() {
        var menu = StompHeaderAccessor.create(StompCommand.SUBSCRIBE); menu.setDestination("/topic/menu/updates");
        assertThat(security.preSend(MessageBuilder.createMessage(new byte[0],menu.getMessageHeaders()),null)).isNotNull();
        var kitchen = StompHeaderAccessor.create(StompCommand.SUBSCRIBE); kitchen.setDestination("/topic/kitchen/orders");
        assertThatThrownBy(() -> security.preSend(MessageBuilder.createMessage(new byte[0],kitchen.getMessageHeaders()),null)).isInstanceOf(AccessDeniedException.class);
        var send = StompHeaderAccessor.create(StompCommand.SEND); send.setDestination("/topic/menu/updates");
        assertThatThrownBy(() -> security.preSend(MessageBuilder.createMessage(new byte[0],send.getMessageHeaders()),null)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void expiredStaffCannotSubscribeEvenWithRole() {
        var token = Jwt.withTokenValue("test").header("alg","HS256").subject("staff").issuedAt(Instant.now().minusSeconds(120)).expiresAt(Instant.now().minusSeconds(1)).build();
        var h = StompHeaderAccessor.create(StompCommand.SUBSCRIBE); h.setDestination("/topic/kitchen/orders");
        h.setUser(new JwtAuthenticationToken(token,List.of(new SimpleGrantedAuthority("ROLE_KITCHEN"))));
        assertThatThrownBy(() -> security.preSend(MessageBuilder.createMessage(new byte[0],h.getMessageHeaders()),null)).isInstanceOf(AccessDeniedException.class);
    }
}
