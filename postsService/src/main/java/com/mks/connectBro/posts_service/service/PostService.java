package com.mks.connectBro.posts_service.service;


import com.mks.connectBro.posts_service.auth.AuthContextHolder;
import com.mks.connectBro.posts_service.client.ConnectionServiceClient;
import com.mks.connectBro.posts_service.config.KafkaConfig;
import com.mks.connectBro.posts_service.dto.PersonDto;
import com.mks.connectBro.posts_service.dto.PostCreateRequestDto;
import com.mks.connectBro.posts_service.dto.PostDto;
import com.mks.connectBro.posts_service.entity.Post;
import com.mks.connectBro.posts_service.event.PostCreated;
import com.mks.connectBro.posts_service.exception.ResourceNotFoundException;
import com.mks.connectBro.posts_service.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final ModelMapper modelMapper;
    private final ConnectionServiceClient connectionServiceClient;
    private final KafkaTemplate<Long, PostCreated> postCreatedKafkaTemplate;


    public PostDto createPost(PostCreateRequestDto postCreateRequestDto, Long userId) {
        log.info("Creating post for user with id: {}", userId);
        Post post= modelMapper.map(postCreateRequestDto, Post.class);
        post.setUserId(userId);
        post=postRepository.save(post);


        List <PersonDto> personDtoList= connectionServiceClient.getFirstDegreeConnections(userId);



        for(PersonDto person : personDtoList){ //send notification to each connection
            PostCreated postCreated= PostCreated.builder()
                    .postId(post.getId())
                    .content(post.getContent())
                    .userId(person.getUserId())
                    .ownerUserId(userId)
                    .build();

            postCreatedKafkaTemplate.send("post_created_topics", postCreated);
        }

        return modelMapper.map(post, PostDto.class);



    }

    public List<PostDto> getAllPostsOfUser(Long userId) {
        log.info("Getting all post of user with ID: {}", userId);

        List<Post> postList =postRepository.findByUserId(userId);

        return postList.stream().map((element) -> modelMapper.map(element, PostDto.class)).collect(Collectors.toList());
    }

    public PostDto getPostById(Long postId) {
        log.info("Getting post for user with id: {}", postId);

        Post post = postRepository.findById(postId).orElseThrow(()->new ResourceNotFoundException("Post not found with ID"+ postId));

        return modelMapper.map(post, PostDto.class);
    }
}
