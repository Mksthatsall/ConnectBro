package com.connectBro.ConnectionsService.services;

import com.connectBro.ConnectionsService.entity.GraphSyncEvent;
import com.connectBro.ConnectionsService.repository.GraphSyncEventRepository;
import com.connectBro.ConnectionsService.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class GraphSyncWorker {
    private final GraphSyncEventRepository eventRepository;
    private final PersonRepository personRepository;

    @Scheduled(fixedDelayString = "${connections.graph-sync-delay-ms:5000}")
    @Transactional(transactionManager = "transactionManager")
    public void synchronizeGraph() {
        for (GraphSyncEvent event : eventRepository.findTop100ByProcessedAtIsNullOrderByCreatedAtAscIdAsc()) {
            try {
                if ("UPSERT".equals(event.getAction())) {
                    personRepository.ensurePerson(event.getUserA());
                    personRepository.ensurePerson(event.getUserB());
                    personRepository.addFriendship(event.getUserA(), event.getUserB());
                } else if ("DELETE".equals(event.getAction())) {
                    personRepository.removeFriendship(event.getUserA(), event.getUserB());
                } else {
                    throw new IllegalStateException("Unsupported graph sync action: " + event.getAction());
                }
                event.setProcessedAt(LocalDateTime.now());
                event.setLastError(null);
            } catch (RuntimeException ex) {
                event.setAttempts(event.getAttempts() + 1);
                String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                event.setLastError(message.substring(0, Math.min(message.length(), 1000)));
                log.warn("Graph sync event {} failed (attempt {}): {}", event.getId(), event.getAttempts(), message);
            }
        }
    }
}
