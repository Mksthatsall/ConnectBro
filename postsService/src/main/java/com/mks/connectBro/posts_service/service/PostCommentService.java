package com.mks.connectBro.posts_service.service;

import com.mks.connectBro.posts_service.auth.AuthContextHolder;
import com.mks.connectBro.posts_service.dto.CommentCreateRequestDto;
import com.mks.connectBro.posts_service.dto.PostCommentDto;
import com.mks.connectBro.posts_service.entity.PostComment;
import com.mks.connectBro.posts_service.entity.Post;
import com.mks.connectBro.posts_service.event.PostCommented;
import com.mks.connectBro.posts_service.exception.BadRequestException;
import com.mks.connectBro.posts_service.exception.ResourceNotFoundException;
import com.mks.connectBro.posts_service.repository.PostCommentRepository;
import com.mks.connectBro.posts_service.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PostCommentService {
    private final PostCommentRepository commentRepository;
    private final PostRepository postRepository;
    private final ModelMapper modelMapper;
    private final KafkaTemplate<Long, PostCommented> postCommentedKafkaTemplate;

    public List<PostCommentDto> list(Long postId) {
        requirePost(postId);
        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(comment -> modelMapper.map(comment, PostCommentDto.class)).toList();
    }

    public PostCommentDto create(Long postId, CommentCreateRequestDto request) {
        Long userId = AuthContextHolder.getCurrentUserId();
        if (request.getContent() == null || request.getContent().isBlank() || request.getContent().length() > 3000) {
            throw new BadRequestException("Comment must contain 1 to 3000 characters");
        }
        Post post = requirePost(postId);
        PostComment comment = new PostComment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(request.getContent().trim());
        PostComment saved = commentRepository.save(comment);
        if (!post.getUserId().equals(userId)) {
            postCommentedKafkaTemplate.send("post_commented_topic", PostCommented.builder()
                    .ownerUserId(post.getUserId()).commentedByUserId(userId).postId(postId).build());
        }
        return modelMapper.map(saved, PostCommentDto.class);
    }

    public void delete(Long postId, Long commentId) {
        Long userId = AuthContextHolder.getCurrentUserId();
        PostComment comment = commentRepository.findById(commentId)
                .filter(item -> item.getPostId().equals(postId))
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
        if (!comment.getUserId().equals(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can delete only your own comments");
        commentRepository.delete(comment);
    }

    private Post requirePost(Long postId) {
        return postRepository.findById(postId).orElseThrow(() -> new ResourceNotFoundException("Post not found"));
    }
}
