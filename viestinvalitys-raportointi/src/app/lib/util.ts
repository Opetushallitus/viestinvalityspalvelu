import {
  EPAONNISTUNEET_TILAT,
  KESKENERAISET_TILAT,
  ONNISTUNEET_TILAT,
  Status,
  VastaanotonTila,
  LahetyksenVastaanottoTila,
  Organisaatio,
} from './types';

export const getLahetyksenVastaanottajia = (
  tilat: LahetyksenVastaanottoTila[],
): number => {
  return tilat
    .map((tila) => tila.vastaanottajaLkm)
    .reduce(function (a, b) {
      return a + b;
    });
};

export const lahetyksenStatus = (
  tilat: LahetyksenVastaanottoTila[] | undefined,
): string => {
  if (!tilat || tilat.length < 1) {
    return ' ei viestejä/vastaanottajia';
  }
  const status = `${getVastaanottajatPerStatus(
    tilat,
  )}/${getLahetyksenVastaanottajia(tilat)} viestin lähetys ${getLahetysStatus(
    tilat.map((tila) => tila.vastaanottotila),
  )}`;
  return status;
};

export const getLahetysStatus = (tilat: VastaanotonTila[]): string => {
  if (tilat.filter((tila) => EPAONNISTUNEET_TILAT.includes(tila)).length > 0) {
    return Status.EPAONNISTUI;
  }
  if (tilat.filter((tila) => KESKENERAISET_TILAT.includes(tila)).length > 0) {
    return Status.KESKEN;
  }
  if (tilat.filter((tila) => ONNISTUNEET_TILAT.includes(tila)).length > 0) {
    return Status.ONNISTUI;
  }
  return 'tuntematon tila';
};

export const getVastaanottajatPerStatus = (
  tilat: LahetyksenVastaanottoTila[],
): number => {
  const lahetysStatus = getLahetysStatus(
    tilat.map((tila) => tila.vastaanottotila),
  );
  if (lahetysStatus === 'onnistui') {
    return tilat
      .filter((tila) => ONNISTUNEET_TILAT.includes(tila.vastaanottotila))
      .map((tila) => tila.vastaanottajaLkm)
      .reduce(function (a, b) {
        return a + b;
      });
  }
  if (lahetysStatus === 'epäonnistui') {
    return tilat
      .filter((tila) => EPAONNISTUNEET_TILAT.includes(tila.vastaanottotila))
      .map((tila) => tila.vastaanottajaLkm)
      .reduce(function (a, b) {
        return a + b;
      });
  }
  if (lahetysStatus === 'kesken') {
    return tilat
      .filter((tila) => KESKENERAISET_TILAT.includes(tila.vastaanottotila))
      .map((tila) => tila.vastaanottajaLkm)
      .reduce(function (a, b) {
        return a + b;
      });
  }
  return 0;
};

export const parseExpandedParents = (
  parentOidPath: string | undefined,
): string[] => {
  if (!parentOidPath || parentOidPath.length < 1) {
    return [];
  }
  return parentOidPath.split('/');
};

export const findOrganisaatioByOid = (
  orgs: Organisaatio[],
  oid: string,
): Organisaatio | undefined => {
  for (const item of orgs) {
    const found = findOrganisaatioRecursive(item, oid);
    if (found) {
      return found;
    }
  }
  return undefined;
};

function findOrganisaatioRecursive(
  org: Organisaatio,
  oid: string,
): Organisaatio | undefined {
  if (org.oid === oid) {
    return org;
  }

  if (org.children) {
    for (const child of org.children) {
      const found = findOrganisaatioRecursive(child, oid);
      if (found) {
        return found;
      }
    }
  }

  return undefined;
}

// oidit organisaatioista joiden nimi täsmää hakustringiin ja parent-polku
export const collectOrgsWithMatchingName = (
  orgs: Organisaatio[],
  searchString: string,
  result: { oid: string; parentOidPath: string }[],
): void => {
  for (const org of orgs) {
    // Täsmääkö nimi hakustringiin
    const name = org.nimi?.fi; // TODO kielistys
    if (name && name.toLowerCase().includes(searchString.toLowerCase())) {
      // Jos matchaa, lisätään kokoelmaan oid ja parent-polku
      result.push({ oid: org.oid, parentOidPath: org.parentOidPath });
    }
    // Rekursiivisesti lapsiorganisaatiot
    collectOrgsWithMatchingName(org.children, searchString, result);
  }
};
