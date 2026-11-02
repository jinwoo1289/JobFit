package com.jinwoo.jobfit.domain.user.repository;

import com.jinwoo.jobfit.domain.user.entity.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    // 이미 체크된 공고를 다시 체크해도(연타·동시 요청 포함) 유니크 제약 위반 없이 기존 행과 체크 시각을 그대로 둔다.
    @Modifying
    @Query(value = """
            INSERT INTO job_applications (user_profile_id, job_posting_id, applied_at)
            VALUES (:userProfileId, :jobPostingId, :appliedAt)
            ON CONFLICT (user_profile_id, job_posting_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userProfileId") Long userProfileId,
                       @Param("jobPostingId") Long jobPostingId,
                       @Param("appliedAt") LocalDateTime appliedAt);

    Optional<JobApplication> findByUserProfileIdAndJobPostingId(Long userProfileId, Long jobPostingId);

    List<JobApplication> findByUserProfileId(Long userProfileId);

    @Modifying
    @Query("DELETE FROM JobApplication a WHERE a.userProfile.id = :userProfileId AND a.jobPosting.id = :jobPostingId")
    int deleteByUserProfileIdAndJobPostingId(@Param("userProfileId") Long userProfileId,
                                             @Param("jobPostingId") Long jobPostingId);

    @Modifying
    @Query("DELETE FROM JobApplication a WHERE a.userProfile.id = :userProfileId")
    int deleteAllByUserProfileId(@Param("userProfileId") Long userProfileId);
}
