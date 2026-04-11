package com.solvit.internship_system.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.solvit.internship_system.dto.attendance.AdminAttendanceRowDto;
import com.solvit.internship_system.dto.attendance.AttendanceAnalyticsResponseDto;
import com.solvit.internship_system.dto.attendance.AttendanceDailyTrendDto;
import com.solvit.internship_system.dto.attendance.AttendanceInternSummaryDto;
import com.solvit.internship_system.dto.attendance.AttendanceListResponseDto;
import com.solvit.internship_system.dto.attendance.AttendanceStatsDto;
import com.solvit.internship_system.dto.attendance.BulkAttendanceRequestDto;
import com.solvit.internship_system.dto.attendance.PaginationDto;
import com.solvit.internship_system.dto.attendance.UpsertAttendanceRequestDto;
import com.solvit.internship_system.entity.Attendance;
import com.solvit.internship_system.entity.InternProfile;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.repository.AttendanceRepository;
import com.solvit.internship_system.repository.InternProfileRepository;
import com.solvit.internship_system.repository.LeaveRequestRepository;
import com.solvit.internship_system.repository.PublicHolidayRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminAttendanceService {

    private static final ZoneId APP_ZONE = AttendanceCalculationService.APP_ZONE;
    private static final String ENTITY_ATTENDANCE = "Attendance";

    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final InternProfileRepository internProfileRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PublicHolidayRepository publicHolidayRepository;
    private final ConsecutiveAbsenceNotificationService consecutiveAbsenceNotificationService;
    private final AuditService auditService;

    @Transactional
    public List<AdminAttendanceRowDto> getForDate(LocalDate date, Long supervisorId, Attendance.AttendanceStatus status) {
        ensureDerivedRecordsForDate(date);
        return buildRowsForDate(date, supervisorId, status);
    }

    @Transactional
    public AttendanceListResponseDto getList(
            LocalDate date,
            Long supervisorId,
            Attendance.AttendanceStatus status,
            String search,
            int page,
            int limit,
            Role callerRole,
            Long callerUserId
    ) {
        Long effectiveSupervisor = resolveSupervisorFilter(supervisorId, callerRole, callerUserId);
        List<AdminAttendanceRowDto> all = getForDate(date, effectiveSupervisor, status);
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<AdminAttendanceRowDto> filtered = q.isEmpty()
                ? all
                : all.stream()
                .filter(r -> (r.getFirstName() + " " + r.getLastName()).toLowerCase(Locale.ROOT).contains(q)
                        || (r.getUniversityId() != null && r.getUniversityId().toLowerCase(Locale.ROOT).contains(q)))
                .toList();
        int safeLimit = Math.max(1, Math.min(limit, 200));
        int safePage = Math.max(0, page);
        long total = filtered.size();
        int fromIdx = safePage * safeLimit;
        List<AdminAttendanceRowDto> slice = fromIdx >= filtered.size()
                ? List.of()
                : filtered.subList(fromIdx, Math.min(fromIdx + safeLimit, filtered.size()));
        AttendanceStatsDto stats = computeStatsFromRows(all, date);
        return AttendanceListResponseDto.builder()
                .records(slice)
                .stats(stats)
                .pagination(PaginationDto.builder()
                        .total(total)
                        .page(safePage)
                        .limit(safeLimit)
                        .build())
                .build();
    }

    private Long resolveSupervisorFilter(Long requestSupervisorId, Role callerRole, Long callerUserId) {
        if (callerRole == Role.ADMIN || callerRole == Role.HR) {
            return requestSupervisorId;
        }
        if (callerRole == Role.SUPERVISOR) {
            return callerUserId;
        }
        throw new AccessDeniedException("Attendance admin API requires ADMIN, HR, or SUPERVISOR");
    }

    private AttendanceStatsDto computeStatsFromRows(List<AdminAttendanceRowDto> rows, LocalDate date) {
        boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        long total = rows.size();
        long present = rows.stream().filter(r -> r.getStatus() == Attendance.AttendanceStatus.PRESENT).count();
        long absent = rows.stream().filter(r -> r.getStatus() == Attendance.AttendanceStatus.ABSENT).count();
        long late = rows.stream().filter(r -> r.getStatus() == Attendance.AttendanceStatus.LATE).count();
        long excused = rows.stream().filter(r -> r.getStatus() == Attendance.AttendanceStatus.EXCUSED).count();
        long halfDay = rows.stream().filter(r -> r.getStatus() == Attendance.AttendanceStatus.HALF_DAY).count();
        long counted = present + late + halfDay;
        double rate = weekend || total == 0 ? 0.0 : (counted * 100.0 / total);
        return AttendanceStatsDto.builder()
                .date(date)
                .totalInterns(total)
                .present(present)
                .absent(absent)
                .late(late)
                .excused(excused)
                .halfDay(halfDay)
                .attendanceRatePercent(rate)
                .build();
    }

    @Transactional
    public AttendanceStatsDto stats(LocalDate date, Long supervisorId, Role callerRole, Long callerUserId) {
        Long effective = resolveSupervisorFilter(supervisorId, callerRole, callerUserId);
        List<AdminAttendanceRowDto> rows = getForDate(date, effective, null);
        return computeStatsFromRows(rows, date);
    }

    /**
     * Analytics snapshot without running end-of-day auto-mark (read-only).
     */
    public List<AdminAttendanceRowDto> listRowsForAnalytics(LocalDate date, Long supervisorId, Role callerRole, Long callerUserId) {
        Long effective = resolveSupervisorFilter(supervisorId, callerRole, callerUserId);
        return buildRowsForDate(date, effective, null);
    }

    @Transactional(readOnly = true)
    public AttendanceAnalyticsResponseDto getAnalytics(
            LocalDate from,
            LocalDate to,
            Long supervisorId,
            boolean includeCompleted,
            Role callerRole,
            Long callerUserId
    ) {
        if (from == null || to == null) {
            throw new BadRequestException("from and to are required");
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("to must be on or after from");
        }
        Long effective = resolveSupervisorFilter(supervisorId, callerRole, callerUserId);
        LocalDate today = AttendanceCalculationService.todayKigali();

        List<AttendanceDailyTrendDto> daily = new ArrayList<>();
        Map<Attendance.AttendanceStatus, Long> histogram = new EnumMap<>(Attendance.AttendanceStatus.class);

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            List<AdminAttendanceRowDto> rows = listRowsForAnalytics(d, effective, callerRole, callerUserId);
            AttendanceStatsDto st = computeStatsFromRows(rows, d);
            daily.add(AttendanceDailyTrendDto.builder()
                    .date(d)
                    .totalInterns(st.getTotalInterns())
                    .present(st.getPresent())
                    .absent(st.getAbsent())
                    .late(st.getLate())
                    .excused(st.getExcused())
                    .attendanceRatePercent(st.getAttendanceRatePercent())
                    .build());
            for (AdminAttendanceRowDto r : rows) {
                if (r.getStatus() != null) {
                    histogram.merge(r.getStatus(), 1L, Long::sum);
                }
            }
        }

        List<AttendanceInternSummaryDto> summaries = new ArrayList<>();
        List<User> interns = listInternUsers(effective);
        for (User u : interns) {
            if (u.getRole() != Role.INTERN) {
                continue;
            }
            InternProfile ip = internProfileRepository.findByUser_Id(u.getId()).orElse(null);
            if (ip == null) {
                continue;
            }
            String phase = InternshipAttendanceRules.computeInternshipStatus(ip, today);
            if (!includeCompleted && "COMPLETED".equals(phase)) {
                continue;
            }
            summaries.add(buildInternSummary(u, ip, from, to, phase));
        }

        if (includeCompleted) {
            Set<Long> seen = summaries.stream().map(AttendanceInternSummaryDto::getInternId).collect(Collectors.toSet());
            List<User> allInterns = userRepository.findByRoleAndActiveTrue(Role.INTERN);
            for (User u : allInterns) {
                if (seen.contains(u.getId())) {
                    continue;
                }
                InternProfile ip = internProfileRepository.findByUser_Id(u.getId()).orElse(null);
                if (ip == null) {
                    continue;
                }
                if (!"COMPLETED".equals(InternshipAttendanceRules.computeInternshipStatus(ip, today))) {
                    continue;
                }
                if (effective != null && !Objects.equals(ip.getSupervisorUserId(), effective)) {
                    continue;
                }
                AttendanceInternSummaryDto row = buildInternSummary(u, ip, from, to, "COMPLETED");
                if (row.getExpectedWorkdays() > 0) {
                    summaries.add(row);
                }
            }
        }

        summaries.sort(Comparator.comparing(AttendanceInternSummaryDto::getLastName, Comparator.nullsLast(String::compareToIgnoreCase)));

        return AttendanceAnalyticsResponseDto.builder()
                .dailyTrend(daily)
                .byIntern(summaries)
                .statusHistogram(histogram)
                .build();
    }

    private AttendanceInternSummaryDto buildInternSummary(User u, InternProfile ip, LocalDate from, LocalDate to, String phase) {
        long expected = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (InternshipAttendanceRules.eligibleForAttendanceOnDate(ip, d) && !publicHolidayRepository.existsByHolidayDate(d)) {
                expected++;
            }
        }
        List<Attendance> atts = attendanceRepository.findForUserInDateRange(u.getId(), from, to);
        long counted = atts.stream()
                .filter(a -> InternshipAttendanceRules.isWorkday(a.getAttendanceDate()))
                .filter(a -> InternshipAttendanceRules.isWithinContract(ip, a.getAttendanceDate()))
                .filter(a -> a.getStatus() != Attendance.AttendanceStatus.ABSENT && a.getStatus() != Attendance.AttendanceStatus.PENDING)
                .count();
        double rate = expected > 0 ? (counted * 100.0 / expected) : 0;
        Long supId = ip.getSupervisorUserId();
        String supName = supId != null
                ? userRepository.findById(supId).map(s -> s.getFirstName() + " " + s.getLastName()).orElse(null)
                : null;
        return AttendanceInternSummaryDto.builder()
                .internId(u.getId())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .supervisorName(supName)
                .contractPhase(phase)
                .expectedWorkdays(expected)
                .countedPresentDays(counted)
                .attendanceRatePercent(rate)
                .build();
    }

    @Transactional
    public List<AdminAttendanceRowDto> getForDateRange(LocalDate from, LocalDate to, Long supervisorId, Role callerRole, Long callerUserId) {
        Long effective = resolveSupervisorFilter(supervisorId, callerRole, callerUserId);
        List<AdminAttendanceRowDto> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            out.addAll(getForDate(d, effective, null));
        }
        return out;
    }

    @Transactional
    public AdminAttendanceRowDto upsert(UpsertAttendanceRequestDto dto, Long actorUserId, Role role) {
        validateDateNotFuture(dto.getDate());
        User intern = userRepository.findById(dto.getInternId()).orElseThrow(() -> new ResourceNotFoundException("User", dto.getInternId()));
        if (intern.getRole() != Role.INTERN) {
            throw new BadRequestException("Selected user is not an intern");
        }
        assertInternAccess(role, actorUserId, dto.getInternId());

        Optional<Attendance> existing = attendanceRepository.findByUser_IdAndAttendanceDate(dto.getInternId(), dto.getDate());
        return existing.map(a -> update(a.getId(), dto, actorUserId, role)).orElseGet(() -> createNew(dto, actorUserId, role));
    }

    @Transactional
    public AdminAttendanceRowDto createNew(UpsertAttendanceRequestDto dto, Long actorUserId, Role role) {
        validateDateNotFuture(dto.getDate());
        assertInternshipAllowsAdminDate(dto.getInternId(), dto.getDate());
        User intern = userRepository.findById(dto.getInternId()).orElseThrow(() -> new ResourceNotFoundException("User", dto.getInternId()));
        if (intern.getRole() != Role.INTERN) {
            throw new BadRequestException("Selected user is not an intern");
        }
        assertInternAccess(role, actorUserId, dto.getInternId());

        if (attendanceRepository.findByUser_IdAndAttendanceDate(dto.getInternId(), dto.getDate()).isPresent()) {
            throw new BadRequestException("Attendance already exists for this intern on the selected date");
        }

        if (dto.getModificationReason() == null || dto.getModificationReason().isBlank()) {
            throw new BadRequestException("Modification reason (motif) is required");
        }

        Attendance a = Attendance.builder()
                .user(intern)
                .attendanceDate(dto.getDate())
                .checkInAt(toInstant(dto.getDate(), dto.getCheckInTime()))
                .checkOutAt(toInstant(dto.getDate(), dto.getCheckOutTime()))
                .notes(composeNotesForCreate(actorUserId, dto.getModificationReason(), normalize(dto.getNotes())))
                .manualEntry(dto.getManualEntry() != null ? dto.getManualEntry() : true)
                .modifiedBy(actorUserId != null ? userRepository.getReferenceById(actorUserId) : null)
                .excused(resolveExcusedFlag(dto))
                .excuseReason(normalize(dto.getExcuseReason()))
                .build();

        applyApprovedLeaveAndRecalc(a, dto.getDate());

        validateTimes(a.getCheckInAt(), a.getCheckOutAt());

        a = saveAttendanceWithStreakCheck(a);
        auditService.log(actorUserId, "UPDATE", ENTITY_ATTENDANCE, a.getId(), null, "created_by_admin", null, null);
        return toRow(a);
    }

    @Transactional
    public AdminAttendanceRowDto update(Long id, UpsertAttendanceRequestDto dto, Long actorUserId, Role role) {
        Attendance a = attendanceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Attendance", id));
        validateDateNotFuture(dto.getDate());
        assertInternshipAllowsAdminDate(dto.getInternId(), dto.getDate());
        if (!Objects.equals(a.getAttendanceDate(), dto.getDate())) {
            throw new BadRequestException("Date cannot be changed for an attendance record");
        }
        if (!Objects.equals(a.getUser().getId(), dto.getInternId())) {
            throw new BadRequestException("Intern cannot be changed for an attendance record");
        }
        assertInternAccess(role, actorUserId, dto.getInternId());

        String oldSnapshot = snapshot(a);
        String previousNotes = normalize(a.getNotes());

        if (dto.getModificationReason() == null || dto.getModificationReason().isBlank()) {
            throw new BadRequestException("Modification reason (motif) is required for attendance changes");
        }

        Attendance.AttendanceStatus oldStatus = a.getStatus();
        Instant oldIn = a.getCheckInAt();
        Instant oldOut = a.getCheckOutAt();

        Instant nextIn = toInstant(dto.getDate(), dto.getCheckInTime());
        Instant nextOut = toInstant(dto.getDate(), dto.getCheckOutTime());

        a.setCheckInAt(nextIn);
        a.setCheckOutAt(nextOut);
        a.setExcused(resolveExcusedFlag(dto));
        a.setExcuseReason(normalize(dto.getExcuseReason()));
        if (dto.getManualEntry() != null) {
            a.setManualEntry(dto.getManualEntry());
        }
        a.setModifiedBy(actorUserId != null ? userRepository.getReferenceById(actorUserId) : null);

        applyApprovedLeaveAndRecalc(a, dto.getDate());

        validateTimes(a.getCheckInAt(), a.getCheckOutAt());

        boolean overrideNeedsNotes = requiresNotesForOverride(
                oldStatus,
                a.getStatus(),
                oldIn,
                oldOut,
                nextIn,
                nextOut
        );
        String nextNotes = normalize(dto.getNotes());
        if (overrideNeedsNotes && (nextNotes == null || nextNotes.isBlank())) {
            throw new BadRequestException("Additional detail notes are required when admin overrides status or times");
        }

        a.setNotes(composeNotesWithAudit(actorUserId, dto.getModificationReason(), nextNotes, previousNotes));

        a = saveAttendanceWithStreakCheck(a);
        auditService.log(actorUserId, "UPDATE", ENTITY_ATTENDANCE, a.getId(), oldSnapshot, snapshot(a), null, null);
        return toRow(a);
    }

    private boolean requiresNotesForOverride(
            Attendance.AttendanceStatus oldStatus,
            Attendance.AttendanceStatus newStatus,
            Instant oldCheckIn,
            Instant oldCheckOut,
            Instant newCheckIn,
            Instant newCheckOut
    ) {
        if (newStatus != null && oldStatus != null && newStatus != oldStatus) {
            return true;
        }
        if (!Objects.equals(oldCheckIn, newCheckIn)) {
            return true;
        }
        if (!Objects.equals(oldCheckOut, newCheckOut)) {
            return true;
        }
        return false;
    }

    private boolean resolveExcusedFlag(UpsertAttendanceRequestDto dto) {
        if (Boolean.TRUE.equals(dto.getExcused())) {
            return true;
        }
        return dto.getStatus() == Attendance.AttendanceStatus.EXCUSED;
    }

    private void applyApprovedLeaveAndRecalc(Attendance a, LocalDate date) {
        boolean hasApprovedLeave = hasApprovedLeave(a.getUser().getId(), date);
        if (hasApprovedLeave) {
            a.setExcused(true);
        }
        applyRecalculatedFields(a, date);
    }

    private void applyRecalculatedFields(Attendance a, LocalDate date) {
        boolean leaveExcused = hasApprovedLeave(a.getUser().getId(), date);
        boolean excused = a.isExcused() || leaveExcused;
        if (leaveExcused) {
            a.setExcused(true);
        }
        Attendance.AttendanceStatus st = AttendanceCalculationService.calculateStatus(
                a.getCheckInAt(), a.getCheckOutAt(), excused);
        a.setStatus(st);
        a.setDurationMinutes(AttendanceCalculationService.calcDurationMinutes(a.getCheckInAt(), a.getCheckOutAt()));
    }

    private boolean hasApprovedLeave(Long internId, LocalDate date) {
        return !leaveRequestRepository
                .findByUser_IdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        internId,
                        com.solvit.internship_system.entity.LeaveRequest.LeaveStatus.APPROVED,
                        date,
                        date
                )
                .isEmpty();
    }

    @Transactional
    public void delete(Long id) {
        Attendance a = attendanceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Attendance", id));
        String snap = snapshot(a);
        attendanceRepository.delete(a);
        auditService.log(null, "DELETE", ENTITY_ATTENDANCE, id, snap, null, null, null);
    }

    @Transactional
    public List<AdminAttendanceRowDto> bulk(BulkAttendanceRequestDto dto, Long actorUserId, Role role) {
        validateDateNotFuture(dto.getDate());
        if (dto.getModificationReason() == null || dto.getModificationReason().isBlank()) {
            throw new BadRequestException("Bulk attendance requires a modification reason (motif)");
        }
        List<Long> targets = dto.getInternIds();
        if (targets == null || targets.isEmpty()) {
            Long sup = resolveSupervisorFilter(null, role, actorUserId);
            targets = eligibleInternsForAttendance(dto.getDate(), sup).stream().map(User::getId).toList();
        }
        List<AdminAttendanceRowDto> out = new ArrayList<>();
        String noteText = normalize(dto.getNotes());
        for (Long internId : targets) {
            UpsertAttendanceRequestDto u = new UpsertAttendanceRequestDto();
            u.setInternId(internId);
            u.setDate(dto.getDate());
            u.setStatus(dto.getStatus());
            u.setModificationReason(dto.getModificationReason());
            u.setNotes(noteText != null ? noteText : "Bulk attendance");
            u.setManualEntry(true);
            if (dto.getStatus() == Attendance.AttendanceStatus.EXCUSED) {
                u.setExcused(true);
            }
            if (dto.getStatus() == Attendance.AttendanceStatus.PRESENT) {
                u.setCheckInTime(LocalTime.of(8, 0));
                u.setCheckOutTime(LocalTime.of(17, 0));
            }
            out.add(upsert(u, actorUserId, role));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<AdminAttendanceRowDto> internMonthlyHistory(Long internId, int month, int year, Role role, Long callerUserId) {
        User intern = userRepository.findById(internId).orElseThrow(() -> new ResourceNotFoundException("User", internId));
        if (intern.getRole() != Role.INTERN) {
            throw new BadRequestException("User is not an intern");
        }
        assertInternAccess(role, callerUserId, internId);
        if (month < 1 || month > 12) {
            throw new BadRequestException("Invalid month");
        }
        YearMonth ym = YearMonth.of(year, month);
        Optional<InternProfile> ipOpt = internProfileRepository.findByUser_Id(internId);
        return attendanceRepository.findForUserInDateRange(internId, ym.atDay(1), ym.atEndOfMonth())
                .stream()
                .filter(a -> InternshipAttendanceRules.isWorkday(a.getAttendanceDate()))
                .filter(a -> ipOpt.isEmpty() || InternshipAttendanceRules.isWithinContract(ipOpt.get(), a.getAttendanceDate()))
                .map(this::toRow)
                .toList();
    }

    @Transactional
    public byte[] exportCsv(LocalDate from, LocalDate to, Long supervisorId, Attendance.AttendanceStatus status, Role role, Long callerUserId) {
        Long effective = resolveSupervisorFilter(supervisorId, role, callerUserId);
        if (from == null || to == null) {
            throw new BadRequestException("from and to are required");
        }
        if (to.isBefore(from)) {
            throw new BadRequestException("to must be on/after from");
        }
        if (from.isAfter(LocalDate.now().plusDays(1))) {
            throw new BadRequestException("from cannot be in the far future");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Name,University ID,Date,Check-In,Check-Out,Status,Duration,Supervisor,Notes\n");
        LocalDate d = from;
        while (!d.isAfter(to)) {
            List<AdminAttendanceRowDto> rows = getForDate(d, effective, status);
            for (AdminAttendanceRowDto r : rows) {
                String name = r.getFirstName() + " " + r.getLastName();
                String checkIn = r.getCheckInAt() != null ? fmtTime(r.getCheckInAt()) : "";
                String checkOut = r.getCheckOutAt() != null ? fmtTime(r.getCheckOutAt()) : "";
                String duration = formatDurationHuman(r.getDurationMinutes(), r.getCheckInAt(), r.getCheckOutAt());
                sb.append(csv(name)).append(',')
                        .append(csv(nvl(r.getUniversityId()))).append(',')
                        .append(csv(String.valueOf(r.getDate()))).append(',')
                        .append(csv(checkIn)).append(',')
                        .append(csv(checkOut)).append(',')
                        .append(csv(String.valueOf(r.getStatus()))).append(',')
                        .append(csv(duration)).append(',')
                        .append(csv(nvl(r.getSupervisorName()))).append(',')
                        .append(csv(nvl(r.getNotes())))
                        .append('\n');
            }
            d = d.plusDays(1);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public byte[] exportPdf(LocalDate from, LocalDate to, Long supervisorId, Attendance.AttendanceStatus status, Role role, Long callerUserId) {
        byte[] csvStyleData = exportCsv(from, to, supervisorId, status, role, callerUserId);
        String csv = new String(csvStyleData, StandardCharsets.UTF_8);
        String[] lines = csv.split("\n", -1);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(doc, baos);
            doc.open();
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            doc.add(new Paragraph("SOLVIT Africa — Attendance Export", titleFont));
            doc.add(new Paragraph(
                    "Period: " + from + " to " + to + " | Filters: supervisor=" + (supervisorId != null ? supervisorId : "all")
                            + ", status=" + (status != null ? status : "all"),
                    metaFont
            ));
            doc.add(new Paragraph(" ", metaFont));

            if (lines.length > 0) {
                String[] headers = lines[0].split(",", -1);
                PdfPTable table = new PdfPTable(headers.length);
                table.setWidthPercentage(100);
                for (String h : headers) {
                    PdfPCell c = new PdfPCell(new Phrase(stripQuotes(h), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8)));
                    c.setBackgroundColor(new Color(230, 240, 255));
                    c.setHorizontalAlignment(Element.ALIGN_CENTER);
                    table.addCell(c);
                }
                for (int i = 1; i < lines.length; i++) {
                    if (lines[i].isBlank()) {
                        continue;
                    }
                    String[] cols = splitCsvLine(lines[i]);
                    for (String col : cols) {
                        PdfPCell cell = new PdfPCell(new Phrase(col, FontFactory.getFont(FontFactory.HELVETICA, 7)));
                        table.addCell(cell);
                    }
                }
                doc.add(table);
            }
            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new BadRequestException("PDF export failed: " + e.getMessage());
        }
    }

    /** Minimal CSV line split for PDF (handles quoted fields). */
    private String[] splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQ = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQ = !inQ;
            } else if (c == ',' && !inQ) {
                out.add(stripQuotes(cur.toString()));
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(stripQuotes(cur.toString()));
        return out.toArray(new String[0]);
    }

    private String stripQuotes(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) {
            return t.substring(1, t.length() - 1).replace("\"\"", "\"");
        }
        return t;
    }

    /**
     * Invoked by cron at 18:30 Africa/Kigali: create ABSENT rows for eligible interns with no attendance for today.
     */
    @Transactional
    public void runEndOfDayAutoMark() {
        ensureDerivedRecordsForDate(AttendanceCalculationService.todayKigali());
    }

    private void ensureDerivedRecordsForDate(LocalDate date) {
        if (publicHolidayRepository.existsByHolidayDate(date)) {
            return;
        }
        ensureLeaveExcused(date);
        if (shouldAutoMarkAbsentForDate(date)) {
            markAbsentWhereMissing(date);
        }
    }

    private boolean shouldAutoMarkAbsentForDate(LocalDate date) {
        ZonedDateTime now = ZonedDateTime.now(APP_ZONE);
        ZonedDateTime cutoff = date.atTime(AttendanceCalculationService.autoAbsentCutoffLocal()).atZone(APP_ZONE);
        return !now.isBefore(cutoff);
    }

    private void ensureLeaveExcused(LocalDate date) {
        for (User intern : eligibleInternsForAttendance(date, null)) {
            if (attendanceRepository.findByUser_IdAndAttendanceDate(intern.getId(), date).isPresent()) {
                continue;
            }
            if (!hasApprovedLeave(intern.getId(), date)) {
                continue;
            }
            Attendance a = Attendance.builder()
                    .user(intern)
                    .attendanceDate(date)
                    .manualEntry(false)
                    .notes("Auto-marked EXCUSED (approved leave)")
                    .excused(true)
                    .build();
            applyRecalculatedFields(a, date);
            saveAttendanceWithStreakCheck(a);
        }
    }

    private void markAbsentWhereMissing(LocalDate date) {
        List<User> interns = eligibleInternsForAttendance(date, null);
        Set<Long> existing = attendanceRepository.findByAttendanceDate(date).stream()
                .map(a -> a.getUser().getId())
                .collect(Collectors.toSet());
        for (User intern : interns) {
            if (existing.contains(intern.getId())) {
                continue;
            }
            if (hasApprovedLeave(intern.getId(), date)) {
                continue;
            }
            Attendance a = Attendance.builder()
                    .user(intern)
                    .attendanceDate(date)
                    .manualEntry(false)
                    .notes("Auto-marked ABSENT (no attendance by 18:30 Africa/Kigali)")
                    .build();
            applyRecalculatedFields(a, date);
            saveAttendanceWithStreakCheck(a);
        }
    }

    private Attendance saveAttendanceWithStreakCheck(Attendance a) {
        a = attendanceRepository.save(a);
        consecutiveAbsenceNotificationService.maybeNotifyOnAbsentStreak(a);
        return a;
    }

    private List<AdminAttendanceRowDto> buildRowsForDate(LocalDate date, Long supervisorId, Attendance.AttendanceStatus status) {
        List<User> interns = eligibleInternsForAttendance(date, supervisorId);

        Map<Long, Attendance> attendanceByUser = attendanceRepository.findByAttendanceDate(date).stream()
                .collect(Collectors.toMap(a -> a.getUser().getId(), a -> a, (a1, a2) -> a1));

        List<AdminAttendanceRowDto> rows = new ArrayList<>();
        for (User intern : interns) {
            InternProfile ip = internProfileRepository.findByUser_Id(intern.getId()).orElse(null);
            Long supId = ip != null ? ip.getSupervisorUserId() : null;
            String supName = supId != null
                    ? userRepository.findById(supId).map(u -> u.getFirstName() + " " + u.getLastName()).orElse(null)
                    : null;

            Attendance a = attendanceByUser.get(intern.getId());
            if (a != null && status != null && a.getStatus() != status) {
                continue;
            }
            if (a == null) {
                Attendance.AttendanceStatus s = Attendance.AttendanceStatus.PENDING;
                if (status != null && s != status) {
                    continue;
                }
                rows.add(AdminAttendanceRowDto.builder()
                        .id(null)
                        .internId(intern.getId())
                        .firstName(intern.getFirstName())
                        .lastName(intern.getLastName())
                        .profilePhotoUrl(intern.getProfilePhotoUrl())
                        .universityId(intern.getUniversityId())
                        .supervisorId(supId)
                        .supervisorName(supName)
                        .date(date)
                        .checkInAt(null)
                        .checkOutAt(null)
                        .status(s)
                        .durationMinutes(null)
                        .excused(false)
                        .excuseReason(null)
                        .notes(null)
                        .manualEntry(false)
                        .build());
            } else {
                rows.add(toRow(a));
            }
        }

        if (supervisorId != null) {
            rows = rows.stream().filter(r -> Objects.equals(r.getSupervisorId(), supervisorId)).toList();
        }
        return rows;
    }

    private void assertInternAccess(Role role, Long currentUserId, Long internId) {
        if (role == Role.ADMIN || role == Role.HR) {
            return;
        }
        if (role == Role.SUPERVISOR) {
            InternProfile ip = internProfileRepository.findByUser_Id(internId)
                    .orElseThrow(() -> new BadRequestException("Intern profile not found"));
            if (!Objects.equals(ip.getSupervisorUserId(), currentUserId)) {
                throw new AccessDeniedException("Not authorized for this intern");
            }
        }
    }

    private List<User> listInternUsers(Long supervisorId) {
        if (supervisorId == null) {
            return userRepository.findByRoleAndActiveTrue(Role.INTERN);
        }
        List<Long> internIds = internProfileRepository.findBySupervisorUserId(supervisorId).stream()
                .map(ip -> ip.getUser().getId())
                .toList();
        if (internIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllById(internIds).stream()
                .filter(u -> u.getRole() == Role.INTERN && u.isActive())
                .toList();
    }

    /** Weekdays only; intern must have profile dates and date within [start, end]. Public holidays excluded. */
    private List<User> eligibleInternsForAttendance(LocalDate date, Long supervisorId) {
        List<User> base = listInternUsers(supervisorId);
        if (!InternshipAttendanceRules.isWorkday(date) || publicHolidayRepository.existsByHolidayDate(date)) {
            return List.of();
        }
        return base.stream()
                .filter(u -> {
                    InternProfile ip = internProfileRepository.findByUser_Id(u.getId()).orElse(null);
                    return ip != null && InternshipAttendanceRules.eligibleForAttendanceOnDate(ip, date);
                })
                .toList();
    }

    private void assertInternshipAllowsAdminDate(Long internId, LocalDate date) {
        InternProfile ip = internProfileRepository.findByUser_Id(internId).orElse(null);
        if (ip == null || !InternshipAttendanceRules.eligibleForAttendanceOnDate(ip, date)) {
            throw new BadRequestException("Date must be a weekday within the intern's internship contract");
        }
    }

    private void validateDateNotFuture(LocalDate date) {
        if (date == null) {
            throw new BadRequestException("date is required");
        }
        if (date.isAfter(AttendanceCalculationService.todayKigali())) {
            throw new BadRequestException("Date cannot be in the future");
        }
    }

    private Instant toInstant(LocalDate date, LocalTime time) {
        if (date == null || time == null) {
            return null;
        }
        return ZonedDateTime.of(date, time, APP_ZONE).toInstant();
    }

    private void validateTimes(Instant checkInAt, Instant checkOutAt) {
        if (checkInAt != null && checkOutAt != null && !checkOutAt.isAfter(checkInAt)) {
            throw new BadRequestException("Check-out time must be after check-in time");
        }
    }

    private String fmtTime(Instant i) {
        return DateTimeFormatter.ofPattern("HH:mm").withZone(APP_ZONE).format(i);
    }

    private String formatDurationHuman(Integer durationMinutes, Instant in, Instant out) {
        if (durationMinutes != null) {
            long h = durationMinutes / 60;
            long m = durationMinutes % 60;
            return String.format(Locale.ROOT, "%dh %02dm", h, m);
        }
        if (in != null && out != null && out.isAfter(in)) {
            long mins = Duration.between(in, out).toMinutes();
            return String.format(Locale.ROOT, "%dh %02dm", mins / 60, mins % 60);
        }
        return "";
    }

    private String snapshot(Attendance a) {
        return String.format(Locale.ROOT,
                "{\"id\":%d,\"userId\":%d,\"date\":\"%s\",\"checkInAt\":\"%s\",\"checkOutAt\":\"%s\",\"status\":\"%s\",\"notes\":%s,\"manualEntry\":%s,\"excused\":%s}",
                a.getId(),
                a.getUser() != null ? a.getUser().getId() : null,
                a.getAttendanceDate(),
                a.getCheckInAt(),
                a.getCheckOutAt(),
                a.getStatus(),
                a.getNotes() != null ? "\"" + a.getNotes().replace("\"", "\\\"") + "\"" : null,
                a.isManualEntry(),
                a.isExcused());
    }

    private String csv(String s) {
        if (s == null) {
            return "";
        }
        String escaped = s.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    private String normalize(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private String formatAuditLine(Long actorUserId, String modificationReason) {
        User actor = actorUserId != null ? userRepository.findById(actorUserId).orElse(null) : null;
        String who = actor != null
                ? actor.getFirstName() + " " + actor.getLastName() + " [id=" + actor.getId() + "]"
                : ("user#" + actorUserId);
        String when = ZonedDateTime.now(APP_ZONE).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return "[Audit " + when + " Africa/Kigali] By " + who + " — Motif: " + modificationReason.trim();
    }

    private String composeNotesForCreate(Long actorUserId, String modificationReason, String extraNotes) {
        String audit = formatAuditLine(actorUserId, modificationReason);
        if (extraNotes != null && !extraNotes.isBlank()) {
            return audit + "\nDetail: " + extraNotes;
        }
        return audit;
    }

    private String composeNotesWithAudit(Long actorUserId, String modificationReason, String extraNotes, String previousNotes) {
        String audit = formatAuditLine(actorUserId, modificationReason);
        StringBuilder sb = new StringBuilder(audit);
        if (extraNotes != null && !extraNotes.isBlank()) {
            sb.append("\nDetail: ").append(extraNotes);
        }
        if (previousNotes != null && !previousNotes.isBlank()) {
            sb.append("\n— Previous —\n").append(previousNotes);
        }
        return sb.toString();
    }

    private AdminAttendanceRowDto toRow(Attendance a) {
        if (a == null) {
            throw new IllegalArgumentException("Attendance cannot be null");
        }
        Long internId = a.getUser() != null ? a.getUser().getId() : null;
        InternProfile ip = internId != null ? internProfileRepository.findByUser_Id(internId).orElse(null) : null;
        Long supId = ip != null ? ip.getSupervisorUserId() : null;
        String supName = supId != null
                ? userRepository.findById(supId).map(u -> u.getFirstName() + " " + u.getLastName()).orElse(null)
                : null;
        User modBy = a.getModifiedBy();
        Long modId = modBy != null ? modBy.getId() : null;
        String modName = modBy != null ? modBy.getFirstName() + " " + modBy.getLastName() : null;
        return AdminAttendanceRowDto.builder()
                .id(a.getId())
                .internId(internId)
                .firstName(a.getUser() != null ? a.getUser().getFirstName() : null)
                .lastName(a.getUser() != null ? a.getUser().getLastName() : null)
                .profilePhotoUrl(a.getUser() != null ? a.getUser().getProfilePhotoUrl() : null)
                .universityId(a.getUser() != null ? a.getUser().getUniversityId() : null)
                .supervisorId(supId)
                .supervisorName(supName)
                .date(a.getAttendanceDate())
                .checkInAt(a.getCheckInAt())
                .checkOutAt(a.getCheckOutAt())
                .status(a.getStatus())
                .durationMinutes(a.getDurationMinutes())
                .excused(a.isExcused())
                .excuseReason(a.getExcuseReason())
                .notes(a.getNotes())
                .manualEntry(a.isManualEntry())
                .modifiedByUserId(modId)
                .modifiedByName(modName)
                .build();
    }
}
