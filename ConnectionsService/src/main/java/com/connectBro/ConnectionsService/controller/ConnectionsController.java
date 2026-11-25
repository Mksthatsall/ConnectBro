package com.connectBro.ConnectionsService.controller;


import com.connectBro.ConnectionsService.entity.Person;
import com.connectBro.ConnectionsService.auth.AuthContextHolder;
import com.connectBro.ConnectionsService.entity.ConnectionRequest;
import com.connectBro.ConnectionsService.services.ConnectionRequestService;
import com.connectBro.ConnectionsService.services.ConnectionsService;
import com.connectBro.ConnectionsService.services.RecommendationService;
import com.connectBro.ConnectionsService.dto.RecommendationPageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/core")
@RequiredArgsConstructor
@Slf4j
public class ConnectionsController {

    private final ConnectionsService connectionsService;
    private final ConnectionRequestService requestService;
    private final RecommendationService recommendationService;

    @GetMapping("/recommendations")
    public ResponseEntity<RecommendationPageDto> recommendations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String goal) {
        return ResponseEntity.ok(recommendationService.recommend(currentUserId(), page, size, goal));
    }

    @PostMapping("/requests/{targetUserId}")
    public ResponseEntity<ConnectionRequest> sendRequest(@PathVariable Long targetUserId) {
        return ResponseEntity.status(201).body(requestService.send(currentUserId(), targetUserId));
    }

    @GetMapping("/requests/incoming")
    public ResponseEntity<List<ConnectionRequest>> getIncomingRequests() {
        return ResponseEntity.ok(requestService.incoming(currentUserId()));
    }

    @GetMapping("/requests/outgoing")
    public ResponseEntity<List<ConnectionRequest>> getOutgoingRequests() {
        return ResponseEntity.ok(requestService.outgoing(currentUserId()));
    }

    @PostMapping("/requests/{requestId}/accept")
    public ResponseEntity<ConnectionRequest> acceptRequest(@PathVariable Long requestId) {
        return ResponseEntity.ok(requestService.accept(requestId, currentUserId()));
    }

    @PostMapping("/requests/{requestId}/reject")
    public ResponseEntity<ConnectionRequest> rejectRequest(@PathVariable Long requestId) {
        return ResponseEntity.ok(requestService.reject(requestId, currentUserId()));
    }

    @DeleteMapping("/requests/{requestId}")
    public ResponseEntity<Void> cancelRequest(@PathVariable Long requestId) {
        requestService.cancel(requestId, currentUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/connections")
    public ResponseEntity<List<ConnectionRequest>> getConnections() {
        return ResponseEntity.ok(requestService.connections(currentUserId()));
    }

    @DeleteMapping("/connections/{otherUserId}")
    public ResponseEntity<Void> removeConnection(@PathVariable Long otherUserId) {
        requestService.removeConnection(currentUserId(), otherUserId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{userId}/first-degree")
    public ResponseEntity<List<Person>> getFirstDegreeConnections(@PathVariable Long userId){
        if (!isCurrentUser(userId)) return ResponseEntity.status(403).build();
        List<Person> personList = connectionsService.getFirstDegreeConnectionsOfUser(userId);
        return ResponseEntity.ok(personList);
    }

    @GetMapping("/{userId}/second-degree")
    public ResponseEntity<List<Person>> getSecondDegreeConnections(@PathVariable Long userId) {
        if (!isCurrentUser(userId)) return ResponseEntity.status(403).build();
        List<Person> personList = connectionsService.getSecondDegreeConnectionsOfUser(userId);
        return ResponseEntity.ok(personList);
    }

    @GetMapping("/{userId}/third-degree")
    public ResponseEntity<List<Person>> getThirdDegreeConnections(@PathVariable Long userId) {
        if (!isCurrentUser(userId)) return ResponseEntity.status(403).build();
        List<Person> personList = connectionsService.getThirdDegreeConnectionsOfUser(userId);
        return ResponseEntity.ok(personList);
    }

    private boolean isCurrentUser(Long userId) {
        return userId != null && userId.equals(AuthContextHolder.getCurrentUserId());
    }

    private Long currentUserId() {
        Long userId = AuthContextHolder.getCurrentUserId();
        if (userId == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication is required");
        return userId;
    }
}
