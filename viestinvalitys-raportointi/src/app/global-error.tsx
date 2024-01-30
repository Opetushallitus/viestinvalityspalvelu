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
        <button onClick={() => reset()}>Yritä uudelleen</button>
      </body>
    </html>
  )
}
