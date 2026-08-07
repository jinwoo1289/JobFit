package com.jinwoo.jobfit.domain.user.dto;

import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UserProjectRequest(
        @NotBlank String projectName,
        String description,
        List<String> techStack
) {
    public UserProject toEntity(UserProfile userProfile) {
        return new UserProject(userProfile, projectName, description, techStack);
    }
}
