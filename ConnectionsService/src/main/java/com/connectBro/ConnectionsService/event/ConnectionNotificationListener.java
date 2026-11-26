package com.connectBro.ConnectionsService.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ConnectionNotificationListener {
    private final KafkaTemplate<Long, Object> kafkaTemplate;

    @Async("connectionNotificationExecutor")
    @TransactionalEventListener
    public void onConnectionRequested(ConnectionRequestedEvent event) {
        send("connection_request_topic", event);
    }

    @Async("connectionNotificationExecutor")
    @TransactionalEventListener
    public void onConnectionAccepted(ConnectionAcceptedEvent event) {
        send("connection_accepted_topic", event);
    }

    private void send(String topic, Object event) {
        try {
            kafkaTemplate.send(topic, event).whenComplete((result, error) -> {
                if (error != null) log.warn("Could not publish {} notification: {}", topic, error.getMessage());
            });
        } catch (RuntimeException error) {
            log.warn("Could not publish {} notification: {}", topic, error.getMessage());
        }
    }
}
