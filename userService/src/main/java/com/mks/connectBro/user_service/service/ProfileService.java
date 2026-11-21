package com.mks.connectBro.user_service.service;

import com.mks.connectBro.user_service.dto.ProfileUpdateRequestDto;
import com.mks.connectBro.user_service.dto.PublicProfileDto;
import com.mks.connectBro.user_service.dto.UserDto;
import com.mks.connectBro.user_service.entity.User;
import com.mks.connectBro.user_service.exception.ResourceNotFoundException;
import com.mks.connectBro.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileService {
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;

    public UserDto getMe(Long userId) {
        return modelMapper.map(findUser(userId), UserDto.class);
    }

    public PublicProfileDto getPublicProfile(Long userId) {
        return modelMapper.map(findUser(userId), PublicProfileDto.class);
    }

    public UserDto update(Long userId, ProfileUpdateRequestDto update) {
        User user = findUser(userId);
        user.setHeadline(update.getHeadline());
        user.setAbout(update.getAbout());
        user.setLocation(update.getLocation());
        user.setProfilePicture(update.getProfilePicture());
        user.setSkills(update.getSkills());
        user.setInterests(update.getInterests());
        user.setGoals(update.getGoals());
        return modelMapper.map(userRepository.save(user), UserDto.class);
    }

    public List<PublicProfileDto> search(String query) {
        String term = query == null ? "" : query.trim();
        if (term.isEmpty()) return List.of();
        return userRepository
                .findTop30ByNameContainingIgnoreCaseOrHeadlineContainingIgnoreCaseOrSkillsContainingIgnoreCaseOrInterestsContainingIgnoreCaseOrGoalsContainingIgnoreCase(
                        term, term, term, term, term)
                .stream()
                .map(user -> modelMapper.map(user, PublicProfileDto.class))
                .toList();
    }

    public List<PublicProfileDto> getProfiles(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        return userRepository.findAllById(userIds).stream()
                .map(user -> modelMapper.map(user, PublicProfileDto.class))
                .toList();
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
