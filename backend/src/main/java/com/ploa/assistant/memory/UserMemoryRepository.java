package com.ploa.assistant.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserMemoryRepository extends JpaRepository<UserMemory, UUID> {

    List<UserMemory> findAllByOrderByCategoryAscMemoryKeyAsc();

    List<UserMemory> findByCategoryIgnoreCase(String category);

    Optional<UserMemory> findByCategoryIgnoreCaseAndMemoryKeyIgnoreCase(String category, String memoryKey);
}
