package com.solvit.internship_system.validation;

import com.solvit.internship_system.exception.BadRequestException;

import java.util.regex.Pattern;

/**
 * Strong password rules for registration, reset, and admin-created accounts.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;
    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{}|;:,.?/]");

    private PasswordPolicy() {
    }

    public static void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new BadRequestException("Password is required.");
        }
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new BadRequestException("Password must be between " + MIN_LENGTH + " and " + MAX_LENGTH + " characters.");
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            throw new BadRequestException("Password must not contain spaces.");
        }
        if (!UPPER.matcher(password).find()) {
            throw new BadRequestException("Password must contain at least one uppercase letter.");
        }
        if (!LOWER.matcher(password).find()) {
            throw new BadRequestException("Password must contain at least one lowercase letter.");
        }
        if (!DIGIT.matcher(password).find()) {
            throw new BadRequestException("Password must contain at least one digit.");
        }
        if (!SPECIAL.matcher(password).find()) {
            throw new BadRequestException("Password must contain at least one special character (!@#$%^&* etc.).");
        }
    }
}
