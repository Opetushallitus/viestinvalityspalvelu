import { cookies } from 'next/headers';
import { LahetysHakuParams, VastaanottajatHakuParams } from './types';
import { apiUrl, cookieName, loginUrl } from './configurations';
import { redirect } from 'next/navigation';

const LAHETYKSET_SIVUTUS_KOKO = 20;
const VASTAANOTTAJAT_SIVUTUS_KOKO = 10;
// TODO apuwrapperi headerien asettamiseen ja virheenkäsittelyyn
export async function fetchLahetykset(hakuParams: LahetysHakuParams) {
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
  return res.json();
}

export async function fetchLahetys(lahetysTunnus: string) {
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
  return res.json();
}

export async function fetchLahetyksenVastaanottajat(
  lahetysTunnus: string,
  hakuParams: VastaanottajatHakuParams
) {
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
  return res.json();
}

export async function fetchOrganisaatioHierarkia(oids?: string[]) {
  const sessionCookie = cookies().get(cookieName);
  if (sessionCookie === undefined) {
    console.info('no session cookie, redirect to login');
    redirect(loginUrl);
  }
  const url = `${apiUrl}/organisaatiot`;
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
    throw new Error('organisaatioiden haku epäonnistui');
  }
  return res.json();
}
