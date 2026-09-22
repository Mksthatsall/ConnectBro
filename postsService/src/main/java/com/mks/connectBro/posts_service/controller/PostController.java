package com.mks.connectBro.posts_service.controller;


import com.mks.connectBro.posts_service.auth.AuthContextHolder;
import com.mks.connectBro.posts_service.dto.PostCreateRequestDto;
import com.mks.connectBro.posts_service.dto.PostDto;
import com.mks.connectBro.posts_service.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/core")
public class PostController {


    private final PostService postService;
    @PostMapping
    public ResponseEntity<PostDto> createPost(@RequestBody PostCreateRequestDto postCreateRequestDto,
                                              HttpServletRequest httpServletRequest, Long userId){



        PostDto postDto= postService.createPost(postCreateRequestDto, 1L);
        return new ResponseEntity<>(postDto, HttpStatus.CREATED);
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostDto> getPost(@PathVariable Long postId){
        Long userId= AuthContextHolder.getCurrentUserId();
        PostDto postDto= postService.getPostById(postId);
        return ResponseEntity.ok(postDto);
    }


    @GetMapping("/users/{userId}/allPosts")
    public ResponseEntity<List<PostDto>> getAllPostsOfUser(@PathVariable Long userId){


       List<PostDto> posts= postService.getAllPostsOfUser(userId);
        return ResponseEntity.ok(posts);
    }



}
