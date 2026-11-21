package com.connectBro.ConnectionsService.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

@FeignClient(name = "user-service", path = "/profiles")
public interface UserServiceClient {
    @GetMapping("/{userId}")
    PublicProfileDto getPublicProfile(@PathVariable("userId") Long userId);

    @GetMapping("/batch")
    List<PublicProfileDto> getPublicProfiles(@RequestParam("ids") List<Long> userIds);
}
