package com.mks.connectBro.posts_service.event;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PostLiked {

    private Long postId;
    private Long likedByUserId;
    private Long ownerUserId;

}
