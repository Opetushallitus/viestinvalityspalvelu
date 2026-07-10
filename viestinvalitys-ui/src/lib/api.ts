import { LahetysHakuParams, VastaanottajatHakuParams, OrganisaatioSearchResult } from './types';
import { paatteleHakuParametri } from './util';

const API_BASE = '/raportointi/v1';
const VIRKAILIJA_BASE = '';

const CALLER_ID = '1.2.246.562.10.00000000001.viestinvalityspalvelu';

const LAHETYKSET_SIVUTUS_KOKO = 20;
const VASTAANOTTAJAT_SIVUTUS_KOKO = 10;

async function apiFetch(url: string): Promise<Response> {
  const res = await fetch(url, {
    headers: {
      'Caller-Id': CALLER_ID,
      CSRF: CALLER_ID,
    },
    credentials: 'include',
  });
  if (res.status === 401) {
    window.location.href = '/raportointi/login';
    throw new Error('Unauthenticated');
  }
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`);
  }
  return res;
}

export async function fetchLahetykset(params: LahetysHakuParams) {
  let url = `${API_BASE}/lahetykset/lista?enintaan=${LAHETYKSET_SIVUTUS_KOKO}`;
  if (params.seuraavatAlkaen) url += `&alkaen=${params.seuraavatAlkaen}`;
  if (params.hakusana) {
    const kentta = paatteleHakuParametri(params.hakusana);
    url += `&${kentta}=${encodeURIComponent(params.hakusana)}`;
  }
  if (params.organisaatio) url += `&organisaatio=${params.organisaatio}`;
  if (params.palvelu) url += `&palvelu=${encodeURIComponent(params.palvelu)}`;
  if (params.hakuAlkaen) url += `&hakuAlkaen=${params.hakuAlkaen}`;
  if (params.hakuPaattyen) url += `&hakuPaattyen=${params.hakuPaattyen}`;
  const res = await apiFetch(url);
  return res.json();
}

export async function fetchLahetys(tunniste: string) {
  const res = await apiFetch(`${API_BASE}/lahetykset/${tunniste}`);
  return res.json();
}

export async function fetchLahetyksenVastaanottajat(
  tunniste: string,
  params: VastaanottajatHakuParams,
) {
  let url = `${API_BASE}/lahetykset/${tunniste}/vastaanottajat?enintaan=${VASTAANOTTAJAT_SIVUTUS_KOKO}`;
  if (params.alkaen) url += `&alkaen=${params.alkaen}`;
  if (params.hakusana) {
    const kentta = paatteleHakuParametri(params.hakusana);
    url += `&${kentta}=${encodeURIComponent(params.hakusana)}`;
  }
  if (params.tila) url += `&tila=${params.tila}`;
  if (params.organisaatio) url += `&organisaatio=${params.organisaatio}`;
  const res = await apiFetch(url);
  return res.json();
}

export async function fetchMassaviesti(tunniste: string) {
  const res = await apiFetch(`${API_BASE}/massaviesti/${tunniste}`);
  return res.json();
}

export async function fetchViesti(tunniste: string) {
  const res = await apiFetch(`${API_BASE}/viesti/${tunniste}`);
  return res.json();
}

export async function fetchAsiointikieli() {
  const res = await apiFetch(`${API_BASE}/asiointikieli`);
  return res.json();
}

export async function fetchLahettavatPalvelut(): Promise<string[]> {
  const res = await apiFetch(`${API_BASE}/palvelut`);
  return res.json();
}

export async function fetchOrganisaatioRajoitukset(): Promise<string[]> {
  const res = await apiFetch(`${API_BASE}/organisaatiot/oikeudet`);
  return res.json();
}

export async function searchOrganisaatio(searchStr: string): Promise<OrganisaatioSearchResult> {
  const oidRestrictions = await fetchOrganisaatioRajoitukset();
  const oidParams =
    oidRestrictions.length > 0
      ? '&oidRestrictionList=' + oidRestrictions.join('&oidRestrictionList=')
      : '';
  const url = `${VIRKAILIJA_BASE}/organisaatio-service/api/hierarkia/hae?aktiiviset=true&suunnitellut=false&lakkautetut=false${oidParams}&searchStr=${encodeURIComponent(searchStr)}&skipParents=false`;
  const res = await fetch(url, {
    headers: {
      callerId: CALLER_ID,
      csrf: CALLER_ID,
    },
  });
  return res.json();
}
