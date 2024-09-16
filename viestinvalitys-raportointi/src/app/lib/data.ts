'use server'; // täytyy olla eksplisiittisesti koska käytetään client-komponentista react-querylla
import { cookies } from 'next/headers';
import { LahetysHakuParams, OrganisaatioSearchResult, VastaanottajatHakuParams } from './types';
import { apiUrl, cookieName, loginUrl, virkailijaUrl } from './configurations';
import { redirect } from 'next/navigation';

const LAHETYKSET_SIVUTUS_KOKO = 20;
const VASTAANOTTAJAT_SIVUTUS_KOKO = 10;
const REVALIDATE_TIME_SECONDS = 60 * 60 * 2;
const REVALIDATE_ASIOINTIKIELI = 60;

// TODO apuwrapperi headerien asettamiseen ja virheenkäsittelyyn
export async function fetchLahetykset(hakuParams: LahetysHakuParams) {
  const sessionCookie = cookies().get(cookieName);
  if (sessionCookie === undefined) {
    console.info('no session cookie, redirect to login');
    redirect(loginUrl);
  }
  const fetchUrlBase = `${apiUrl}/lahetykset/lista?enintaan=${LAHETYKSET_SIVUTUS_KOKO}`;
  // eslint-disable-next-line no-var
  var fetchParams = hakuParams.seuraavatAlkaen
    ? `&alkaen=${hakuParams.seuraavatAlkaen}`
    : '';
  if (hakuParams?.hakukentta && hakuParams.hakusana) {
    fetchParams += `&${hakuParams.hakukentta}=${hakuParams.hakusana}`;
  }
  if (hakuParams?.organisaatio) {
    fetchParams += `&organisaatio=${hakuParams.organisaatio}`;
  }
  if(hakuParams?.palvelu) {
    fetchParams += `&palvelu=${hakuParams.palvelu}`;
  }
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
  hakuParams: VastaanottajatHakuParams,
) {
  const sessionCookie = cookies().get(cookieName);
  if (sessionCookie === undefined) {
    console.info('no session cookie, redirect to login');
    redirect(loginUrl);
  }
  const url = `${apiUrl}/lahetykset/${lahetysTunnus}/vastaanottajat?enintaan=${VASTAANOTTAJAT_SIVUTUS_KOKO}`;
  // eslint-disable-next-line no-var
  var fetchParams = hakuParams.alkaen
    ? `&alkaen=${hakuParams.alkaen}`
    : '';
  if (hakuParams?.hakukentta && hakuParams.hakusana) {
    fetchParams += `&${hakuParams.hakukentta}=${hakuParams.hakusana}`;
  }
  if (hakuParams?.tila) {
    fetchParams += `&tila=${hakuParams.tila}`;
  }
  if (hakuParams?.organisaatio) {
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

export async function fetchMassaviesti(lahetysTunnus: string) {
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
  return res.json();
}

export async function fetchViesti(viestiTunnus: string) {
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
  console.info(res.status);
  if (!(res.ok || res.status === 400 || res.status === 410)) {
    if (res.status === 401) {
      redirect(loginUrl);
    }
    // This will activate the closest `error.js` Error Boundary
    throw new Error(res.statusText);
  }
  return res.json();
}

export async function fetchAsiointikieli() {
  const sessionCookie = cookies().get(cookieName);
  if (sessionCookie === undefined) {
    console.info('no session cookie, redirect to login');
    console.info(loginUrl);
    redirect(loginUrl);
  }
  const url = `${apiUrl}/omattiedot`;
  const cookieParam = sessionCookie.name + '=' + sessionCookie.value;
  const res = await fetch(url, {
    headers: { cookie: cookieParam ?? '' }, // Forward the authorization header
    next: { revalidate: REVALIDATE_ASIOINTIKIELI }
  });
  if (!(res.ok || res.status === 400 || res.status === 410)) {
    if (res.status === 401) {
      redirect(loginUrl);
    }
    // This will activate the closest `error.js` Error Boundary
    throw new Error('asiointikielen haku epäonnistui');
  }
  return res.json();
}

export async function fetchLokalisaatiot(lang: string) {
  const url = `${virkailijaUrl}/lokalisointi/cxf/rest/v1/localisation?category=viestinvalitys&locale=`;
  const res = await fetch(`${url}${lang}`, {
    next: { revalidate: REVALIDATE_TIME_SECONDS },
  });
  return res.json()
}

export async function fetchOrganisaatioRajoitukset() {
  const sessionCookie = cookies().get(cookieName);
  if (sessionCookie === undefined) {
    console.info('no session cookie, redirect to login');
    redirect(loginUrl);
  }
  const url = `${apiUrl}/organisaatiot/oikeudet`;
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
    throw new Error('organisaatio-oikeuksien haku epäonnistui');
  }
  return res.json();
}

export async function searchOrganisaatio(searchStr: string): Promise<OrganisaatioSearchResult> {
  console.info('haetaan organisaatiota');
  const sessionCookie = cookies().get(cookieName);
  if (sessionCookie === undefined) {
    console.info('no session cookie, redirect to login');
    redirect(loginUrl);
  }
  // organisaatiorajaus oikeuksien mukaan
  const oidRestrictionList: string[] = await fetchOrganisaatioRajoitukset();
  const oidRestrictionParams =
    oidRestrictionList.length > 0
      ? '&oidRestrictionList=' + oidRestrictionList.join('&oidRestrictionList=')
      : '';
  const url = `${virkailijaUrl}/organisaatio-service/api/hierarkia/hae?aktiiviset=true&suunnitellut=false&lakkautetut=false${oidRestrictionParams}&searchStr=${searchStr}&skipParents=false`;
  const res = await fetch(url, {
    headers: {
      callerId: '1.2.246.562.10.00000000001.viestinvalityspalvelu',
      csrf: '1.2.246.562.10.00000000001.viestinvalityspalvelu',
    },
  });
  if (!(res.ok || res.status === 400 || res.status === 410)) {
    // This will activate the closest `error.js` Error Boundary
    throw new Error('organisaation haku epäonnistui');
  }
  return res.json();
}

export async function fetchLahettavatPalvelut(): Promise<string[]> {
  console.info('haetaan lähettävät palvelut');
  const sessionCookie = cookies().get(cookieName);
  if (sessionCookie === undefined) {
    console.info('no session cookie, redirect to login');
    redirect(loginUrl);
  }
  const url = `${apiUrl}/palvelut`;
  const cookieParam = sessionCookie.name + '=' + sessionCookie.value;
  const res = await fetch(url, {
    headers: { cookie: cookieParam ?? '' }, // Forward the authorization header
    cache: 'no-store',
  });
  console.info(res.status)
  if (!(res.ok || res.status === 400 || res.status === 410)) {
    if (res.status === 401) {
      redirect(loginUrl);
    }
    // This will activate the closest `error.js` Error Boundary
    throw new Error('lähettävien palvelujen haku epäonnistui');
  }
  return res.json();
}
