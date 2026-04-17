package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.Attendance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByUser_IdAndAttendanceDate(Long userId, LocalDate date);

    List<Attendance> findByAttendanceDate(LocalDate date);

    /** Eager user + modifier for admin roster (avoids lazy-init and N+1 when mapping to DTOs). */
    @Query("SELECT DISTINCT a FROM Attendance a JOIN FETCH a.user LEFT JOIN FETCH a.modifiedBy WHERE a.attendanceDate = :date")
    List<Attendance> findByAttendanceDateWithAssociations(@Param("date") LocalDate date);

    List<Attendance> findByUser_IdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
            Long userId, LocalDate start, LocalDate end);

    Page<Attendance> findByUser_IdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
            Long userId, LocalDate start, LocalDate end, Pageable pageable);

    List<Attendance> findByUser_IdOrderByAttendanceDateDesc(Long userId, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT a FROM Attendance a WHERE a.user.id = ?1 AND a.attendanceDate >= ?2 AND a.attendanceDate <= ?3")
    List<Attendance> findForUserInDateRange(Long userId, LocalDate start, LocalDate end);

    List<Attendance> findByAttendanceDateBetweenOrderByAttendanceDateAsc(LocalDate start, LocalDate end);

    @Query("SELECT a FROM Attendance a WHERE a.user.id IN :internIds "
            + "AND (:d IS NULL OR a.attendanceDate = :d) "
            + "AND (:st IS NULL OR a.status = :st)")
    Page<Attendance> searchInInternScope(
            @Param("internIds") List<Long> internIds,
            @Param("d") LocalDate d,
            @Param("st") Attendance.AttendanceStatus st,
            Pageable pageable);
}
