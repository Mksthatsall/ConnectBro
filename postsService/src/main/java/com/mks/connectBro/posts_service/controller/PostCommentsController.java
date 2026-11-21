package com.mks.connectBro.posts_service.controller;

import com.mks.connectBro.posts_service.dto.CommentCreateRequestDto;
import com.mks.connectBro.posts_service.dto.PostCommentDto;
import com.mks.connectBro.posts_service.service.PostCommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/core/{postId}/comments")
public class PostCommentsController {
    private final PostCommentService commentService;

    @GetMapping
    public ResponseEntity<List<PostCommentDto>> list(@PathVariable Long postId) {
        return ResponseEntity.ok(commentService.list(postId));
    }

    @PostMapping
    public ResponseEntity<PostCommentDto> create(@PathVariable Long postId,
                                                  @RequestBody CommentCreateRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(commentService.create(postId, request));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(@PathVariable Long postId, @PathVariable Long commentId) {
        commentService.delete(postId, commentId);
        return ResponseEntity.noContent().build();
    }
}
