package com.solvit.internship_system.service;

import com.solvit.internship_system.dto.attendance.dynamicqr.*;
import com.solvit.internship_system.entity.Attendance;
import com.solvit.internship_system.entity.AttendanceLocation;
import com.solvit.internship_system.entity.AttendanceScanLog;
import com.solvit.internship_system.entity.Notification;
import com.solvit.internship_system.entity.InternProfile;
import com.solvit.internship_system.entity.QrExtraAccessRequest;
import com.solvit.internship_system.entity.QrExtraAccessStatus;
import com.solvit.internship_system.entity.Role;
import com.solvit.internship_system.entity.User;
import com.solvit.internship_system.exception.BadRequestException;
import com.solvit.internship_system.exception.ResourceNotFoundException;
import com.solvit.internship_system.exception.TooManyRequestsException;
import com.solvit.internship_system.repository.AttendanceLocationRepository;
import com.solvit.internship_system.repository.AttendanceRepository;
import com.solvit.internship_system.repository.AttendanceScanLogRepository;
import com.solvit.internship_system.repository.InternProfileRepository;
import com.solvit.internship_system.repository.QrExtraAccessRequestRepository;
import com.solvit.internship_system.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.solvit.internship_system.service.qr.QrRedisSupport;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Dynamic intern QR (JWT) + supervisor scan. Persists {@link Attendance} via existing {@link AttendanceService} rules.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InternQrAttendanceService {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double MAX_DISTANCE_KM = 0.5;
    private static final int DAILY_QR_DOWNLOAD_LIMIT = 2;
    private static final int DAILY_QR_TOKEN_COPY_LIMIT = 2;
    /** Max new QR tokens per Kigali calendar day before supervisor bonus (check-in + check-out). */
    private static final int DAILY_QR_GENERATION_LIMIT = 2;
    /** Max extra generations a supervisor may grant in a single approval (1 or 2). */
    private static final int MAX_BONUS_PER_GRANT = 2;

    private final QrTokenService qrTokenService;
    private final AttendanceService attendanceService;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;
    private final InternProfileRepository internProfileRepository;
    private final AttendanceLocationRepository attendanceLocationRepository;
    private final AttendanceScanLogRepository attendanceScanLogRepository;
    private final NotificationService notificationService;
    private final QrRedisSupport qrRedisSupport;
    private final QrExtraAccessRequestRepository qrExtraAccessRequestRepository;

    @Value("${qr.validity-minutes:5}")
    private int validityMinutes;

    @Transactional
    public QrTokenResponseDto generateTokenForIntern(Long internId) {
        String rateKey = "qr:ratelimit:" + internId;
        long cnt = qrRedisSupport.incrementWithExpire(rateKey, Duration.ofHours(1));
        if (cnt > 20) {
            throw new TooManyRequestsException("Too many QR refreshes — try again later.");
        }

        User intern = userRepository.findById(internId).orElseThrow(() -> new ResourceNotFoundException("User", internId));
        if (intern.getRole() != Role.INTERN) {
            throw new BadRequestException("Only interns can generate attendance QR codes");
        }

        LocalDate dayDate = AttendanceCalculationService.todayKigali();
        String day = dayDate.toString();
        String genKey = "qr:daily:gen:" + internId + ":" + day;
        int effective = effectiveGenerationLimit(internId, dayDate);
        long used = qrRedisSupport.getCounter(genKey);
        if (used >= effective) {
            throw new TooManyRequestsException(
                    "Daily QR generation limit reached for today (base "
                            + DAILY_QR_GENERATION_LIMIT
                            + " plus any supervisor-approved extra). Resets at midnight Kigali, or ask your supervisor for extra access.");
        }

        String token = qrTokenService.generateQrToken(internId);
        long expiresAt = Instant.now().plus(validityMinutes, ChronoUnit.MINUTES).getEpochSecond();
        qrRedisSupport.incrementWithExpire(genKey, AttendanceCalculationService.ttlUntilMidnightKigali());

        return QrTokenResponseDto.builder()
                .token(token)
                .expiresAt(expiresAt)
                .firstName(intern.getFirstName())
                .lastName(intern.getLastName())
                .internId(internId)
                .build();
    }

    @Transactional(readOnly = true)
    public Page<AttendanceRecordApiDto> getMyAttendance(Long internId, LocalDate from, LocalDate to, Pageable pageable) {
        return attendanceRepository.findByUser_IdAndAttendanceDateBetweenOrderByAttendanceDateDesc(internId, from, to, pageable)
                .map(this::toRecordDto);
    }

    @Transactional(readOnly = true)
    public InternDynamicQrStatsDto getInternStats(Long internId, LocalDate from, LocalDate to) {
        List<Attendance> records = attendanceRepository.findForUserInDateRange(internId, from, to);
        long total = records.size();
        long present = records.stream().filter(r -> {
            Attendance.AttendanceStatus s = r.getStatus();
            return s == Attendance.AttendanceStatus.PRESENT
                    || s == Attendance.AttendanceStatus.LATE
                    || s == Attendance.AttendanceStatus.HALF_DAY
                    || s == Attendance.AttendanceStatus.VALIDATED;
        }).count();
        long absent = records.stream().filter(r -> r.getStatus() == Attendance.AttendanceStatus.ABSENT).count();
        long late = records.stream().filter(r -> r.getStatus() == Attendance.AttendanceStatus.LATE).count();
        double rate = total > 0 ? (double) present / total * 100 : 0;
        double avgDuration = records.stream()
                .map(Attendance::getDurationMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);
        return InternDynamicQrStatsDto.builder()
                .totalDays(total)
                .presentDays(present)
                .absentDays(absent)
                .lateDays(late)
                .attendanceRate(Math.round(rate * 10) / 10.0)
                .avgDurationMinutes((int) avgDuration)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AttendanceLocationDto> getActiveLocations() {
        return attendanceLocationRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toLocationDto)
                .toList();
    }

    /**
     * Supervisor / admin view of attendance rows for interns in scope (supervisor: assigned interns only).
     */
    @Transactional(readOnly = true)
    public Page<AttendanceRecordApiDto> listForSupervisor(Long supervisorId, Role role, Long internId,
                                                        LocalDate date, String status, Pageable pageable) {
        List<Long> scopeIds = resolveInternScope(supervisorId, role, internId);
        if (scopeIds.isEmpty()) {
            return Page.empty(pageable);
        }
        Attendance.AttendanceStatus st = parseStatusFilter(status);
        return attendanceRepository.searchInInternScope(scopeIds, date, st, pageable)
                .map(this::toRecordDto);
    }

    private List<Long> resolveInternScope(Long supervisorId, Role role, Long internIdFilter) {
        if (role == Role.ADMIN || role == Role.HR) {
            if (internIdFilter != null) {
                return userRepository.findById(internIdFilter)
                        .filter(u -> u.getRole() == Role.INTERN)
                        .map(u -> List.of(u.getId()))
                        .orElse(List.of());
            }
            return userRepository.findByRoleAndActiveTrue(Role.INTERN).stream().map(User::getId).toList();
        }
        if (role == Role.SUPERVISOR) {
            List<Long> mine = internProfileRepository.findBySupervisorUserId(supervisorId).stream()
                    .map(p -> p.getUser().getId())
                    .toList();
            if (internIdFilter != null) {
                return mine.contains(internIdFilter) ? List.of(internIdFilter) : List.of();
            }
            return mine;
        }
        return List.of();
    }

    private static Attendance.AttendanceStatus parseStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return Attendance.AttendanceStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown attendance status: " + status);
        }
    }

    @Transactional
    public AttendanceLocationDto createLocation(AttendanceLocationDto dto) {
        AttendanceLocation e = AttendanceLocation.builder()
                .name(dto.getName())
                .address(dto.getAddress())
                .checkInStartTime(parseTime(dto.getCheckInStartTime()))
                .checkInDeadline(parseTime(dto.getCheckInDeadline()))
                .checkOutDeadline(parseTime(dto.getCheckOutDeadline()))
                .expectedHoursPerDay(dto.getExpectedHoursPerDay())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .active(true)
                .build();
        e = attendanceLocationRepository.save(e);
        return toLocationDto(e);
    }

    private static LocalTime parseTime(String s) {
        if (s == null || s.isBlank()) return null;
        return LocalTime.parse(s, DateTimeFormatter.ISO_LOCAL_TIME);
    }

    @Transactional
    public ScanResultDto processScan(String token, Long scannedByUserId, Long locationId,
                                     Double scanLat, Double scanLon, String clientIp) {
        String tokenHash = sha256Hex(token);
        try {
            QrTokenClaims claims = qrTokenService.validateAndConsume(token);
            Long internId = claims.internId();

            String cooldownKey = "qr:scan:cooldown:" + internId;
            if (qrRedisSupport.hasKey(cooldownKey)) {
                throw new BadRequestException("Please wait before scanning this intern again.");
            }

            User scanner = userRepository.findById(scannedByUserId).orElseThrow(() -> new ResourceNotFoundException("User", scannedByUserId));
            User intern = userRepository.findById(internId).orElseThrow(() -> new ResourceNotFoundException("User", internId));
            if (intern.getRole() != Role.INTERN) {
                throw new BadRequestException("QR code is not for an intern account");
            }

            assertScannerMayActOnIntern(scanner, internId);

            AttendanceLocation location = null;
            if (locationId != null) {
                location = attendanceLocationRepository.findById(locationId)
                        .orElseThrow(() -> new ResourceNotFoundException("Location", locationId));
                if (!location.isActive()) {
                    throw new BadRequestException("Location is inactive");
                }
                validateGpsIfConfigured(location, scanLat, scanLon);
            }

            String locationLabel = location != null ? location.getName() : null;

            LocalDate today = AttendanceCalculationService.todayKigali();
            Optional<Attendance> todayOpt = attendanceRepository.findByUser_IdAndAttendanceDate(internId, today);
            boolean willCheckOut = todayOpt.isPresent()
                    && todayOpt.get().getCheckInAt() != null
                    && todayOpt.get().getCheckOutAt() == null;

            Attendance saved = attendanceService.applySupervisorScannedInternQr(
                    internId, scannedByUserId,
                    scanLat != null ? String.valueOf(scanLat) : null,
                    scanLon != null ? String.valueOf(scanLon) : null,
                    locationLabel);

            qrRedisSupport.set(cooldownKey, "1", Duration.ofSeconds(30));

            if (!willCheckOut && saved.getStatus() == Attendance.AttendanceStatus.LATE) {
                notifySupervisorLate(intern, saved);
            }

            persistScanLog(intern, scanner, location, tokenHash, "SUCCESS", "OK", clientIp);

            return buildScanResult(saved, intern, willCheckOut ? "CHECK_OUT" : "CHECK_IN");
        } catch (BadRequestException | AccessDeniedException ex) {
            persistFailureLog(tokenHash, scannedByUserId, clientIp, ex.getMessage());
            throw ex;
        } catch (TooManyRequestsException ex) {
            throw ex;
        } catch (Exception ex) {
            persistFailureLog(tokenHash, scannedByUserId, clientIp, ex.getMessage());
            throw ex;
        }
    }

    private void persistFailureLog(String tokenHash, Long scannedByUserId, String clientIp, String msg) {
        try {
            User scanner = scannedByUserId != null ? userRepository.findById(scannedByUserId).orElse(null) : null;
            AttendanceScanLog row = AttendanceScanLog.builder()
                    .intern(null)
                    .scannedBy(scanner)
                    .location(null)
                    .tokenHash(tokenHash)
                    .result("FAILURE")
                    .message(msg != null && msg.length() > 500 ? msg.substring(0, 500) : msg)
                    .ipAddress(clientIp)
                    .build();
            attendanceScanLogRepository.save(row);
        } catch (Exception e) {
            log.warn("Could not persist scan failure log: {}", e.getMessage());
        }
    }

    private void assertScannerMayActOnIntern(User scanner, Long internId) {
        if (scanner.getRole() == Role.ADMIN || scanner.getRole() == Role.HR) {
            return;
        }
        if (scanner.getRole() == Role.SUPERVISOR) {
            com.solvit.internship_system.entity.InternProfile p = internProfileRepository.findByUser_Id(internId)
                    .orElseThrow(() -> new AccessDeniedException("Intern profile not found"));
            if (!Objects.equals(p.getSupervisorUserId(), scanner.getId())) {
                throw new AccessDeniedException("This intern is not assigned to you.");
            }
            return;
        }
        throw new AccessDeniedException("Not allowed to scan attendance QR codes.");
    }

    private void validateGpsIfConfigured(AttendanceLocation loc, Double scanLat, Double scanLon) {
        if (loc.getLatitude() == null || loc.getLongitude() == null) {
            return;
        }
        if (scanLat == null || scanLon == null) {
            throw new BadRequestException("GPS coordinates required for this location.");
        }
        double d = haversineKm(scanLat, scanLon, loc.getLatitude().doubleValue(), loc.getLongitude().doubleValue());
        if (d > MAX_DISTANCE_KM) {
            throw new BadRequestException("You are too far from the selected location (max " + MAX_DISTANCE_KM + " km).");
        }
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    private void persistScanLog(User intern, User scanner, AttendanceLocation location,
                                String tokenHash, String result, String message, String clientIp) {
        AttendanceScanLog row = AttendanceScanLog.builder()
                .intern(intern)
                .scannedBy(scanner)
                .location(location)
                .tokenHash(tokenHash)
                .result(result)
                .message(message)
                .ipAddress(clientIp)
                .build();
        attendanceScanLogRepository.save(row);
    }

    private void notifySupervisorLate(User intern, Attendance a) {
        internProfileRepository.findByUser_Id(intern.getId()).ifPresent(ip -> {
            Long supId = ip.getSupervisorUserId();
            if (supId == null) return;
            notificationService.create(
                    supId,
                    "Late check-in (QR)",
                    intern.getFirstName() + " " + intern.getLastName() + " checked in late via dynamic QR.",
                    Notification.NotificationType.ATTENDANCE_REMINDER,
                    "Attendance",
                    a.getId(),
                    false
            );
        });
    }

    private ScanResultDto buildScanResult(Attendance a, User intern, String action) {
        String name = (intern.getFirstName() != null ? intern.getFirstName() : "") + " "
                + (intern.getLastName() != null ? intern.getLastName() : "");
        name = name.trim();
        Instant t = "CHECK_OUT".equals(action) ? a.getCheckOutAt() : a.getCheckInAt();
        String timeStr = t != null ? t.toString() : Instant.now().toString();
        String status = a.getStatus() != null ? a.getStatus().name() : "";
        Integer lateMin = computeLateMinutes(a);
        Integer dur = a.getDurationMinutes();
        String msg;
        if ("CHECK_IN".equals(action)) {
            msg = Attendance.AttendanceStatus.LATE.equals(a.getStatus())
                    ? ("Check-in recorded — " + (lateMin != null ? lateMin + " minutes late" : "late"))
                    : "Check-in recorded successfully";
        } else {
            long dm = dur != null ? dur : 0L;
            msg = "Check-out recorded — " + (dm / 60) + "h " + (dm % 60) + "min today";
        }
        return ScanResultDto.builder()
                .action(action)
                .internId(intern.getId())
                .internName(name)
                .time(timeStr)
                .status(status)
                .lateMinutes(lateMin)
                .durationMinutes(dur)
                .message(msg)
                .build();
    }

    private Integer computeLateMinutes(Attendance a) {
        if (a.getStatus() != Attendance.AttendanceStatus.LATE || a.getCheckInAt() == null) {
            return null;
        }
        ZoneId z = AttendanceCalculationService.APP_ZONE;
        LocalTime lateCutoff = LocalTime.of(9, 30);
        ZonedDateTime in = a.getCheckInAt().atZone(z);
        ZonedDateTime deadline = in.toLocalDate().atTime(lateCutoff).atZone(z);
        if (!in.isAfter(deadline)) {
            return null;
        }
        return (int) Duration.between(deadline, in).toMinutes();
    }

    private AttendanceRecordApiDto toRecordDto(Attendance a) {
        String internName = a.getUser() != null
                ? (a.getUser().getFirstName() + " " + a.getUser().getLastName())
                : "";
        String scannedByName = null;
        if (a.getModifiedBy() != null) {
            User m = a.getModifiedBy();
            scannedByName = (m.getFirstName() != null ? m.getFirstName() : "") + " " + (m.getLastName() != null ? m.getLastName() : "");
            scannedByName = scannedByName.trim();
        }
        return AttendanceRecordApiDto.builder()
                .id(a.getId())
                .internName(internName.trim())
                .date(a.getAttendanceDate())
                .checkInTime(a.getCheckInAt() != null ? a.getCheckInAt().toString() : null)
                .checkOutTime(a.getCheckOutAt() != null ? a.getCheckOutAt().toString() : null)
                .durationMinutes(a.getDurationMinutes())
                .status(a.getStatus() != null ? a.getStatus().name() : null)
                .lateMinutes(computeLateMinutes(a))
                .locationName(a.getCheckInLocation())
                .scannedByName(scannedByName)
                .build();
    }

    @Transactional(readOnly = true)
    public QrDailyLimitsDto getDailyLimits(long internId) {
        LocalDate dayDate = AttendanceCalculationService.todayKigali();
        return buildQrDailyLimitsDto(internId, dayDate);
    }

    private QrDailyLimitsDto buildQrDailyLimitsDto(long internId, LocalDate dayDate) {
        String day = dayDate.toString();
        String dlKey = "qr:daily:dl:" + internId + ":" + day;
        String cpyKey = "qr:daily:cpy:" + internId + ":" + day;
        String genKey = "qr:daily:gen:" + internId + ":" + day;
        int dl = (int) Math.min(DAILY_QR_DOWNLOAD_LIMIT, qrRedisSupport.getCounter(dlKey));
        int cpy = (int) Math.min(DAILY_QR_TOKEN_COPY_LIMIT, qrRedisSupport.getCounter(cpyKey));
        long rawGen = qrRedisSupport.getCounter(genKey);
        int bonusTotal = bonusGenerationsTotal(internId, dayDate);
        int effective = DAILY_QR_GENERATION_LIMIT + bonusTotal;
        int genUsed = (int) rawGen;
        int remaining = Math.max(0, effective - genUsed);

        boolean supervisorAssigned = internProfileRepository.findByUser_Id(internId)
                .map(p -> p.getSupervisorUserId() != null)
                .orElse(false);

        Optional<QrExtraAccessRequest> pending = qrExtraAccessRequestRepository
                .findFirstByInternUserIdAndRequestDateAndStatusOrderByCreatedAtDesc(
                        internId, dayDate, QrExtraAccessStatus.PENDING);

        String extraStatus = "NONE";
        if (pending.isPresent()) {
            extraStatus = "PENDING";
        } else {
            Optional<QrExtraAccessRequest> last = qrExtraAccessRequestRepository
                    .findTopByInternUserIdAndRequestDateOrderByCreatedAtDesc(internId, dayDate);
            if (last.isPresent() && last.get().getStatus() == QrExtraAccessStatus.REJECTED) {
                extraStatus = "REJECTED";
            }
        }

        boolean exhausted = genUsed >= effective;
        boolean canRequest = supervisorAssigned
                && exhausted
                && !pending.isPresent();

        return new QrDailyLimitsDto(
                dl,
                DAILY_QR_DOWNLOAD_LIMIT,
                cpy,
                DAILY_QR_TOKEN_COPY_LIMIT,
                Math.max(0, DAILY_QR_DOWNLOAD_LIMIT - dl),
                Math.max(0, DAILY_QR_TOKEN_COPY_LIMIT - cpy),
                genUsed,
                effective,
                remaining,
                bonusTotal,
                DAILY_QR_GENERATION_LIMIT,
                effective,
                extraStatus,
                canRequest,
                supervisorAssigned);
    }

    private int bonusGenerationsTotal(long internId, LocalDate day) {
        String key = "qr:daily:bonusTotal:" + internId + ":" + day;
        return (int) Math.min(Integer.MAX_VALUE / 4, qrRedisSupport.getCounter(key));
    }

    private int effectiveGenerationLimit(long internId, LocalDate day) {
        return DAILY_QR_GENERATION_LIMIT + bonusGenerationsTotal(internId, day);
    }

    private void addBonusGrant(long internId, LocalDate day, int amount) {
        if (amount < 1 || amount > MAX_BONUS_PER_GRANT) {
            throw new BadRequestException("Supervisor may grant 1 or 2 extra QR generations per approval.");
        }
        String key = "qr:daily:bonusTotal:" + internId + ":" + day;
        long cur = qrRedisSupport.getCounter(key);
        long next = cur + amount;
        qrRedisSupport.set(key, String.valueOf(next), AttendanceCalculationService.ttlUntilMidnightKigali());
    }

    @Transactional
    public void requestExtraQrAccess(long internId, String message) {
        User intern = userRepository.findById(internId).orElseThrow(() -> new ResourceNotFoundException("User", internId));
        if (intern.getRole() != Role.INTERN) {
            throw new BadRequestException("Only interns can request extra QR access.");
        }
        LocalDate day = AttendanceCalculationService.todayKigali();
        InternProfile p = internProfileRepository.findByUser_Id(internId)
                .orElseThrow(() -> new BadRequestException("Intern profile not found."));
        if (p.getSupervisorUserId() == null) {
            throw new BadRequestException("No supervisor assigned — contact HR.");
        }
        String genKey = "qr:daily:gen:" + internId + ":" + day;
        long used = qrRedisSupport.getCounter(genKey);
        int effective = effectiveGenerationLimit(internId, day);
        if (used < effective) {
            throw new BadRequestException(
                    "Extra access is only available after you have used your current daily QR generation quota.");
        }
        if (qrExtraAccessRequestRepository
                .findFirstByInternUserIdAndRequestDateAndStatusOrderByCreatedAtDesc(internId, day, QrExtraAccessStatus.PENDING)
                .isPresent()) {
            throw new BadRequestException("You already have a pending request for today.");
        }
        String msg = message != null && !message.isBlank() ? message.trim() : null;
        if (msg != null && msg.length() > 2000) {
            throw new BadRequestException("Message is too long.");
        }
        QrExtraAccessRequest req = QrExtraAccessRequest.builder()
                .internUserId(internId)
                .supervisorUserId(p.getSupervisorUserId())
                .requestDate(day)
                .status(QrExtraAccessStatus.PENDING)
                .message(msg)
                .createdAt(Instant.now())
                .build();
        qrExtraAccessRequestRepository.save(req);
        User sup = userRepository.findById(p.getSupervisorUserId()).orElse(null);
        if (sup != null) {
            String internName = (intern.getFirstName() != null ? intern.getFirstName() : "") + " "
                    + (intern.getLastName() != null ? intern.getLastName() : "");
            notificationService.create(
                    sup.getId(),
                    "Extra QR access requested",
                    internName.trim() + " has requested additional QR generations for attendance (after daily limit).",
                    Notification.NotificationType.SYSTEM,
                    "QR_EXTRA_ACCESS",
                    req.getId(),
                    false);
        }
    }

    @Transactional(readOnly = true)
    public List<QrExtraAccessRequestItemDto> listPendingExtraAccessRequests(long supervisorId) {
        return qrExtraAccessRequestRepository
                .findBySupervisorUserIdAndStatusOrderByCreatedAtDesc(supervisorId, QrExtraAccessStatus.PENDING)
                .stream()
                .map(r -> {
                    User u = userRepository.findById(r.getInternUserId()).orElse(null);
                    String name = u == null
                            ? "Intern #" + r.getInternUserId()
                            : ((u.getFirstName() != null ? u.getFirstName() : "") + " "
                                    + (u.getLastName() != null ? u.getLastName() : "")).trim();
                    return new QrExtraAccessRequestItemDto(
                            r.getId(),
                            r.getInternUserId(),
                            name.isEmpty() ? "Intern #" + r.getInternUserId() : name,
                            r.getRequestDate(),
                            r.getStatus().name(),
                            r.getMessage(),
                            r.getCreatedAt());
                })
                .toList();
    }

    @Transactional
    public void resolveExtraAccessRequest(long supervisorId, long requestId, QrExtraAccessResolveDto dto) {
        QrExtraAccessRequest r = qrExtraAccessRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("QrExtraAccessRequest", requestId));
        if (!Objects.equals(r.getSupervisorUserId(), supervisorId)) {
            throw new AccessDeniedException("Not your intern's request");
        }
        if (r.getStatus() != QrExtraAccessStatus.PENDING) {
            throw new BadRequestException("This request was already resolved.");
        }
        if (dto.approve()) {
            Integer bonus = dto.bonusGenerations();
            if (bonus == null || bonus < 1 || bonus > MAX_BONUS_PER_GRANT) {
                throw new BadRequestException("When approving, specify bonusGenerations: 1 or 2.");
            }
            addBonusGrant(r.getInternUserId(), r.getRequestDate(), bonus);
            r.setStatus(QrExtraAccessStatus.APPROVED);
            r.setBonusGenerations(bonus);
        } else {
            r.setStatus(QrExtraAccessStatus.REJECTED);
            r.setBonusGenerations(null);
        }
        r.setResolvedAt(Instant.now());
        qrExtraAccessRequestRepository.save(r);
        User intern = userRepository.findById(r.getInternUserId()).orElse(null);
        if (intern != null) {
            String title = dto.approve() ? "Extra QR access approved" : "Extra QR access denied";
            String body = dto.approve()
                    ? "Your supervisor approved " + r.getBonusGenerations()
                            + " extra QR generation(s) for today. You can refresh My QR again."
                    : "Your supervisor did not approve extra QR generations for today.";
            notificationService.create(
                    intern.getId(),
                    title,
                    body,
                    Notification.NotificationType.SYSTEM,
                    "QR_EXTRA_ACCESS",
                    r.getId(),
                    false);
        }
    }

    @Transactional
    public QrDailyLimitsDto recordDownload(long internId) {
        verifyInternForDailyLimits(internId);
        String day = AttendanceCalculationService.todayKigali().toString();
        String key = "qr:daily:dl:" + internId + ":" + day;
        if (qrRedisSupport.getCounter(key) >= DAILY_QR_DOWNLOAD_LIMIT) {
            throw new TooManyRequestsException(
                    "You can download your QR image at most " + DAILY_QR_DOWNLOAD_LIMIT + " times per day.");
        }
        qrRedisSupport.incrementWithExpire(key, AttendanceCalculationService.ttlUntilMidnightKigali());
        return getDailyLimits(internId);
    }

    @Transactional
    public QrDailyLimitsDto recordTokenCopy(long internId) {
        verifyInternForDailyLimits(internId);
        String day = AttendanceCalculationService.todayKigali().toString();
        String key = "qr:daily:cpy:" + internId + ":" + day;
        if (qrRedisSupport.getCounter(key) >= DAILY_QR_TOKEN_COPY_LIMIT) {
            throw new TooManyRequestsException(
                    "You can copy your token at most " + DAILY_QR_TOKEN_COPY_LIMIT
                            + " times per day (intended for check-in and check-out).");
        }
        qrRedisSupport.incrementWithExpire(key, AttendanceCalculationService.ttlUntilMidnightKigali());
        return getDailyLimits(internId);
    }

    @Transactional(readOnly = true)
    public SupervisorContactDto getSupervisorContact(long internId) {
        Optional<com.solvit.internship_system.entity.InternProfile> p = internProfileRepository.findByUser_Id(internId);
        if (p.isEmpty() || p.get().getSupervisorUserId() == null) {
            return new SupervisorContactDto(null, null, false);
        }
        User sup = userRepository.findById(p.get().getSupervisorUserId()).orElse(null);
        if (sup == null) {
            return new SupervisorContactDto(null, null, false);
        }
        String name = (sup.getFirstName() != null ? sup.getFirstName() : "") + " " + (sup.getLastName() != null ? sup.getLastName() : "");
        name = name.trim();
        return new SupervisorContactDto(name.isEmpty() ? null : name, sup.getEmail(), true);
    }

    private void verifyInternForDailyLimits(long internId) {
        User u = userRepository.findById(internId).orElseThrow(() -> new ResourceNotFoundException("User", internId));
        if (u.getRole() != Role.INTERN) {
            throw new BadRequestException("Only interns use this resource.");
        }
    }

    private AttendanceLocationDto toLocationDto(AttendanceLocation e) {
        DateTimeFormatter f = DateTimeFormatter.ISO_LOCAL_TIME;
        return AttendanceLocationDto.builder()
                .id(e.getId())
                .name(e.getName())
                .address(e.getAddress())
                .checkInStartTime(e.getCheckInStartTime() != null ? e.getCheckInStartTime().format(f) : null)
                .checkInDeadline(e.getCheckInDeadline() != null ? e.getCheckInDeadline().format(f) : null)
                .checkOutDeadline(e.getCheckOutDeadline() != null ? e.getCheckOutDeadline().format(f) : null)
                .expectedHoursPerDay(e.getExpectedHoursPerDay())
                .latitude(e.getLatitude())
                .longitude(e.getLongitude())
                .active(e.isActive())
                .build();
    }

    private static String sha256Hex(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
