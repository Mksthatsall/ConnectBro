package com.connectBro.ConnectionsService.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "connection_graph_outbox", indexes =
        @Index(name = "idx_graph_outbox_pending", columnList = "processed_at,created_at"))
@Getter
@Setter
@NoArgsConstructor
public class GraphSyncEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 16)
    private String action;

    @Column(name = "user_a", nullable = false)
    private Long userA;

    @Column(name = "user_b", nullable = false)
    private Long userB;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
