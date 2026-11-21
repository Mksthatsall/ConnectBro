package com.connectBro.ConnectionsService.services;

import com.connectBro.ConnectionsService.entity.Person;
import com.connectBro.ConnectionsService.repository.PersonRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConnectionsServiceTest {

    @Mock
    private PersonRepository personRepository;

    @InjectMocks
    private ConnectionsService connectionsService;

    @Test
    void shouldReturnSecondDegreeConnections() {
        Person person = new Person();
        person.setUserId(2L);
        person.setName("Second Degree");

        when(personRepository.getSecondDegreeConnections(1L)).thenReturn(List.of(person));

        List<Person> result = connectionsService.getSecondDegreeConnectionsOfUser(1L);

        assertEquals(List.of(person), result);
    }

    @Test
    void shouldReturnThirdDegreeConnections() {
        Person person = new Person();
        person.setUserId(3L);
        person.setName("Third Degree");

        when(personRepository.getThirdDegreeConnections(1L)).thenReturn(List.of(person));

        List<Person> result = connectionsService.getThirdDegreeConnectionsOfUser(1L);

        assertEquals(List.of(person), result);
    }
}
