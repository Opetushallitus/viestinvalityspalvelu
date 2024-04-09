'use server'; // täytyy olla eksplisiittisesti koska käytetään client-komponentista swr:llä
import { cookies } from 'next/headers';
import { LahetysHakuParams, VastaanottajatHakuParams } from './types';
import { apiUrl, backendUrl, cookieName, loginUrl } from './configurations';
import { redirect } from 'next/navigation';

const LAHETYKSET_SIVUTUS_KOKO = 20;
const VASTAANOTTAJAT_SIVUTUS_KOKO = 10;
// TODO apuwrapperi headerien asettamiseen ja virheenkäsittelyyn
export async function fetchLahetykset(hakuParams: LahetysHakuParams) {
  console.time("fetchLahetykset");
  console.info('aloitetaan lähetysten haku')
  const sessionCookie = cookies().get(cookieName);
  if (sessionCookie === undefined) {
    console.info('no session cookie, redirect to login');
    redirect(loginUrl);
  }
  const fetchUrlBase = `${apiUrl}/lahetykset/lista?enintaan=${LAHETYKSET_SIVUTUS_KOKO}`;
  console.info(hakuParams);
  var fetchParams = hakuParams.seuraavatAlkaen
    ? `&alkaen=${hakuParams.seuraavatAlkaen}`
    : '';
  if (hakuParams?.hakukentta && hakuParams.hakusana) {
    fetchParams += `&${hakuParams.hakukentta}=${hakuParams.hakusana}`;
  }
  if(hakuParams?.organisaatio) {
    fetchParams += `&organisaatio=${hakuParams.organisaatio}`;
  }
  console.info(fetchUrlBase.concat(fetchParams));
  const cookieParam = sessionCookie.name + '=' + sessionCookie.value;
  const res = await fetch(fetchUrlBase.concat(fetchParams), {
    headers: { cookie: cookieParam ?? '' }, // Forward the authorization header
    cache: 'no-store',
  });
  if (!(res.ok || res.status === 400 || res.status === 410)) {
    if (res.status === 401) {
      console.info('http 401, redirect to login');
      redirect(loginUrl);
    }
    // This will activate the closest `error.js` Error Boundary
    throw new Error(res.statusText);
  }
  console.timeLog("fetchLahetykset");
  console.timeEnd("fetchLahetykset");
  return res.json();
}

export async function fetchLahetys(lahetysTunnus: string) {
  console.time("fetchLahetys");
  console.info('haetaan yksittäinen lähetys')
  const sessionCookie = cookies().get(cookieName);
  if (sessionCookie === undefined) {
    console.info('no session cookie, redirect to login');
    redirect(loginUrl);
  }
  const url = `${apiUrl}/lahetykset/${lahetysTunnus}`;
  const cookieParam = sessionCookie.name + '=' + sessionCookie.value;
  const res = await fetch(url, {
    headers: { cookie: cookieParam ?? '' }, // Forward the authorization header
    cache: 'no-store',
  });
  if (!(res.ok || res.status === 400 || res.status === 410)) {
    if (res.status === 401) {
      redirect(loginUrl);
    }
    // This will activate the closest `error.js` Error Boundary
    throw new Error(res.statusText);
  }
  console.timeLog("fetchLahetys");
  console.timeEnd("fetchLahetys");
  console.info('yksittäisen lähetyksen haku tehty')
  return res.json();
}

export async function fetchLahetyksenVastaanottajat(
  lahetysTunnus: string,
  hakuParams: VastaanottajatHakuParams
) {
  console.time("fetchLahetyksenVastaanottajat");
  console.info('haetaan vastaanottajat')
  const sessionCookie = cookies().get(cookieName);
  if (sessionCookie === undefined) {
    console.info('no session cookie, redirect to login');
    redirect(loginUrl);
  }
  const url = `${apiUrl}/lahetykset/${lahetysTunnus}/vastaanottajat?enintaan=${VASTAANOTTAJAT_SIVUTUS_KOKO}`;
  var fetchParams = hakuParams.alkaen
    ? `&alkaen=${hakuParams.alkaen}&sivutustila=${
        hakuParams.sivutustila || 'kesken'
      }`
    : '';
  if (hakuParams?.hakukentta && hakuParams.hakusana) {
    fetchParams += `&${hakuParams.hakukentta}=${hakuParams.hakusana}`;
  }
  if (hakuParams?.tila) {
    fetchParams += `&tila=${hakuParams.tila}`;
  }
  if(hakuParams?.organisaatio) {
    fetchParams += `&organisaatio=${hakuParams.organisaatio}`;
  }
  const cookieParam = sessionCookie.name + '=' + sessionCookie.value;
  const res = await fetch(url.concat(fetchParams), {
    headers: { cookie: cookieParam ?? '' }, // Forward the authorization header
    cache: 'no-store',
  });
  if (!(res.ok || res.status === 400 || res.status === 410)) {
    if (res.status === 401) {
      redirect(loginUrl);
    }
    // This will activate the closest `error.js` Error Boundary
    throw new Error(res.statusText);
  }
  console.info('vastaanottajat haettu')
  console.timeLog("fetchLahetyksenVastaanottajat");
  console.timeEnd("fetchLahetyksenVastaanottajat");
  return res.json();
}

export async function fetchMassaviesti(lahetysTunnus: string) {
  console.time('fetchMassaviesti')
  console.info('haetaan viesti')
  const sessionCookie = cookies().get(cookieName);
  if (sessionCookie === undefined) {
    console.info('no session cookie, redirect to login');
    redirect(loginUrl);
  }
  const url = `${apiUrl}/massaviesti/${lahetysTunnus}`;
  const cookieParam = sessionCookie.name + '=' + sessionCookie.value;
  const res = await fetch(url, {
    headers: { cookie: cookieParam ?? '' }, // Forward the authorization header
    cache: 'no-store',
  });
  if (!(res.ok || res.status === 400 || res.status === 410)) {
    if (res.status === 401) {
      redirect(loginUrl);
    }
    // This will activate the closest `error.js` Error Boundary
    throw new Error(res.statusText);
  }
  console.info('viestin haku tehty')
  console.timeLog('fetchMassaviesti')
  console.timeEnd('fetchMassaviesti')
  return res.json();
}

export async function fetchViesti(viestiTunnus: string) {
  console.time('fetchViesti')
  console.info('haetaan viesti')
  const sessionCookie = cookies().get(cookieName);
  if (sessionCookie === undefined) {
    console.info('no session cookie, redirect to login');
    redirect(loginUrl);
  }
  const url = `${apiUrl}/viesti/${viestiTunnus}`;
  const cookieParam = sessionCookie.name + '=' + sessionCookie.value;
  const res = await fetch(url, {
    headers: { cookie: cookieParam ?? '' }, // Forward the authorization header
    cache: 'no-store',
  });
  if (!(res.ok || res.status === 400 || res.status === 410)) {
    if (res.status === 401) {
      redirect(loginUrl);
    }
    // This will activate the closest `error.js` Error Boundary
    throw new Error(res.statusText);
  }
  console.info('viestin haku tehty')
  console.timeLog('fetchViesti')
  console.timeEnd('fetchViesti')
  return res.json();
}

export async function fetchOrganisaatioHierarkia(oids?: string[]) {
  console.time('fetchOrganisaatioHierarkia')
  console.info('haetaan organisaatiohierarkia')
  const sessionCookie = cookies().get(cookieName);
  if (sessionCookie === undefined) {
    console.info('no session cookie, redirect to login');
    redirect(loginUrl);
  }
  const url = `${apiUrl}/organisaatiot`;
  const cookieParam = sessionCookie.name + '=' + sessionCookie.value;
  const res = await fetch(url, {
    headers: { cookie: cookieParam ?? '' }, // Forward the authorization header
    cache: 'no-store', // caching in backend
  });
  if (!(res.ok || res.status === 400 || res.status === 410)) {
    if (res.status === 401) {
      redirect(loginUrl);
    }
    // This will activate the closest `error.js` Error Boundary
    throw new Error('organisaatioiden haku epäonnistui');
  }
  console.info('organisaatiohierarkian haku tehty')
  console.timeLog('fetchOrganisaatioHierarkia')
  console.timeEnd('fetchOrganisaatioHierarkia')
  return res.json();
}
