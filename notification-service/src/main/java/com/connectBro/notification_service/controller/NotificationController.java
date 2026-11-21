package com.connectBro.notification_service.controller;

import com.connectBro.notification_service.entity.Notification;
import com.connectBro.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/core")
public class NotificationController {
    private final NotificationService service;

    @GetMapping
    public ResponseEntity<List<Notification>> list(@RequestHeader(name = "X-User-Id", required = false) String header) {
        return ResponseEntity.ok(service.list(userId(header)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> unreadCount(@RequestHeader(name = "X-User-Id", required = false) String header) {
        return ResponseEntity.ok(service.unreadCount(userId(header)));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Notification> markRead(@RequestHeader(name = "X-User-Id", required = false) String header,
                                                 @PathVariable Long notificationId) {
        return ResponseEntity.ok(service.markRead(userId(header), notificationId));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@RequestHeader(name = "X-User-Id", required = false) String header) {
        service.markAllRead(userId(header));
        return ResponseEntity.noContent().build();
    }

    private Long userId(String header) {
        try {
            if (header == null) throw new NumberFormatException();
            return Long.valueOf(header);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid authenticated user is required");
        }
    }
}
