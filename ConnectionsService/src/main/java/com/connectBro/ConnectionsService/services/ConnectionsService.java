package com.connectBro.ConnectionsService.services;


import com.connectBro.ConnectionsService.entity.Person;
import com.connectBro.ConnectionsService.repository.PersonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConnectionsService {

    private final PersonRepository personRepository;

    public List<Person> getFirstDegreeConnectionsOfUser(Long userId){
        log.info("get first degree of user with id: {}", userId);
        try {
            return personRepository.getFirsrDegreeConnections(userId);
        } catch (Exception e) {
            log.error("Failed to fetch first-degree connections (returning empty list): {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    public List<Person> getSecondDegreeConnectionsOfUser(Long userId) {
        log.info("get second degree of user with id: {}", userId);
        try {
            return personRepository.getSecondDegreeConnections(userId);
        } catch (Exception e) {
            log.error("Failed to fetch second-degree connections (returning empty list): {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    public List<Person> getThirdDegreeConnectionsOfUser(Long userId) {
        log.info("get third degree of user with id: {}", userId);
        try {
            return personRepository.getThirdDegreeConnections(userId);
        } catch (Exception e) {
            log.error("Failed to fetch third-degree connections (returning empty list): {}", e.getMessage());
            return java.util.Collections.emptyList();
        }
    }
}
