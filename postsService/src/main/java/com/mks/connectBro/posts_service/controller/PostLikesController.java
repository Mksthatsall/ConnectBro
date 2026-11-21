package com.mks.connectBro.posts_service.controller;


import com.mks.connectBro.posts_service.service.PostLikeService;
import com.mks.connectBro.posts_service.dto.PostEngagementDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/likes")
@RequiredArgsConstructor
@RestController
public class PostLikesController {


    private final PostLikeService postLikeService;

    @GetMapping("/{postId}")
    public ResponseEntity<PostEngagementDto> getEngagement(@PathVariable Long postId) {
        return ResponseEntity.ok(postLikeService.getEngagement(postId));
    }

    @PostMapping("/{postId}")
    public ResponseEntity<Void> likePost(@PathVariable Long postId) {
        postLikeService.likePost(postId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> unlikePost(@PathVariable Long postId) {
        postLikeService.unlikePost(postId);
        return ResponseEntity.noContent().build();

    }
}
