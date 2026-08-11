package com.jinwoo.jobfit.domain.user.repository;

import com.jinwoo.jobfit.domain.user.entity.UserCertificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserCertificateRepository extends JpaRepository<UserCertificate, Long> {

    List<UserCertificate> findByUserProfileId(Long userProfileId);
}
