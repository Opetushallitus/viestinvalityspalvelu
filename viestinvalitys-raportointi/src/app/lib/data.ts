'use server'; // täytyy olla eksplisiittisesti koska käytetään client-komponentista react-querylla
import {
  LahetysHakuParams,
  OrganisaatioSearchResult,
  VastaanottajatHakuParams,
} from './types';
import { apiUrl, virkailijaUrl } from './configurations';
import { makeRequest } from './http-client';

const LAHETYKSET_SIVUTUS_KOKO = 20;
const VASTAANOTTAJAT_SIVUTUS_KOKO = 10;
const REVALIDATE_TIME_SECONDS = 60 * 60 * 2;
const REVALIDATE_ASIOINTIKIELI = 60;

export async function fetchLahetykset(hakuParams: LahetysHakuParams) {
  const fetchUrlBase = `${apiUrl}/lahetykset/lista?enintaan=${LAHETYKSET_SIVUTUS_KOKO}`;
  // eslint-disable-next-line no-var
  let fetchParams = hakuParams.seuraavatAlkaen
    ? `&alkaen=${hakuParams.seuraavatAlkaen}`
    : '';
  if (hakuParams?.hakukentta && hakuParams.hakusana) {
    fetchParams += `&${hakuParams.hakukentta}=${hakuParams.hakusana}`;
  }
  if (hakuParams?.organisaatio) {
    fetchParams += `&organisaatio=${hakuParams.organisaatio}`;
  }
  if (hakuParams?.palvelu) {
    fetchParams += `&palvelu=${hakuParams.palvelu}`;
  }
  if (hakuParams?.hakuAlkaen) {
    fetchParams += `&hakuAlkaen=${hakuParams.hakuAlkaen}`;
  }
  if (hakuParams?.hakuPaattyen) {
    fetchParams += `&hakuPaattyen=${hakuParams.hakuPaattyen}`;
  }
  const res = await makeRequest(fetchUrlBase.concat(fetchParams), {
    cache: 'no-store',
  });
  return res.data;
}

export async function fetchLahetys(lahetysTunnus: string) {
  const url = `${apiUrl}/lahetykset/${lahetysTunnus}`;
  const res = await makeRequest(url, {
    cache: 'no-store',
  });
  return res.data;
}

export async function fetchLahetyksenVastaanottajat(
  lahetysTunnus: string,
  hakuParams: VastaanottajatHakuParams,
) {
  const url = `${apiUrl}/lahetykset/${lahetysTunnus}/vastaanottajat?enintaan=${VASTAANOTTAJAT_SIVUTUS_KOKO}`;
  // eslint-disable-next-line no-var
  var fetchParams = hakuParams.alkaen ? `&alkaen=${hakuParams.alkaen}` : '';
  if (hakuParams?.hakukentta && hakuParams.hakusana) {
    fetchParams += `&${hakuParams.hakukentta}=${hakuParams.hakusana}`;
  }
  if (hakuParams?.tila) {
    fetchParams += `&tila=${hakuParams.tila}`;
  }
  if (hakuParams?.organisaatio) {
    fetchParams += `&organisaatio=${hakuParams.organisaatio}`;
  }
  const res = await makeRequest(url.concat(fetchParams), {
    cache: 'no-store',
  });
  return res.data;
}

export async function fetchMassaviesti(lahetysTunnus: string) {
  const url = `${apiUrl}/massaviesti/${lahetysTunnus}`;
  const res = await makeRequest(url, {
    cache: 'no-store',
  });
  return res.data;
}

export async function fetchViesti(viestiTunnus: string) {
  const url = `${apiUrl}/viesti/${viestiTunnus}`;
  const res = await makeRequest(url, {
    cache: 'no-store',
  });
  return res.data;
}

export async function fetchAsiointikieli() {
  const url = `${apiUrl}/asiointikieli`;
  const res = await makeRequest(url, {
    next: { revalidate: REVALIDATE_ASIOINTIKIELI }
  });
  return res.data;
}

export async function fetchLokalisaatiot(lang: string) {
  // ei tarvi käyttää http-clientia kun ei vaadi tunnistautumista ja on tiedostot fallbackina
  const url = `${virkailijaUrl}/lokalisointi/cxf/rest/v1/localisation?category=viestinvalitys&locale=`;
  const res = await fetch(`${url}${lang}`, {
    next: { revalidate: REVALIDATE_TIME_SECONDS },
  });
  return res.json()
}

export async function fetchOrganisaatioRajoitukset() {
  const url = `${apiUrl}/organisaatiot/oikeudet`;
  const res = await makeRequest(url, {
    cache: 'no-store',
  });
  return res.data;
}

export async function searchOrganisaatio(searchStr: string): Promise<OrganisaatioSearchResult> {
  console.info('haetaan organisaatiota');
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
  return res.json();
}

export async function fetchLahettavatPalvelut(): Promise<string[]> {
  const url = `${apiUrl}/palvelut`;
  const res = await makeRequest(url, {
    cache: 'no-store',
  });
  return res.data;
}
