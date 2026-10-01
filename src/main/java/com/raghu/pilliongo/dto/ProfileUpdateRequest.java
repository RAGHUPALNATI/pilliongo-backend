package com.raghu.pilliongo.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProfileUpdateRequest {

    // This DTO is a PARTIAL update (AuthService.updateProfile only changes a
    // field when it's non-null/non-blank), so fields intentionally stay
    // optional here. @NotBlank would reject a request that legitimately
    // omits a field, so we use @Size/@Pattern instead — Bean Validation
    // treats a null value as valid for both, and only checks the format
    // when a value is actually sent.
    @Size(min = 2, message = "Full name must be at least 2 characters")
    private String fullName;

    @Pattern(regexp = "^[0-9]{10}$", message = "Phone must be 10 digits")
    private String phone;

    private String vehicleType;
    private String vehicleModel;
    private String vehiclePlate;
}
