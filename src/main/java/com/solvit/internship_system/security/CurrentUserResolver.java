package com.solvit.internship_system.security;

import com.solvit.internship_system.entity.Role;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/**
 * Resolves the authenticated user's database id and role from the security context (loaded from DB in
 * {@link UserDetailsServiceImpl}, not from JWT claims).
 */
@Slf4j
@Component
public class CurrentUserResolver {

    public Long requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Not authenticated");
        }
        Object p = auth.getPrincipal();
        if (p instanceof CurrentUserPrincipal cup) {
            Role role = resolveRole(auth);
            log.debug("[CurrentUser] email={} userId={} role={}", cup.getEmail(), cup.getUserId(), role);
            return cup.getUserId();
        }
        throw new AccessDeniedException("Unexpected authentication principal");
    }

    /**
     * Same precedence as {@code TaskController} had: ADMIN &gt; HR &gt; SUPERVISOR &gt; INTERN.
     */
    public Role requireRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Not authenticated");
        }
        return resolveRole(auth);
    }

    private Role resolveRole(Authentication auth) {
        EnumSet<Role> roles = EnumSet.noneOf(Role.class);
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String g = ga.getAuthority();
            if (g != null && g.startsWith("ROLE_")) {
                try {
                    roles.add(Role.valueOf(g.substring(5)));
                } catch (IllegalArgumentException ignored) {
                    // ignore unknown authority strings
                }
            }
        }
        if (roles.isEmpty()) {
            throw new AccessDeniedException("Missing role");
        }
        if (roles.contains(Role.ADMIN)) {
            return Role.ADMIN;
        }
        if (roles.contains(Role.HR)) {
            return Role.HR;
        }
        if (roles.contains(Role.SUPERVISOR)) {
            return Role.SUPERVISOR;
        }
        if (roles.contains(Role.INTERN)) {
            return Role.INTERN;
        }
        throw new AccessDeniedException("Missing role");
    }
}
