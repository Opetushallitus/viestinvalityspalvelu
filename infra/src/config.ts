const environments = ["hahtuva", "dev", "qa", "prod"] as const;
type EnvironmentName = (typeof environments)[number];

export type Config = {
  vpcCidr: string;
  zoneName: string;
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
};

export const dev: Config = {
  vpcCidr: "10.22.64.0/18",
  zoneName: "dev.viestinvalitys.opintopolku.fi",
};

export const qa: Config = {
  vpcCidr: "10.22.128.0/18",
  zoneName: "qa.viestinvalitys.opintopolku.fi",
};

export const prod: Config = {
  vpcCidr: "10.22.192.0/18",
  zoneName: "prod.viestinvalitys.opintopolku.fi",
};
