package com.jinwoo.jobfit.domain.user.repository;

import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
}
