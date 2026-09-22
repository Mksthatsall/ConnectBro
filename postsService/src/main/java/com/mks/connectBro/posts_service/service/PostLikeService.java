package com.mks.connectBro.posts_service.service;


import com.mks.connectBro.posts_service.auth.AuthContextHolder;
import com.mks.connectBro.posts_service.entity.Post;
import com.mks.connectBro.posts_service.entity.PostLike;
import com.mks.connectBro.posts_service.event.PostLiked;
import com.mks.connectBro.posts_service.exception.BadRequestException;
import com.mks.connectBro.posts_service.exception.ResourceNotFoundException;
import com.mks.connectBro.posts_service.repository.PostLikeRepository;
import com.mks.connectBro.posts_service.repository.PostRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostLikeService {

    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;
    private final ModelMapper modelMapper;
    private final KafkaTemplate<Long, PostLiked> postLikedKafkaTemplate;



    @Transactional
    public void likePost(Long postId) {
        Long userId= AuthContextHolder.getCurrentUserId();
        log.info("User with Id: {} liking the post with ID: {}", userId, postId);


      Post post=  postRepository.findById(postId).orElseThrow(()-> new ResourceNotFoundException("Post not found with Id: " + postId));

        boolean hasAlreadyLiked= postLikeRepository.existsByUserIdAndPostId(userId, postId);
        if(hasAlreadyLiked) throw new BadRequestException("Cannot like the post again");

        PostLike postLike=new PostLike();
        postLike.setPostId(postId);
        postLike.setUserId(userId);
        postLikeRepository.save(postLike);


        //Send notification to the owner of the post


        PostLiked postLiked= PostLiked.builder()
                .postId(postId)
                .likedByUserId(userId)
                .ownerUserId(post.getUserId())
                .build();

        postLikedKafkaTemplate.send("post_liked_topic", postLiked);

        }



    @Transactional
    public void unlikePost(Long postId) {
        Long userId= AuthContextHolder.getCurrentUserId();
        log.info("User with Id: {} liking the post with ID: {}", userId, postId);


        postRepository.findById(postId).orElseThrow(()-> new ResourceNotFoundException("Post not found with Id: " + postId));

        boolean hasAlreadyLiked= postLikeRepository.existsByUserIdAndPostId(userId, postId);
        if(!hasAlreadyLiked) throw new BadRequestException("Cannot unlike the post you have not liked");



        postLikeRepository.deleteByUserIdAndPostId(userId, postId);


    }
}
