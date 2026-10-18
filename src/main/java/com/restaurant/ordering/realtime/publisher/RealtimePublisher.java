package com.restaurant.ordering.realtime.publisher;
import com.restaurant.ordering.realtime.event.OutboundEvent;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import java.util.concurrent.Executor;

@Component @Slf4j
public class RealtimePublisher {
    private final SimpMessagingTemplate messaging;
    private final Executor executor;
    private final MeterRegistry metrics;
    public RealtimePublisher(SimpMessagingTemplate messaging, @Qualifier("realtimeExecutor") Executor executor, MeterRegistry metrics) {
        this.messaging = messaging; this.executor = executor; this.metrics = metrics; this.messaging.setSendTimeout(1000);
    }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void afterCommit(OutboundEvent event) {
        var context = MDC.getCopyOfContextMap();
        try {
            executor.execute(() -> {
                if (context != null) MDC.setContextMap(context);
                try { messaging.convertAndSend(event.destination(), event.payload()); metrics.counter("ordering.realtime.published").increment(); }
                catch (Exception e) { failed(event, e); }
                finally { MDC.clear(); }
            });
        } catch (RuntimeException e) { failed(event, e); }
    }
    private void failed(OutboundEvent event, Exception e) {
        metrics.counter("ordering.realtime.failed").increment(); log.error("WebSocket publication failed destination={}; clients must resync using REST", event.destination(), e);
    }
}
