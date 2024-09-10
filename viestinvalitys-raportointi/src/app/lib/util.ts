import {
  EPAONNISTUNEET_TILAT,
  KESKENERAISET_TILAT,
  ONNISTUNEET_TILAT,
  Status,
  VastaanotonTila,
  LahetyksenVastaanottoTila,
  Organisaatio,
  LanguageCode,
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
  return 'tuntematon';
};

export const getVastaanottajatPerStatus = (
  tilat: LahetyksenVastaanottoTila[],
): number => {
  const lahetysStatus = getLahetysStatus(
    tilat.map((tila) => tila.vastaanottotila),
  );
  if (lahetysStatus === Status.ONNISTUI) {
    return tilat
      .filter((tila) => ONNISTUNEET_TILAT.includes(tila.vastaanottotila))
      .map((tila) => tila.vastaanottajaLkm)
      .reduce(function (a, b) {
        return a + b;
      });
  }
  if (lahetysStatus === Status.EPAONNISTUI) {
    return tilat
      .filter((tila) => EPAONNISTUNEET_TILAT.includes(tila.vastaanottotila))
      .map((tila) => tila.vastaanottajaLkm)
      .reduce(function (a, b) {
        return a + b;
      });
  }
  if (lahetysStatus === Status.KESKEN) {
    return tilat
      .filter((tila) => KESKENERAISET_TILAT.includes(tila.vastaanottotila))
      .map((tila) => tila.vastaanottajaLkm)
      .reduce(function (a, b) {
        return a + b;
      });
  }
  return 0; // tuntematon tila
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
  locale: LanguageCode,
  result: { oid: string; parentOidPath: string }[],
): void => {
  for (const org of orgs) {
    // Täsmääkö nimi hakustringiin
    const name = translateOrgName(org, locale); // nyt matchataan vain käyttäjän kielellä
    if (name && name.toLowerCase().includes(searchString.toLowerCase())) {
      // Jos matchaa, lisätään kokoelmaan oid ja parent-polku
      result.push({ oid: org.oid, parentOidPath: org.parentOidPath });
    }
    // Rekursiivisesti lapsiorganisaatiot
    collectOrgsWithMatchingName(org.children, searchString, locale, result);
  }
};

export function translateOrgName(
  organisaatio: Organisaatio | undefined,
  userLanguage: LanguageCode = 'fi',
): string {
  if (!organisaatio) {
    return '';
  }
  const translation = organisaatio.nimi[userLanguage];
  if (translation && translation?.trim().length > 0) {
    return organisaatio.nimi[userLanguage] || '';
  } else if (organisaatio.nimi.fi && organisaatio.nimi.fi.trim().length > 0) {
    return organisaatio.nimi.fi;
  } else if (organisaatio.nimi.sv && organisaatio.nimi.sv.trim().length > 0) {
    return organisaatio.nimi.sv;
  }
  return organisaatio.nimi.en || '';
}