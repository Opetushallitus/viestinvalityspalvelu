// TODO validointi puuttuville konffeille jos ei dev-moodissa
export const loginUrl=process.env.LOGIN_URL || ''
export const backendUrl=process.env.VIESTINTAPALVELU_URL || ''
export const apiUrl = `${backendUrl}/raportointi/v1`
export const cookieName = process.env.COOKIE_NAME || 'JSESSIONID'