package com.connectBro.ConnectionsService.repository;

import com.connectBro.ConnectionsService.entity.Person;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;

import java.util.List;
import java.util.Optional;

public interface PersonRepository extends Neo4jRepository<Person, Long> {
    Optional<Person>  findByUserId(Long userId);

    @Query("MATCH (personA:Person)-[:FRIEND]->(personB:Person)" +
            "WHERE personA.userId = $userId" +
            "RETURN personB;")
    List<Person> getFirsrDegreeConnections(Long userId);



}
