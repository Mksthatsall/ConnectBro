package com.connectBro.ConnectionsService.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "connection_requests", uniqueConstraints =
        @UniqueConstraint(name = "uk_connection_pair", columnNames = {"low_user_id", "high_user_id"}),
        indexes = {
                @Index(name = "idx_connection_receiver_status", columnList = "receiver_user_id,status"),
                @Index(name = "idx_connection_requester_status", columnList = "requester_user_id,status")
        })
@Getter
@Setter
@NoArgsConstructor
public class ConnectionRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "low_user_id", nullable = false)
    private Long lowUserId;

    @Column(name = "high_user_id", nullable = false)
    private Long highUserId;

    @Column(name = "requester_user_id", nullable = false)
    private Long requesterUserId;

    @Column(name = "receiver_user_id", nullable = false)
    private Long receiverUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConnectionStatus status = ConnectionStatus.PENDING;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
