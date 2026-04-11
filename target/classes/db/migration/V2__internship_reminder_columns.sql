-- Reminder deduplication for internship end notifications (30 days / 7 days before end)
ALTER TABLE intern_profiles
    ADD COLUMN reminder_30d_sent_for_end_date DATE NULL,
    ADD COLUMN reminder_7d_sent_for_end_date DATE NULL;
