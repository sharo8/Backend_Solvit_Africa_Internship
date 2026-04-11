package com.solvit.internship_system.dto.tasks;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.solvit.internship_system.entity.Task;

import java.io.IOException;

/**
 * Accepts legacy {@code COMPLETED} in JSON for task status patches (maps to {@link Task.TaskStatus#IN_REVIEW}).
 */
public class TaskStatusPatchDeserializer extends JsonDeserializer<Task.TaskStatus> {

    @Override
    public Task.TaskStatus deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String raw = p.getValueAsString();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().toUpperCase();
        if ("COMPLETED".equals(s)) {
            return Task.TaskStatus.IN_REVIEW;
        }
        if ("NOT_STARTED".equals(s)) {
            return Task.TaskStatus.PENDING;
        }
        if ("SUBMITTED".equals(s)) {
            return Task.TaskStatus.IN_REVIEW;
        }
        return Task.TaskStatus.valueOf(s);
    }
}
