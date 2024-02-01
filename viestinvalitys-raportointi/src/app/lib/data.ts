import { cookies, headers } from 'next/headers'
import { LahetysHakuParams } from './types'
import { apiUrl, cookieName, loginUrl } from './configurations'
import { redirect } from 'next/navigation'

// TODO apuwrapperi headerien asettamiseen ja virheenkäsittelyyn
export async function fetchLahetykset(hakuParams: LahetysHakuParams) {
    const sessionCookie = cookies().get(cookieName)
    if (sessionCookie === undefined) {
      console.info('no session cookie, redirect to login')
      redirect(loginUrl)
    }
    const fetchUrlBase = `${apiUrl}/lahetykset/lista?enintaan=20`
    var fetchParams = hakuParams.seuraavatAlkaen ? `&alkaen=${hakuParams.seuraavatAlkaen}` : ''
    if(hakuParams.hakukentta && hakuParams.hakusana) {
      fetchParams += `&${hakuParams.hakukentta}=${hakuParams.hakusana}`
    }
    console.info(fetchUrlBase.concat(fetchParams))
    const cookieParam = sessionCookie.name+'='+sessionCookie.value
    const res = await fetch(fetchUrlBase.concat(fetchParams),{
        headers: { cookie: cookieParam ?? '' }, // Forward the authorization header
        cache: 'no-store'
      })
    console.info(res.status)
    if (!(res.ok || res.status===400 || res.status===410)) {
      if(res.status===401) {
        console.info('http 401, redirect to login')
        redirect(loginUrl)
      }
      // This will activate the closest `error.js` Error Boundary
      throw new Error(res.statusText)
    }
    return res.json()
  }

  export async function fetchLahetys(lahetysTunnus: string) {
    const sessionCookie = cookies().get(cookieName)
    if (sessionCookie === undefined) {
      console.info('no session cookie, redirect to login')
      redirect(loginUrl)
    }
    const url = `${apiUrl}/lahetykset/${lahetysTunnus}`
    console.log(url)
    const cookieParam = sessionCookie.name+'='+sessionCookie.value
    const res = await fetch(url,{
        headers: { cookie: cookieParam ?? '' }, // Forward the authorization header
        cache: 'no-store'
      })
    console.log(res.status)
    if (!res.ok || res.status===400 || res.status===410) {
      if(res.status===401) {
        redirect(loginUrl)
      }
      // This will activate the closest `error.js` Error Boundary
      throw new Error(res.statusText)
    }
    return res.json()  
  }

  export async function fetchLahetyksenVastaanottajat(lahetysTunnus: string) {
    const sessionCookie = cookies().get(cookieName)
    if (sessionCookie === undefined) {
      console.info('no session cookie, redirect to login')
      redirect(loginUrl)
    }
    const headersInstance = headers()
    const url = `${apiUrl}/lahetykset/${lahetysTunnus}/vastaanottajat`
    console.log(url)
    const cookieParam = sessionCookie.name+'='+sessionCookie.value
    const res = await fetch(url,{
        headers: { cookie: cookieParam ?? '' }, // Forward the authorization header
        cache: 'no-store'
      })
    console.log(res.status)
    if (!res.ok) {
      if(res.status===401) {
        redirect(loginUrl)
      }
      // This will activate the closest `error.js` Error Boundary
      throw new Error(res.statusText)
    }
    return res.json()
  }