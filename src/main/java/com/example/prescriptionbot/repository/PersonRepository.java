package com.example.prescriptionbot.repository;

import com.example.prescriptionbot.entity.Person;
import com.example.prescriptionbot.entity.RelationType;
import com.example.prescriptionbot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PersonRepository extends JpaRepository<Person, Long> {
    List<Person> findByUser(User user);
    Optional<Person> findByUserAndRelationType(User user, RelationType relationType);
}
