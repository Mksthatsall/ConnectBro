package com.connectBro.ConnectionsService.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionAcceptedEvent {
    private Long recipientUserId;
    private Long actorUserId;
}
