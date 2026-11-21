package com.connectBro.ConnectionsService.services;

import com.connectBro.ConnectionsService.client.PublicProfileDto;
import com.connectBro.ConnectionsService.client.UserServiceClient;
import com.connectBro.ConnectionsService.dto.RecommendationDto;
import com.connectBro.ConnectionsService.dto.RecommendationPageDto;
import com.connectBro.ConnectionsService.entity.ConnectionStatus;
import com.connectBro.ConnectionsService.entity.Person;
import com.connectBro.ConnectionsService.repository.ConnectionRequestRepository;
import com.connectBro.ConnectionsService.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {
    private final PersonRepository personRepository;
    private final ConnectionRequestRepository requestRepository;
    private final UserServiceClient userServiceClient;

    @Value("${recommendations.weights.second-degree:25}") private int secondDegreeWeight;
    @Value("${recommendations.weights.third-degree:10}") private int thirdDegreeWeight;
    @Value("${recommendations.weights.mutual-connections:15}") private int mutualWeight;
    @Value("${recommendations.weights.shared-skills:20}") private int skillsWeight;
    @Value("${recommendations.weights.shared-interests:10}") private int interestsWeight;
    @Value("${recommendations.weights.goal-alignment:15}") private int goalsWeight;
    @Value("${recommendations.weights.professional-focus:5}") private int professionalWeight;

    public RecommendationPageDto recommend(Long userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Page must be non-negative and size must be between 1 and 100");
        }
        Set<Long> firstDegree = personRepository.getFirsrDegreeConnections(userId).stream()
                .map(Person::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Integer> degrees = new HashMap<>();
        personRepository.getSecondDegreeConnections(userId).forEach(person -> addCandidate(degrees, person, 2));
        personRepository.getThirdDegreeConnections(userId).forEach(person -> addCandidate(degrees, person, 3));

        Set<Long> excluded = requestRepository.findByRequesterUserIdAndStatusOrderByCreatedAtDesc(userId, ConnectionStatus.PENDING)
                .stream().map(request -> request.getReceiverUserId()).collect(Collectors.toSet());
        requestRepository.findByReceiverUserIdAndStatusOrderByCreatedAtDesc(userId, ConnectionStatus.PENDING)
                .stream().map(request -> request.getRequesterUserId()).forEach(excluded::add);
        requestRepository.findAcceptedConnections(userId, ConnectionStatus.ACCEPTED).forEach(request ->
                excluded.add(request.getLowUserId().equals(userId) ? request.getHighUserId() : request.getLowUserId()));
        degrees.keySet().removeIf(candidate -> candidate.equals(userId) || firstDegree.contains(candidate) || excluded.contains(candidate));
        if (degrees.isEmpty()) return new RecommendationPageDto(List.of(), page, size, 0);

        List<Long> profileIds = new ArrayList<>(degrees.keySet());
        profileIds.add(userId);
        Map<Long, PublicProfileDto> profiles = userServiceClient.getPublicProfiles(profileIds).stream()
                .filter(profile -> profile.getId() != null)
                .collect(Collectors.toMap(PublicProfileDto::getId, profile -> profile, (first, ignored) -> first));
        PublicProfileDto source = profiles.get(userId);
        if (source == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Your profile could not be loaded");

        List<PublicProfileDto> profileCorpus = new ArrayList<>(profiles.values());
        List<String> skillCorpus = profileCorpus.stream().map(PublicProfileDto::getSkills).toList();
        List<String> interestCorpus = profileCorpus.stream().map(PublicProfileDto::getInterests).toList();
        List<String> goalCorpus = profileCorpus.stream().map(PublicProfileDto::getGoals).toList();
        List<String> headlineCorpus = profileCorpus.stream().map(PublicProfileDto::getHeadline).toList();
        List<RecommendationDto> ranked = new ArrayList<>();

        for (Map.Entry<Long, Integer> entry : degrees.entrySet()) {
            Long candidateId = entry.getKey();
            PublicProfileDto profile = profiles.get(candidateId);
            if (profile == null) continue;
            int degree = entry.getValue();
            List<String> sharedSkills = sharedTags(source.getSkills(), profile.getSkills());
            List<String> sharedInterests = sharedTags(source.getInterests(), profile.getInterests());
            List<String> sharedGoals = sharedTags(source.getGoals(), profile.getGoals());
            double skillSimilarity = ProfileSimilarity.cosine(source.getSkills(), profile.getSkills(), skillCorpus);
            double interestSimilarity = ProfileSimilarity.cosine(source.getInterests(), profile.getInterests(), interestCorpus);
            double goalAlignment = ProfileSimilarity.cosine(source.getGoals(), profile.getGoals(), goalCorpus);
            double professionalSimilarity = ProfileSimilarity.cosine(source.getHeadline(), profile.getHeadline(), headlineCorpus);
            long mutualCount = personRepository.countMutualConnections(userId, candidateId);

            double rawScore = (degree == 2 ? secondDegreeWeight : thirdDegreeWeight)
                    + Math.min(mutualCount, 5) / 5.0 * mutualWeight
                    + skillSimilarity * skillsWeight
                    + interestSimilarity * interestsWeight
                    + goalAlignment * goalsWeight
                    + professionalSimilarity * professionalWeight;
            double maximum = secondDegreeWeight + mutualWeight + skillsWeight + interestsWeight + goalsWeight + professionalWeight;
            int score = maximum <= 0 ? 0 : (int) Math.round(100 * rawScore / maximum);
            List<String> reasons = explain(degree, mutualCount, sharedSkills, sharedInterests, sharedGoals,
                    skillSimilarity, interestSimilarity, goalAlignment, professionalSimilarity);

            ranked.add(new RecommendationDto(candidateId, profile.getName(), profile.getHeadline(), profile.getProfilePicture(),
                    (int) Math.min(mutualCount, Integer.MAX_VALUE), sharedSkills, sharedInterests, sharedGoals,
                    degree, clamp(score), percentage(skillSimilarity), percentage(interestSimilarity),
                    percentage(goalAlignment), percentage(professionalSimilarity), reasons));
        }
        ranked.sort(Comparator.comparingInt(RecommendationDto::score).reversed()
                .thenComparingInt(RecommendationDto::degree).thenComparing(RecommendationDto::userId));
        int start = (int) Math.min((long) page * size, ranked.size());
        int end = Math.min(start + size, ranked.size());
        return new RecommendationPageDto(ranked.subList(start, end), page, size, ranked.size());
    }

    private List<String> explain(int degree, long mutualCount, List<String> skills, List<String> interests,
                                 List<String> goals, double skillScore, double interestScore,
                                 double goalScore, double professionalScore) {
        List<String> reasons = new ArrayList<>();
        if (mutualCount > 0) reasons.add("You have " + mutualCount + " mutual connection" + (mutualCount == 1 ? "." : "s."));
        reasons.add(degree == 2 ? "Connected through your second-degree network." : "Connected through your extended professional network.");
        if (!skills.isEmpty()) reasons.add("Shared skills: " + String.join(", ", skills) + ".");
        else if (skillScore >= .35) reasons.add("Your skill profiles are similar (" + percentage(skillScore) + "%).");
        if (!interests.isEmpty()) reasons.add("Shared interests: " + String.join(", ", interests) + ".");
        else if (interestScore >= .35) reasons.add("You have similar professional interests.");
        if (!goals.isEmpty()) reasons.add("Aligned goals: " + String.join(", ", goals) + ".");
        else if (goalScore >= .3) reasons.add("Your current professional goals align.");
        if (professionalScore >= .35) reasons.add("You have a similar professional focus.");
        return reasons;
    }

    private void addCandidate(Map<Long, Integer> degrees, Person person, int degree) {
        if (person.getUserId() != null) degrees.merge(person.getUserId(), degree, Math::min);
    }

    private List<String> sharedTags(String first, String second) {
        Map<String, String> left = normalize(first);
        Map<String, String> right = normalize(second);
        return left.keySet().stream().filter(right::containsKey).map(left::get)
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    private Map<String, String> normalize(String values) {
        Map<String, String> normalized = new TreeMap<>();
        if (values == null) return normalized;
        for (String value : values.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) normalized.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        return normalized;
    }

    private int percentage(double similarity) { return clamp((int) Math.round(similarity * 100)); }
    private int clamp(int value) { return Math.max(0, Math.min(value, 100)); }
}
