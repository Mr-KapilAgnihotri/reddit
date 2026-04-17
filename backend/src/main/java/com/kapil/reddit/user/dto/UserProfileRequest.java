package com.kapil.reddit.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

@Getter
@Setter
@NoArgsConstructor
public class UserProfileRequest {

    @Size(max = 50, message = "Display name must be at most 50 characters")
    private String displayName;

    @Size(max = 500, message = "Bio must be at most 500 characters")
    private String bio;

    @URL(message = "Avatar URL must be a valid URL")
    @Size(max = 2048, message = "Avatar URL too long")
    private String avatarUrl;
}
