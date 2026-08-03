package com.jinwoo.jobfit.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Entity
@Table(name = "user_projects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @Column(name = "project_name", nullable = false, length = 200)
    private String projectName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ElementCollection
    @CollectionTable(name = "user_project_tech_stacks", joinColumns = @JoinColumn(name = "user_project_id"))
    @Column(name = "tech_stack", nullable = false, length = 100)
    private List<String> techStack;

    public UserProject(UserProfile userProfile, String projectName, String description, List<String> techStack) {
        this.userProfile = userProfile;
        this.projectName = projectName;
        this.description = description;
        this.techStack = normalize(techStack);
    }

    private static List<String> normalize(List<String> techStack) {
        if (techStack == null) {
            return new ArrayList<>();
        }
        return techStack.stream()
                .filter(Objects::nonNull)
                .map(tech -> tech.trim().toLowerCase(Locale.ROOT))
                .filter(tech -> !tech.isBlank())
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
