import Link from 'next/link'
 
export default function NotFound() {
  return (
    <div>
      <h2>Sivua ei löytynyt</h2>
      <p>Etsittyä sivua ei löytynyt.</p>
      <Link href="/">Palaa aloitussivulle</Link>
    </div>
  )
}