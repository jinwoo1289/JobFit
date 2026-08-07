package com.jinwoo.jobfit.domain.user.dto;

import com.jinwoo.jobfit.domain.user.entity.UserProject;

import java.util.List;

public record UserProjectResponse(
        Long id,
        String projectName,
        String description,
        List<String> techStack
) {
    public static UserProjectResponse from(UserProject entity) {
        return new UserProjectResponse(
                entity.getId(),
                entity.getProjectName(),
                entity.getDescription(),
                entity.getTechStack()
        );
    }
}
