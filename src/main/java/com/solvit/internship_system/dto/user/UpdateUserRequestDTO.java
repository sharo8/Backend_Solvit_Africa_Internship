package com.solvit.internship_system.dto.user;

import com.solvit.internship_system.entity.Role;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequestDTO {

    private String firstName;
    private String lastName;

    @Email(message = "Invalid email format")
    private String email;

    private Role role;
    private String universityId;
    private Boolean active;
    private String profilePhotoUrl;
}
