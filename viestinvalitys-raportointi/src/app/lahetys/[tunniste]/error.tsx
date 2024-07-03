'use client'
 
import { useEffect } from 'react'
 
export default function Error({
  error,
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
      <p>Tietojen haussa tapahtui virhe.</p>
    </div>
  )
}