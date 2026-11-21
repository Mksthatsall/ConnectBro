package com.connectBro.ConnectionsService.repository;

import com.connectBro.ConnectionsService.entity.GraphSyncEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;

public interface GraphSyncEventRepository extends JpaRepository<GraphSyncEvent, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<GraphSyncEvent> findTop100ByProcessedAtIsNullOrderByCreatedAtAscIdAsc();
}
