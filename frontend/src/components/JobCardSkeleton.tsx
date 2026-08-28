function JobCardSkeleton() {
  return (
    <article className="job-card job-card-skeleton" aria-hidden="true">
      <header className="job-card-header">
        <div className="skeleton-block skeleton-title-group">
          <span className="skeleton-line skeleton-line-sm" />
          <span className="skeleton-line skeleton-line-lg" />
        </div>
        <span className="skeleton-line skeleton-badge" />
      </header>
      <span className="skeleton-line skeleton-total" />
      <div className="job-card-scores">
        {Array.from({ length: 4 }).map((_, i) => (
          <span key={i} className="skeleton-line skeleton-score" />
        ))}
      </div>
      <span className="skeleton-line skeleton-line-full" />
      <span className="skeleton-line skeleton-line-full" />
    </article>
  )
}

export default JobCardSkeleton
