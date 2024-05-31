'use client'

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string }
  reset: () => void
}) {
  return (
    <html>
      <body>
        <h2>Tapahtui virhe</h2>
        <p>Järjestelmässä tapahtui virhe</p>
      </body>
    </html>
  )
}
