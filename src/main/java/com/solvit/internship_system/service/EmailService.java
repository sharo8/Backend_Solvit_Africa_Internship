package com.solvit.internship_system.service;

import com.solvit.internship_system.entity.LeaveRequest;
import com.solvit.internship_system.entity.ProjectGroup;
import com.solvit.internship_system.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import jakarta.mail.internet.MimeMessage;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendBaseUrl;

    private static final String BRAND_COLOR = "#1a56db";

    private void sendHtmlEmail(String to, String subject, String templateName, Map<String, Object> variables) {
        try {
            Context ctx = new Context();
            ctx.setVariables(variables);
            ctx.setVariable("brandColor", BRAND_COLOR);
            ctx.setVariable("logoUrl", frontendBaseUrl.replaceAll("/$", "") + "/logo.png");

            String html = templateEngine.process(templateName, ctx);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send HTML email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendTaskMailHtml(String to,
                                   String subject,
                                   String templateName,
                                   Map<String, Object> variables) {
        sendHtmlEmail(to, subject, templateName, variables);
    }

    @Async
    public void sendProjectMailHtml(String to,
                                      String subject,
                                      String templateName,
                                      Map<String, Object> variables) {
        sendHtmlEmail(to, subject, templateName, variables);
    }

    @Async
    public void sendOtpEmail(String to, String otp, String purpose) {
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("otp", otp);
            vars.put("purpose", purpose != null && !purpose.isBlank() ? purpose : "verification");
            vars.put("loginUrl", frontendBaseUrl.replaceAll("/$", "") + "/login");
            sendHtmlEmail(to, "Your verification code — SOLVIT Africa", "emails/otp-email", vars);
            log.debug("OTP email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendPasswordResetEmail(String to, String resetLink) {
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("resetLink", resetLink);
            sendHtmlEmail(to, "Reset your password — SOLVIT Africa", "emails/password-reset-email", vars);
            log.debug("Password reset email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendWelcomeEmail(String firstName, String to, String setPasswordLink) {
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("firstName", firstName != null ? firstName : "User");
            vars.put("loginUrl", frontendBaseUrl + "/login");
            vars.put("setPasswordLink", setPasswordLink);
            sendHtmlEmail(to, "Welcome to SOLVIT Africa — You're all set! \uD83C\uDF89", "emails/welcome-email", vars);
            log.debug("Welcome email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendHrNewPendingInternEmail(String hrFirstName, String hrEmail,
                                            String internFirstName, String internLastName, String internEmail) {
        try {
            String greet = hrFirstName != null && !hrFirstName.isBlank() ? hrFirstName : "there";
            String body = String.format(
                    "Hello %s,%n%nA new intern has applied through the public registration form and is awaiting approval.%n%n"
                            + "Name: %s %s%nEmail: %s%n%nSign in to the management portal → Users → filter “Pending HR” to review and approve.%n%n— SOLVIT Africa",
                    greet,
                    internFirstName != null ? internFirstName : "",
                    internLastName != null ? internLastName : "",
                    internEmail != null ? internEmail : "");
            sendNotificationEmail(hrEmail, "[SOLVIT Africa] New intern awaiting your approval", body);
            log.debug("HR pending-intern alert sent to {}", hrEmail);
        } catch (Exception e) {
            log.error("Failed to send HR pending-intern email to {}: {}", hrEmail, e.getMessage());
        }
    }

    @Async
    public void sendInternRegistrationPendingEmail(String firstName, String to) {
        try {
            String name = firstName != null && !firstName.isBlank() ? firstName : "there";
            String body = String.format(
                    "Hello %s,%n%nThank you for applying through the intern portal. Your account is pending approval by HR or an administrator.%n"
                            + "You will be able to sign in only after your request has been approved.%n%n"
                            + "Login page: %s/login%n%n— SOLVIT Africa",
                    name, frontendBaseUrl.replaceAll("/$", ""));
            sendNotificationEmail(to, "[SOLVIT Africa] Intern registration received — pending approval", body);
            log.debug("Pending intern registration email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send pending registration email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendNotificationEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.debug("Notification email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send notification email to {}: {}", to, e.getMessage());
        }
    }

    /**
     * HR/Admin: intern's scheduled internship end is approaching (30-day or 7-day window).
     */
    @Async
    public void sendInternshipEndingReminderEmail(String to, String internDisplayName, java.time.LocalDate scheduledEnd, String windowDescription) {
        String subject = "[SOLVIT Africa] Internship ending soon — " + internDisplayName;
        String body = String.format(
                "Hello,%n%n"
                        + "This is an automated reminder from SOLVIT Africa.%n%n"
                        + "Intern: %s%n"
                        + "Scheduled end date: %s%n"
                        + "Reminder: %s%n%n"
                        + "You can renew (extend) the internship or set an early completion date from Admin → Interns.%n%n"
                        + "— SOLVIT Africa Management System",
                internDisplayName,
                scheduledEnd,
                windowDescription
        );
        sendNotificationEmail(to, subject, body);
    }

    /**
     * Intern: five consecutive absent workdays — formal warning about possible programme cancellation.
     */
    @Async
    public void sendConsecutiveAbsenceProgramWarning(String to, String internDisplayName, java.time.LocalDate streakEndDate) {
        String name = internDisplayName != null && !internDisplayName.isBlank() ? internDisplayName : "Intern";
        String subject = "[SOLVIT Africa] Consecutive absence warning";
        String body = String.format(
                "Hello %s,%n%n"
                        + "Our records show 5 consecutive working days marked absent (through %s).%n%n"
                        + "Under programme rules, repeated unexcused absences may lead to cancellation of your internship.%n%n"
                        + "Please contact your supervisor or HR as soon as possible.%n%n"
                        + "— SOLVIT Africa Management System",
                name,
                streakEndDate
        );
        sendNotificationEmail(to, subject, body);
    }

    @Async
    public void sendLeaveApprovedEmail(User intern, LeaveRequest leave) {
        if (intern.getEmail() == null || intern.getEmail().isBlank()) {
            return;
        }
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("firstName", intern.getFirstName() != null ? intern.getFirstName() : "there");
            vars.put("leaveType", leave.getLeaveType() != null ? leave.getLeaveType().name() : "");
            vars.put("startDate", leave.getStartDate() != null ? leave.getStartDate().toString() : "");
            vars.put("endDate", leave.getEndDate() != null ? leave.getEndDate().toString() : "");
            vars.put("dashboardUrl", frontendBaseUrl.replaceAll("/$", "") + "/app/leave");
            sendHtmlEmail(
                    intern.getEmail(),
                    "Leave approved — SOLVIT Africa",
                    "emails/leave-approved-intern",
                    vars);
            log.debug("Leave approved email sent to {}", intern.getEmail());
        } catch (Exception e) {
            log.error("Failed to send leave approved email: {}", e.getMessage());
        }
    }

    @Async
    public void sendLeaveRejectedEmail(User intern, LeaveRequest leave, String reason) {
        if (intern.getEmail() == null || intern.getEmail().isBlank()) {
            return;
        }
        try {
            Map<String, Object> vars = new HashMap<>();
            vars.put("firstName", intern.getFirstName() != null ? intern.getFirstName() : "there");
            vars.put("leaveType", leave.getLeaveType() != null ? leave.getLeaveType().name() : "");
            vars.put("startDate", leave.getStartDate() != null ? leave.getStartDate().toString() : "");
            vars.put("endDate", leave.getEndDate() != null ? leave.getEndDate().toString() : "");
            vars.put("reason", reason != null && !reason.isBlank() ? reason : "No additional details were provided.");
            vars.put("dashboardUrl", frontendBaseUrl.replaceAll("/$", "") + "/app/leave");
            sendHtmlEmail(
                    intern.getEmail(),
                    "Leave request update — SOLVIT Africa",
                    "emails/leave-rejected-intern",
                    vars);
            log.debug("Leave rejected email sent to {}", intern.getEmail());
        } catch (Exception e) {
            log.error("Failed to send leave rejected email: {}", e.getMessage());
        }
    }

    @Async
    public void sendLeaveRequestSubmittedToSupervisorEmail(User supervisor, User intern, LeaveRequest leave) {
        if (supervisor.getEmail() == null || supervisor.getEmail().isBlank()) {
            return;
        }
        try {
            String internName = (intern.getFirstName() != null ? intern.getFirstName() : "")
                    + " "
                    + (intern.getLastName() != null ? intern.getLastName() : "");
            internName = internName.trim();
            if (internName.isEmpty()) {
                internName = "An intern";
            }
            Map<String, Object> vars = new HashMap<>();
            vars.put("supervisorFirstName", supervisor.getFirstName() != null ? supervisor.getFirstName() : "there");
            vars.put("internName", internName);
            vars.put("leaveType", leave.getLeaveType() != null ? leave.getLeaveType().name() : "");
            vars.put("startDate", leave.getStartDate() != null ? leave.getStartDate().toString() : "");
            vars.put("endDate", leave.getEndDate() != null ? leave.getEndDate().toString() : "");
            vars.put("reason", leave.getReason() != null && !leave.getReason().isBlank() ? leave.getReason() : "—");
            vars.put("reviewUrl", frontendBaseUrl.replaceAll("/$", "") + "/app/leave-approvals");
            vars.put("leaveRequestsUrl", frontendBaseUrl.replaceAll("/$", "") + "/app/leave-requests");
            sendHtmlEmail(
                    supervisor.getEmail(),
                    "Action needed: " + internName + " submitted a leave request — SOLVIT Africa",
                    "emails/leave-request-supervisor",
                    vars);
            log.debug("Leave request supervisor email sent to {}", supervisor.getEmail());
        } catch (Exception e) {
            log.error("Failed to send leave request email to supervisor: {}", e.getMessage());
        }
    }

    /**
     * Intern: confirmation of enrollment; supervisor: alert that an intern was added to their cohort.
     */
    @Async
    public void sendCohortEnrollmentEmails(User intern, User supervisor, ProjectGroup group) {
        String groupName = group.getName() != null ? group.getName() : "cohorte";
        String loginUrl = frontendBaseUrl + "/login";

        if (intern.getEmail() != null && !intern.getEmail().isBlank()) {
            String bodyIntern = String.format(
                    "Bonjour %s,%n%n"
                            + "Vous avez été ajouté(e) à la cohorte « %s » dans SOLVIT Africa. "
                            + "Connectez-vous pour voir vos projets et tâches : %s%n%n"
                            + "— SOLVIT Africa",
                    intern.getFirstName() != null ? intern.getFirstName() : "",
                    groupName,
                    loginUrl);
            sendNotificationEmail(
                    intern.getEmail(),
                    "Confirmation : inscription à la cohorte « " + groupName + " »",
                    bodyIntern);
        }

        if (supervisor != null && supervisor.getEmail() != null && !supervisor.getEmail().isBlank()) {
            String ln = intern.getLastName() != null ? intern.getLastName() : "";
            String bodySup = String.format(
                    "Bonjour %s,%n%n"
                            + "L’étudiant(e) %s %s a été ajouté(e) à votre cohorte « %s ».%n%n"
                            + "— SOLVIT Africa",
                    supervisor.getFirstName() != null ? supervisor.getFirstName() : "",
                    intern.getFirstName() != null ? intern.getFirstName() : "",
                    ln,
                    groupName);
            sendNotificationEmail(
                    supervisor.getEmail(),
                    "Cohorte : nouvel étudiant — « " + groupName + " »",
                    bodySup);
        }
    }
}
