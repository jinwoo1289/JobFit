package com.jinwoo.jobfit.domain.user.repository;

import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {

    List<UserSkill> findByUserProfileId(Long userProfileId);
}
