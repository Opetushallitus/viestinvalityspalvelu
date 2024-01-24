import { cookieName, loginUrl } from '@/app/lib/configurations'
import { NextResponse } from 'next/server'
import type { NextRequest } from 'next/server'

export async function middleware(request: NextRequest) {
  console.info('middleware')
  const cookie = request.cookies.get(cookieName)
  if (cookie === undefined) {
    console.info('redirect to login')
    return NextResponse.redirect(loginUrl);
  }
  return NextResponse.next(); // Pass control to the next Middleware or route handler
}

export const config = {
  matcher: [
    /*
     * Match all request paths except for the ones starting with:
     * - api (API routes)
     * - _next/static (static files)
     * - _next/image (image optimization files)
     * - login redirect
     * - favicon.ico (favicon file)
     */
    '/((?!api|_next/static|_next/image|login|favicon.ico).*)',
  ],
}