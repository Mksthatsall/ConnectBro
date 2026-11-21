package com.connectBro.ConnectionsService.repository;

import com.connectBro.ConnectionsService.entity.ConnectionRequest;
import com.connectBro.ConnectionsService.entity.ConnectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConnectionRequestRepository extends JpaRepository<ConnectionRequest, Long> {
    Optional<ConnectionRequest> findByLowUserIdAndHighUserId(Long lowUserId, Long highUserId);
    List<ConnectionRequest> findByReceiverUserIdAndStatusOrderByCreatedAtDesc(Long receiverUserId, ConnectionStatus status);
    List<ConnectionRequest> findByRequesterUserIdAndStatusOrderByCreatedAtDesc(Long requesterUserId, ConnectionStatus status);
    @Query("select r from ConnectionRequest r where r.status = :status and (r.lowUserId = :userId or r.highUserId = :userId) order by r.createdAt desc")
    List<ConnectionRequest> findAcceptedConnections(@Param("userId") Long userId,
                                                     @Param("status") ConnectionStatus status);
}
