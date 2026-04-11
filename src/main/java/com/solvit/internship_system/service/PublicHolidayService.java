package com.solvit.internship_system.service;

import com.solvit.internship_system.dto.attendance.CreatePublicHolidayRequestDto;
import com.solvit.internship_system.dto.attendance.PublicHolidayDto;
import com.solvit.internship_system.entity.PublicHoliday;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.PublicHolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicHolidayService {

    private final PublicHolidayRepository publicHolidayRepository;

    @Transactional(readOnly = true)
    public List<PublicHolidayDto> listForYear(int year) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        return publicHolidayRepository.findAllByHolidayDateBetweenOrderByHolidayDateAsc(start, end).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isHoliday(LocalDate date) {
        return date != null && publicHolidayRepository.existsByHolidayDate(date);
    }

    @Transactional
    public PublicHolidayDto create(CreatePublicHolidayRequestDto dto) {
        LocalDate d = dto.getDate();
        if (publicHolidayRepository.existsByHolidayDate(d)) {
            throw new BadRequestException("This date is already a public holiday");
        }
        PublicHoliday h = PublicHoliday.builder()
                .holidayDate(d)
                .name(trimToNull(dto.getName()))
                .build();
        return toDto(publicHolidayRepository.save(h));
    }

    @Transactional
    public void deleteById(Long id) {
        PublicHoliday h = publicHolidayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PublicHoliday", id));
        publicHolidayRepository.delete(h);
    }

    private PublicHolidayDto toDto(PublicHoliday h) {
        return PublicHolidayDto.builder()
                .id(h.getId())
                .date(h.getHolidayDate())
                .name(h.getName())
                .build();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
