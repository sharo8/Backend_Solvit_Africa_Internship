package com.solvit.internship_system.mapper;

import com.solvit.internship_system.dto.evaluation.EvaluationDto;
import com.solvit.internship_system.entity.Evaluation;
import com.solvit.internship_system.entity.ProjectGroup;
import com.solvit.internship_system.entity.User;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EvaluationMapper {

    public static EvaluationDto toDto(Evaluation e) {
        if (e == null) {
            return null;
        }
        User intern = e.getIntern();
        User eval = e.getEvaluator();
        ProjectGroup g = e.getGroup();
        String internName = intern != null
                ? (intern.getFirstName() + " " + (intern.getLastName() == null ? "" : intern.getLastName())).trim()
                : null;
        String evalName = eval != null
                ? (eval.getFirstName() + " " + (eval.getLastName() == null ? "" : eval.getLastName())).trim()
                : null;
        return EvaluationDto.builder()
                .id(e.getId())
                .internId(intern != null ? intern.getId() : null)
                .internName(internName)
                .evaluatorId(eval != null ? eval.getId() : null)
                .evaluatorName(evalName)
                .groupId(g != null ? g.getId() : null)
                .groupName(g != null ? g.getName() : null)
                .type(e.getType())
                .status(e.getStatus())
                .technicalScore(e.getTechnicalScore())
                .communicationScore(e.getCommunicationScore())
                .attendanceScore(e.getAttendanceScore())
                .initiativeScore(e.getInitiativeScore())
                .overallScore(e.getOverallScore())
                .strengthsNote(e.getStrengthsNote())
                .improvementNote(e.getImprovementNote())
                .supervisorComment(e.getSupervisorComment())
                .internResponse(e.getInternResponse())
                .internAcknowledged(e.isInternAcknowledged())
                .acknowledgedAt(e.getAcknowledgedAt())
                .evaluationDate(e.getEvaluationDate())
                .build();
    }
}
