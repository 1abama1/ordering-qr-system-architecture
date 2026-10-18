package com.restaurant.ordering.realtime.security;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.stereotype.Component;

// The simple broker does not implement receipts. Confirm SUBSCRIBE only after its registry has handled it.
@Component @Slf4j
public class SubscriptionReceiptInterceptor implements ExecutorChannelInterceptor {
    private final ObjectProvider<MessageChannel> outbound;
    public SubscriptionReceiptInterceptor(@Qualifier("clientOutboundChannel") ObjectProvider<MessageChannel> outbound) { this.outbound = outbound; }
    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        var input = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (ex != null || !(handler instanceof SimpleBrokerMessageHandler) || input == null ||
                input.getCommand() != StompCommand.SUBSCRIBE || input.getReceipt() == null) return;
        var reply = StompHeaderAccessor.create(StompCommand.RECEIPT);
        reply.setSessionId(input.getSessionId()); reply.setUser(input.getUser()); reply.setReceiptId(input.getReceipt());
        reply.setLeaveMutable(true);
        try { outbound.getObject().send(MessageBuilder.createMessage(new byte[0],reply.getMessageHeaders()),1000); }
        catch (RuntimeException failure) { log.warn("Unable to send subscription receipt; client must reconnect"); }
    }
}
