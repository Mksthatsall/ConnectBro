package com.connectBro.ConnectionsService.dto;

import java.util.List;

public record RecommendationDto(
        Long userId,
        String name,
        String headline,
        String profilePicture,
        int mutualConnections,
        List<String> sharedSkills,
        List<String> sharedInterests,
        List<String> sharedGoals,
        int degree,
        int score,
        int skillSimilarity,
        int interestSimilarity,
        int goalAlignment,
        int professionalSimilarity,
        List<String> reasons) { }
