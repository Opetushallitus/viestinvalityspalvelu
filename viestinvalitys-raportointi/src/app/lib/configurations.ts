export const isDev = process.env.NODE_ENV === 'development';
export const isLocal = process.env.LOCAL === 'true';
export const loginUrl = process.env.LOGIN_URL ?? '';
export const virkailijaUrl = process.env.VIRKAILIJA_URL ?? '';
export const backendUrl = process.env.VIESTINTAPALVELU_URL ?? '';
export const apiUrl = `${backendUrl}/raportointi/v1`;
export const cookieName = process.env.COOKIE_NAME ?? 'JSESSIONID';
