package com.mks.connectBro.posts_service.event;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PostCommented {
    private Long ownerUserId;
    private Long commentedByUserId;
    private Long postId;
}
