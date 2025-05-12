const environments = ["hahtuva", "dev", "qa", "prod"] as const;
type EnvironmentName = (typeof environments)[number];

export type Config = {
  vpcCidr: string;
  zoneName: string;
  domainName: string;
  opintopolkuDomainName: string;
  mailFromDomainName: string;
  mode: "PRODUCTION" | "TEST";
  opintopolkuCloudFront: {
    domainName: string;
    distributionId: string;
  };
  systemEnabled: boolean;
};

export function getEnvironment(): EnvironmentName {
  const env = process.env.ENV;
  if (!env) {
    throw new Error("ENV environment variable is not set");
  }
  if (!contains(environments, env)) {
    throw new Error(`Invalid environment name: ${env}`);
  }
  return env as EnvironmentName;
}

function contains(arr: readonly string[], value: string): boolean {
  return arr.includes(value);
}

export function getConfig(): Config {
  const env = getEnvironment();
  return { hahtuva, dev, qa, prod }[env];
}

export const hahtuva: Config = {
  vpcCidr: "10.22.0.0/18",
  zoneName: "hahtuva.viestinvalitys.opintopolku.fi",
  domainName: "viestinvalitys.hahtuva.viestinvalitys.opintopolku.fi",
  opintopolkuDomainName: "hahtuvaopintopolku.fi",
  mailFromDomainName: "email.hahtuvaopintopolku.fi",
  mode: "TEST",
  opintopolkuCloudFront: {
    domainName: "d29781on2s72yc.cloudfront.net",
    distributionId: "E1K0KYSFU4HWO5",
  },
  systemEnabled: true,
};

export const dev: Config = {
  vpcCidr: "10.22.64.0/18",
  zoneName: "dev.viestinvalitys.opintopolku.fi",
  domainName: "viestinvalitys.dev.viestinvalitys.opintopolku.fi",
  opintopolkuDomainName: "untuvaopintopolku.fi",
  mailFromDomainName: "email.untuvaopintopolku.fi",
  mode: "TEST",
  opintopolkuCloudFront: {
    domainName: "d35h85pghp8cqy.cloudfront.net",
    distributionId: "E3REFB9SPEV4PV",
  },
  systemEnabled: true,
};

export const qa: Config = {
  vpcCidr: "10.22.128.0/18",
  zoneName: "qa.viestinvalitys.opintopolku.fi",
  domainName: "viestinvalitys.qa.viestinvalitys.opintopolku.fi",
  opintopolkuDomainName: "testiopintopolku.fi",
  mailFromDomainName: "email.testiopintopolku.fi",
  mode: "TEST",
  opintopolkuCloudFront: {
    domainName: "d2epf4u223qlfr.cloudfront.net",
    distributionId: "EU43J29PK2P30",
  },
  systemEnabled: true,
};

export const prod: Config = {
  vpcCidr: "10.22.192.0/18",
  zoneName: "prod.viestinvalitys.opintopolku.fi",
  domainName: "viestinvalitys.prod.viestinvalitys.opintopolku.fi",
  opintopolkuDomainName: "opintopolku.fi",
  mailFromDomainName: "email.opintopolku.fi",
  mode: "PRODUCTION",
  opintopolkuCloudFront: {
    domainName: "d1v2x05v3n904u.cloudfront.net",
    distributionId: "E2Q74G9M3B51RU",
  },
  systemEnabled: true,
};
