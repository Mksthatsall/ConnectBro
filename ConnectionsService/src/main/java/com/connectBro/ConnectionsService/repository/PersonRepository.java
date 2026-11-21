package com.connectBro.ConnectionsService.repository;

import com.connectBro.ConnectionsService.entity.Person;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.Optional;

public interface PersonRepository extends Neo4jRepository<Person, Long> {
    Optional<Person>  findByUserId(Long userId);

    @Query("MERGE (person:Person {userId: $userId}) " +
            "ON CREATE SET person.name = toString($userId) " +
            "RETURN person")
    Person ensurePerson(Long userId);

    @Query("MATCH (personA:Person {userId: $userA}), (personB:Person {userId: $userB}) " +
            "MERGE (personA)-[:FRIEND]-(personB) " +
            "RETURN personA")
    Person addFriendship(Long userA, Long userB);

    @Query("MATCH (personA:Person {userId: $userA})-[friendship:FRIEND]-(personB:Person {userId: $userB}) " +
            "DELETE friendship " +
            "RETURN personA")
    Person removeFriendship(Long userA, Long userB);

    @Query("MATCH (me:Person {userId: $userId})-[:FRIEND]-(mutual:Person)-[:FRIEND]-(candidate:Person {userId: $candidateId}) " +
            "WHERE mutual.userId <> $userId AND mutual.userId <> $candidateId " +
            "RETURN count(DISTINCT mutual)")
    long countMutualConnections(Long userId, Long candidateId);

    @Query("MATCH (personA:Person)-[:FRIEND]-(personB:Person) " +
            "WHERE personA.userId = $userId " +
            "RETURN DISTINCT personB")
    List<Person> getFirsrDegreeConnections(Long userId);

    @Query("MATCH (personA:Person)-[:FRIEND*2..2]-(personB:Person) " +
            "WHERE personA.userId = $userId " +
            "AND personB.userId <> personA.userId " +
            "AND NOT (personA)-[:FRIEND]-(personB) " +
            "RETURN DISTINCT personB")
    List<Person> getSecondDegreeConnections(Long userId);

    @Query("MATCH (personA:Person)-[:FRIEND*3..3]-(personB:Person) " +
            "WHERE personA.userId = $userId " +
            "AND personB.userId <> personA.userId " +
            "AND NOT (personA)-[:FRIEND*1..2]-(personB) " +
            "RETURN DISTINCT personB")
    List<Person> getThirdDegreeConnections(Long userId);
}
