package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUser_IdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.user.id = ?1 AND n.readAt IS NULL ORDER BY n.createdAt DESC")
    Page<Notification> findUnreadByUserId(Long userId, Pageable pageable);

    long countByUser_IdAndReadAtIsNull(Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = CURRENT_TIMESTAMP WHERE n.user.id = ?1 AND n.readAt IS NULL")
    int markAllAsReadByUserId(Long userId);
}
