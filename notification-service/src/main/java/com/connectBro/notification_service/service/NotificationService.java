package com.connectBro.notification_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.connectBro.notification_service.entity.Notification;
import com.connectBro.notification_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private final NotificationRepository repository;

    @KafkaListener(topics = "post_created_topics")
    public void onPostCreated(JsonNode event) {
        save(event.path("userId").asLong(), event.path("ownerUserId").asLong(), event.path("postId").asLong(),
                "POST", "A connection shared a new post.");
    }

    @KafkaListener(topics = "post_liked_topic")
    public void onPostLiked(JsonNode event) {
        save(event.path("ownerUserId").asLong(), event.path("likedByUserId").asLong(), event.path("postId").asLong(),
                "LIKE", "Someone liked your post.");
    }

    @KafkaListener(topics = "post_commented_topic")
    public void onPostCommented(JsonNode event) {
        save(event.path("ownerUserId").asLong(), event.path("commentedByUserId").asLong(), event.path("postId").asLong(),
                "COMMENT", "Someone commented on your post.");
    }

    @KafkaListener(topics = "connection_request_topic")
    public void onConnectionRequest(JsonNode event) {
        save(event.path("recipientUserId").asLong(), event.path("actorUserId").asLong(), 0,
                "CONNECTION_REQUEST", "You received a connection request.");
    }

    @KafkaListener(topics = "connection_accepted_topic")
    public void onConnectionAccepted(JsonNode event) {
        save(event.path("recipientUserId").asLong(), event.path("actorUserId").asLong(), 0,
                "CONNECTION_ACCEPTED", "Your connection request was accepted.");
    }

    public List<Notification> list(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public long unreadCount(Long userId) {
        return repository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public Notification markRead(Long userId, Long notificationId) {
        Notification notification = repository.findById(notificationId)
                .filter(item -> item.getUserId().equals(userId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notification.setRead(true);
        return repository.save(notification);
    }

    @Transactional
    public void markAllRead(Long userId) {
        repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(item -> !item.isRead()).forEach(item -> item.setRead(true));
    }

    private void save(long recipientId, long actorId, long referenceId, String type, String message) {
        if (recipientId <= 0 || actorId == recipientId) return;
        Notification notification = Notification.builder().userId(recipientId).message(message)
                .referenceId(referenceId).type(type).isRead(false).build();
        repository.save(notification);
        log.debug("Stored {} notification for user {}", type, recipientId);
    }
}
