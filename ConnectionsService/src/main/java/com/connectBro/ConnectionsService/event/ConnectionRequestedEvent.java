package com.connectBro.ConnectionsService.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionRequestedEvent {
    private Long recipientUserId;
    private Long actorUserId;
}
