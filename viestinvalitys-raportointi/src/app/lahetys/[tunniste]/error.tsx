'use client'
 
import { useEffect } from 'react'
 
export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string }
  reset: () => void
}) {
  useEffect(() => {
    console.error(error)
  }, [error])
 
  return (
    <div>
      <h2>Tapahtui virhe</h2>
      <p>Error message: {error.message}</p>
      <p>Error digest: {error.digest || ''}</p>
      <button
        onClick={
          // Attempt to recover by trying to re-render the segment
          () => reset()
        }
      >
        Yritä uudelleen
      </button>
    </div>
  )
}