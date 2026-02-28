package com.kapil.reddit.user.dto;

import java.util.List;
import lombok.Data;

@Data
public class UpdateUserRolesRequest {
    private List<String> roles;
}
