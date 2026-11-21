package com.connectBro.ConnectionsService.services;

import com.connectBro.ConnectionsService.client.PublicProfileDto;
import com.connectBro.ConnectionsService.client.UserServiceClient;
import com.connectBro.ConnectionsService.dto.RecommendationPageDto;
import com.connectBro.ConnectionsService.entity.ConnectionRequest;
import com.connectBro.ConnectionsService.entity.ConnectionStatus;
import com.connectBro.ConnectionsService.entity.Person;
import com.connectBro.ConnectionsService.repository.ConnectionRequestRepository;
import com.connectBro.ConnectionsService.repository.PersonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {
    @Mock private PersonRepository personRepository;
    @Mock private ConnectionRequestRepository requestRepository;
    @Mock private UserServiceClient userServiceClient;
    @InjectMocks private RecommendationService service;

    @BeforeEach
    void weights() {
        ReflectionTestUtils.setField(service, "secondDegreeWeight", 25);
        ReflectionTestUtils.setField(service, "thirdDegreeWeight", 10);
        ReflectionTestUtils.setField(service, "mutualWeight", 15);
        ReflectionTestUtils.setField(service, "skillsWeight", 20);
        ReflectionTestUtils.setField(service, "interestsWeight", 10);
        ReflectionTestUtils.setField(service, "goalsWeight", 15);
        ReflectionTestUtils.setField(service, "professionalWeight", 5);
    }

    @Test
    void recommendsSecondDegreeWithMutualsAndSharedProfileDetails() {
        graph(1L, List.of(2L), List.of(3L), List.of());
        requestsEmpty(1L);
        PublicProfileDto source = profile(1L, "Java, Spring Boot", "Responsible AI, Accessibility",
                "Mentorship, AI collaboration", "Staff software engineer");
        PublicProfileDto candidate = profile(3L, "java, Spring", "responsible AI",
                "Mentoring, AI collaboration", "Senior software engineer");
        when(userServiceClient.getPublicProfiles(anyList())).thenReturn(List.of(source, candidate));
        when(personRepository.countMutualConnections(1L, 3L)).thenReturn(2L);

        RecommendationPageDto result = service.recommend(1L, 0, 20);

        assertEquals(1, result.total());
        assertEquals(2, result.items().getFirst().degree());
        assertEquals(2, result.items().getFirst().mutualConnections());
        assertEquals(List.of("Java"), result.items().getFirst().sharedSkills());
        assertTrue(result.items().getFirst().goalAlignment() > 0);
        assertTrue(result.items().getFirst().professionalSimilarity() > 0);
        assertTrue(result.items().getFirst().score() > 25);
    }

    @Test
    void goalAndSkillFitCanImproveCandidateRanking() {
        graph(1L, List.of(2L), List.of(3L, 4L), List.of());
        requestsEmpty(1L);
        PublicProfileDto source = profile(1L, "Machine learning, Java", "Accessibility",
                "Mentorship, AI collaboration", "Product engineer");
        PublicProfileDto aligned = profile(3L, "ML, Java", "Inclusive design",
                "Mentoring, collaborating on AI", "Software engineer");
        PublicProfileDto unrelated = profile(4L, "Accounting", "Gardening",
                "Changing careers into finance", "Financial analyst");
        when(userServiceClient.getPublicProfiles(anyList())).thenReturn(List.of(source, aligned, unrelated));

        RecommendationPageDto result = service.recommend(1L, 0, 20);

        assertEquals(2, result.total());
        assertEquals(3L, result.items().getFirst().userId());
        assertTrue(result.items().getFirst().goalAlignment() > result.items().get(1).goalAlignment());
        assertTrue(result.items().getFirst().skillSimilarity() > result.items().get(1).skillSimilarity());
        assertTrue(result.items().getFirst().reasons().stream().anyMatch(reason -> reason.contains("goal")));
    }

    @Test
    void excludesPendingAndAcceptedCandidatesAndReturnsEmptyGracefully() {
        graph(1L, List.of(), List.of(3L, 4L, 5L), List.of());
        ConnectionRequest outgoing = request(1L, 3L, ConnectionStatus.PENDING);
        ConnectionRequest incoming = request(4L, 1L, ConnectionStatus.PENDING);
        ConnectionRequest accepted = request(1L, 5L, ConnectionStatus.ACCEPTED);
        when(requestRepository.findByRequesterUserIdAndStatusOrderByCreatedAtDesc(1L, ConnectionStatus.PENDING)).thenReturn(List.of(outgoing));
        when(requestRepository.findByReceiverUserIdAndStatusOrderByCreatedAtDesc(1L, ConnectionStatus.PENDING)).thenReturn(List.of(incoming));
        when(requestRepository.findAcceptedConnections(1L, ConnectionStatus.ACCEPTED)).thenReturn(List.of(accepted));

        RecommendationPageDto result = service.recommend(1L, 0, 20);

        assertTrue(result.items().isEmpty());
        assertEquals(0, result.total());
        verifyNoInteractions(userServiceClient);
    }

    private void graph(Long userId, List<Long> first, List<Long> second, List<Long> third) {
        when(personRepository.getFirsrDegreeConnections(userId)).thenReturn(first.stream().map(this::person).toList());
        when(personRepository.getSecondDegreeConnections(userId)).thenReturn(second.stream().map(this::person).toList());
        when(personRepository.getThirdDegreeConnections(userId)).thenReturn(third.stream().map(this::person).toList());
    }

    private void requestsEmpty(Long userId) {
        when(requestRepository.findByRequesterUserIdAndStatusOrderByCreatedAtDesc(userId, ConnectionStatus.PENDING)).thenReturn(List.of());
        when(requestRepository.findByReceiverUserIdAndStatusOrderByCreatedAtDesc(userId, ConnectionStatus.PENDING)).thenReturn(List.of());
        when(requestRepository.findAcceptedConnections(userId, ConnectionStatus.ACCEPTED)).thenReturn(List.of());
    }

    private Person person(Long userId) { Person person = new Person(); person.setUserId(userId); return person; }

    private PublicProfileDto profile(Long id, String skills, String interests, String goals, String headline) {
        PublicProfileDto profile = new PublicProfileDto(); profile.setId(id); profile.setName("Member " + id);
        profile.setSkills(skills); profile.setInterests(interests); profile.setGoals(goals); profile.setHeadline(headline); return profile;
    }

    private ConnectionRequest request(Long requester, Long receiver, ConnectionStatus status) {
        ConnectionRequest request = new ConnectionRequest(); request.setLowUserId(Math.min(requester, receiver));
        request.setHighUserId(Math.max(requester, receiver)); request.setRequesterUserId(requester);
        request.setReceiverUserId(receiver); request.setStatus(status); return request;
    }
}
