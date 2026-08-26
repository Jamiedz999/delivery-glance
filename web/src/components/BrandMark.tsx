/** The wordmark's mark: a parcel, shared by the app header and the Sign in screen. */
export function BrandMark() {
  return (
    <span className="app-mark" aria-hidden="true">
      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2">
        <rect x="4" y="7" width="16" height="13" rx="1.5" />
        <path d="M4 11h16" />
      </svg>
    </span>
  )
}
