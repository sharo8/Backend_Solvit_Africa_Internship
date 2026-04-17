INSERT INTO ai_configurations (config_key, config_value, description, updated_by)
VALUES
    ('attendance_weight', '20', 'Weight (%) for attendance score contribution', NULL),
    ('task_completion_weight', '25', 'Weight (%) for task completion score contribution', NULL),
    ('work_quality_weight', '30', 'Weight (%) for work quality score contribution', NULL),
    ('technical_skills_weight', '15', 'Weight (%) for technical skills score contribution', NULL),
    ('conduct_engagement_weight', '10', 'Weight (%) for conduct and engagement score contribution', NULL),
    ('attendance_critical_threshold', '70', 'Attendance threshold below which critical alert is triggered', NULL),
    ('attendance_warning_threshold', '80', 'Attendance threshold below which warning alert is triggered', NULL),
    ('task_overdue_days_alert', '3', 'Number of overdue days before creating an alert', NULL),
    ('quality_drop_threshold', '2.5', 'Minimum average quality rating (1-5 scale) before quality drop alert', NULL),
    ('score_excellent_min', '85', 'Minimum final score for EXCELLENT grade', NULL),
    ('score_good_min', '70', 'Minimum final score for GOOD grade', NULL),
    ('score_satisfactory_min', '55', 'Minimum final score for SATISFACTORY grade', NULL),
    ('score_needs_improvement_min', '40', 'Minimum final score for NEEDS_IMPROVEMENT grade', NULL),
    ('prediction_accuracy_target', '85', 'Target AI prediction accuracy percentage', NULL)
ON DUPLICATE KEY UPDATE
    config_value = VALUES(config_value),
    description = VALUES(description);
