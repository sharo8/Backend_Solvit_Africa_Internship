package com.solvit.internship_system.repository;

import com.solvit.internship_system.entity.PublicHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {

    boolean existsByHolidayDate(LocalDate holidayDate);

    Optional<PublicHoliday> findByHolidayDate(LocalDate holidayDate);

    List<PublicHoliday> findAllByHolidayDateBetweenOrderByHolidayDateAsc(LocalDate start, LocalDate end);
}
