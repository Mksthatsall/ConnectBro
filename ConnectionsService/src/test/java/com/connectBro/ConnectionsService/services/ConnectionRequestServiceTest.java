package com.connectBro.ConnectionsService.services;

import com.connectBro.ConnectionsService.entity.ConnectionRequest;
import com.connectBro.ConnectionsService.entity.ConnectionStatus;
import com.connectBro.ConnectionsService.repository.ConnectionRequestRepository;
import com.connectBro.ConnectionsService.repository.GraphSyncEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConnectionRequestServiceTest {
    @Mock private ConnectionRequestRepository requestRepository;
    @Mock private GraphSyncEventRepository graphSyncEventRepository;
    @Mock private KafkaTemplate<Long, Object> kafkaTemplate;
    @InjectMocks private ConnectionRequestService service;

    @Test
    void rejectsSelfConnection() {
        assertThrows(ResponseStatusException.class, () -> service.send(5L, 5L));
        verifyNoInteractions(requestRepository);
    }

    @Test
    void rejectsDuplicatePendingPairInEitherDirection() {
        ConnectionRequest pending = request(3L, 9L, ConnectionStatus.PENDING);
        when(requestRepository.findByLowUserIdAndHighUserId(3L, 9L)).thenReturn(Optional.of(pending));
        assertThrows(ResponseStatusException.class, () -> service.send(9L, 3L));
        verify(requestRepository, never()).save(any());
    }

    @Test
    void acceptingWritesRelationalStateAndAnOutboxEvent() {
        ConnectionRequest pending = request(3L, 9L, ConnectionStatus.PENDING);
        pending.setId(17L);
        pending.setRequesterUserId(3L);
        pending.setReceiverUserId(9L);
        when(requestRepository.findById(17L)).thenReturn(Optional.of(pending));
        when(requestRepository.saveAndFlush(pending)).thenReturn(pending);

        ConnectionRequest result = service.accept(17L, 9L);

        assertEquals(ConnectionStatus.ACCEPTED, result.getStatus());
        ArgumentCaptor<com.connectBro.ConnectionsService.entity.GraphSyncEvent> event =
                ArgumentCaptor.forClass(com.connectBro.ConnectionsService.entity.GraphSyncEvent.class);
        verify(graphSyncEventRepository).save(event.capture());
        assertEquals("UPSERT", event.getValue().getAction());
        assertEquals(3L, event.getValue().getUserA());
        assertEquals(9L, event.getValue().getUserB());
    }

    @Test
    void onlyTheRequestReceiverCanAccept() {
        ConnectionRequest pending = request(3L, 9L, ConnectionStatus.PENDING);
        pending.setRequesterUserId(3L);
        pending.setReceiverUserId(9L);
        when(requestRepository.findById(17L)).thenReturn(Optional.of(pending));
        assertThrows(ResponseStatusException.class, () -> service.accept(17L, 3L));
        verify(requestRepository, never()).saveAndFlush(any());
    }

    private ConnectionRequest request(Long low, Long high, ConnectionStatus status) {
        ConnectionRequest request = new ConnectionRequest();
        request.setLowUserId(low);
        request.setHighUserId(high);
        request.setStatus(status);
        return request;
    }
}
