package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE (m.sender.id = ?1 AND m.receiver.id = ?2) OR (m.sender.id = ?2 AND m.receiver.id = ?1) ORDER BY m.createdAt DESC")
    Page<Message> findConversation(Long user1Id, Long user2Id, Pageable pageable);

    Page<Message> findByReceiver_IdAndReadAtIsNullOrderByCreatedAtDesc(Long receiverId, Pageable pageable);

    long countByReceiver_IdAndReadAtIsNull(Long receiverId);
}
