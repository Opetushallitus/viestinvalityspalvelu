import { headers } from 'next/headers'
import { LahetysHakuParams } from './types'
import { apiUrl, loginUrl } from './configurations'
import { redirect } from 'next/navigation'

// TODO apuwrapperi headerien asettamiseen 
export async function fetchLahetykset(hakuParams: LahetysHakuParams) {
    console.info(hakuParams)
    const headersInstance = headers()
    const fetchUrlBase = `${apiUrl}/lahetykset/lista?enintaan=2`
    const fetchParams = hakuParams.seuraavatAlkaen ? `&alkaen=${hakuParams.seuraavatAlkaen}` : ''
    console.info(fetchUrlBase.concat(fetchParams))
    const res = await fetch(fetchUrlBase.concat(fetchParams),{
        headers: { cookie: headersInstance.get('cookie') ?? '' }, // Forward the authorization header
      })
    console.info(res.status)
    if (!res.ok) {
      if(res.status===401) {
        redirect(loginUrl)
      }
      // This will activate the closest `error.js` Error Boundary
      throw new Error('Failed to fetch data')
    }
    return res.json()
  }

  export async function fetchLahetys(lahetysTunnus: string) {
    const headersInstance = headers()
    const url = `${apiUrl}/lahetykset/${lahetysTunnus}`
    console.log(url)
    const res = await fetch(url,{
        headers: { cookie: headersInstance.get('cookie') ?? '' }, // Forward the authorization header
      })
    console.log(res.status)
    if (!res.ok) {
      if(res.status===401) {
        redirect(loginUrl)
      }
      // This will activate the closest `error.js` Error Boundary
      throw new Error('Failed to fetch data')
    }
    return res.json()  
  }

  export async function fetchLahetyksenVastaanottajat(lahetysTunnus: string) {
    const headersInstance = headers()
    const url = `${apiUrl}/lahetykset/${lahetysTunnus}/vastaanottajat`
    console.log(url)
    const res = await fetch(url,{
        headers: { cookie: headersInstance.get('cookie') ?? '' }, // Forward the authorization header
      })
    console.log(res.status)
    if (!res.ok) {
      if(res.status===401) {
        redirect(loginUrl)
      }
      // This will activate the closest `error.js` Error Boundary
      throw new Error('Failed to fetch data')
    }
    return res.json()
  }