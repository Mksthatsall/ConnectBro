package com.connectBro.ConnectionsService.services;

import com.connectBro.ConnectionsService.entity.ConnectionRequest;
import com.connectBro.ConnectionsService.entity.ConnectionStatus;
import com.connectBro.ConnectionsService.entity.GraphSyncEvent;
import com.connectBro.ConnectionsService.repository.ConnectionRequestRepository;
import com.connectBro.ConnectionsService.repository.GraphSyncEventRepository;
import com.connectBro.ConnectionsService.event.ConnectionAcceptedEvent;
import com.connectBro.ConnectionsService.event.ConnectionRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConnectionRequestService {
    private final ConnectionRequestRepository requestRepository;
    private final GraphSyncEventRepository graphSyncEventRepository;
    private final KafkaTemplate<Long, Object> kafkaTemplate;

    @Transactional(transactionManager = "transactionManager")
    public ConnectionRequest send(Long requesterId, Long receiverId) {
        if (requesterId == null || receiverId == null || requesterId.equals(receiverId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot connect with yourself");
        }
        long low = Math.min(requesterId, receiverId);
        long high = Math.max(requesterId, receiverId);
        ConnectionRequest request = requestRepository.findByLowUserIdAndHighUserId(low, high).orElse(null);
        if (request != null && request.getStatus() == ConnectionStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already connected");
        }
        if (request != null && request.getStatus() == ConnectionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A request for this user pair is already pending");
        }
        if (request == null) {
            request = new ConnectionRequest();
            request.setLowUserId(low);
            request.setHighUserId(high);
        }
        request.setRequesterUserId(requesterId);
        request.setReceiverUserId(receiverId);
        request.setStatus(ConnectionStatus.PENDING);
        try {
            ConnectionRequest saved = requestRepository.saveAndFlush(request);
            kafkaTemplate.send("connection_request_topic", new ConnectionRequestedEvent(receiverId, requesterId));
            return saved;
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A request or connection for this user pair already exists", ex);
        }
    }

    public List<ConnectionRequest> incoming(Long userId) {
        return requestRepository.findByReceiverUserIdAndStatusOrderByCreatedAtDesc(userId, ConnectionStatus.PENDING);
    }

    public List<ConnectionRequest> outgoing(Long userId) {
        return requestRepository.findByRequesterUserIdAndStatusOrderByCreatedAtDesc(userId, ConnectionStatus.PENDING);
    }

    public List<ConnectionRequest> connections(Long userId) {
        return requestRepository.findAcceptedConnections(userId, ConnectionStatus.ACCEPTED);
    }

    @Transactional(transactionManager = "transactionManager")
    public ConnectionRequest accept(Long requestId, Long userId) {
        ConnectionRequest request = getPending(requestId, userId, true);
        request.setStatus(ConnectionStatus.ACCEPTED);
        requestRepository.saveAndFlush(request);
        queueGraphSync("UPSERT", request.getRequesterUserId(), request.getReceiverUserId());
        kafkaTemplate.send("connection_accepted_topic", new ConnectionAcceptedEvent(
                request.getRequesterUserId(), request.getReceiverUserId()));
        return request;
    }

    @Transactional(transactionManager = "transactionManager")
    public ConnectionRequest reject(Long requestId, Long userId) {
        ConnectionRequest request = getPending(requestId, userId, true);
        request.setStatus(ConnectionStatus.REJECTED);
        return requestRepository.save(request);
    }

    @Transactional(transactionManager = "transactionManager")
    public void cancel(Long requestId, Long userId) {
        ConnectionRequest request = getPending(requestId, userId, false);
        requestRepository.delete(request);
    }

    @Transactional(transactionManager = "transactionManager")
    public void removeConnection(Long userId, Long otherUserId) {
        long low = Math.min(userId, otherUserId);
        long high = Math.max(userId, otherUserId);
        ConnectionRequest request = requestRepository.findByLowUserIdAndHighUserId(low, high)
                .filter(item -> item.getStatus() == ConnectionStatus.ACCEPTED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found"));
        requestRepository.delete(request);
        queueGraphSync("DELETE", userId, otherUserId);
    }

    private ConnectionRequest getPending(Long requestId, Long userId, boolean mustBeReceiver) {
        ConnectionRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection request not found"));
        boolean correctParty = mustBeReceiver
                ? request.getReceiverUserId().equals(userId)
                : request.getRequesterUserId().equals(userId);
        if (!correctParty || request.getStatus() != ConnectionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request cannot transition from its current state");
        }
        return request;
    }

    private void queueGraphSync(String action, Long userA, Long userB) {
        GraphSyncEvent event = new GraphSyncEvent();
        event.setAction(action);
        event.setUserA(userA);
        event.setUserB(userB);
        graphSyncEventRepository.save(event);
    }
}
