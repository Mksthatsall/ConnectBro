package com.mks.connectBro.user_service.controller;


import com.mks.connectBro.user_service.dto.LoginRequestDto;
import com.mks.connectBro.user_service.dto.SignupRequestDto;
import com.mks.connectBro.user_service.dto.UserDto;
import com.mks.connectBro.user_service.dto.ProfileUpdateRequestDto;
import com.mks.connectBro.user_service.dto.PublicProfileDto;
import com.mks.connectBro.user_service.service.AuthService;
import com.mks.connectBro.user_service.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
public class UserController {



    private final AuthService authService;
    private final ProfileService profileService;





    @PostMapping("/auth/signup")
    public ResponseEntity<UserDto> signUp(@Valid @RequestBody SignupRequestDto signupRequestDto) {
        UserDto userDto = authService.signUp(signupRequestDto);
        return new ResponseEntity<>(userDto, HttpStatus.CREATED);
    }

    @PostMapping("/auth/login")
    public ResponseEntity<String> login(@RequestBody LoginRequestDto loginRequestDto) {
        String token = authService.login(loginRequestDto);
        return ResponseEntity.ok(token);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getMe(@RequestHeader(name = "X-User-Id", required = false) String userId) {
        return ResponseEntity.ok(profileService.getMe(authenticatedUserId(userId)));
    }

    @PutMapping("/me")
    public ResponseEntity<UserDto> updateMe(@RequestHeader(name = "X-User-Id", required = false) String userId,
                                            @RequestBody ProfileUpdateRequestDto update) {
        return ResponseEntity.ok(profileService.update(authenticatedUserId(userId), update));
    }

    @GetMapping("/profiles/{userId}")
    public ResponseEntity<PublicProfileDto> getProfile(@PathVariable Long userId) {
        return ResponseEntity.ok(profileService.getPublicProfile(userId));
    }

    @GetMapping("/profiles/batch")
    public ResponseEntity<java.util.List<PublicProfileDto>> getProfiles(@RequestParam java.util.List<Long> ids) {
        return ResponseEntity.ok(profileService.getProfiles(ids));
    }

    @GetMapping("/search")
    public ResponseEntity<java.util.List<PublicProfileDto>> search(@RequestParam String q) {
        return ResponseEntity.ok(profileService.search(q));
    }

    private Long authenticatedUserId(String userId) {
        try {
            if (userId == null) throw new NumberFormatException();
            return Long.valueOf(userId);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "A valid authenticated user is required");
        }
    }

}
