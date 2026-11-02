package com.jinwoo.jobfit.domain.user.entity;

import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 사용자가 공고에 "지원 완료" 체크한 기록. 체크하면 행을 만들고 해제하면 삭제한다.
// 평가 결과·추출·checkPoints 캐시와는 무관하며, 평가 응답을 만들 때만 합친다.
@Entity
@Table(
        name = "job_applications",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_application_profile_posting",
                columnNames = {"user_profile_id", "job_posting_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id", nullable = false)
    private UserProfile userProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;
}
