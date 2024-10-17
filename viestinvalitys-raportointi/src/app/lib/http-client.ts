import { redirect } from 'next/navigation';
import { apiUrl, cookieName, loginUrl } from './configurations';
import { FetchError } from "./error-handling";
import { cookies } from 'next/headers';

/* sovellettu valintojen-totettaminen repon vastaavasta */
const doFetch = async (request: Request) => {
    try {
      const response = await fetch(request);
      return response.status >= 400
        ? Promise.reject(new FetchError(response))
        : Promise.resolve(response);
    } catch (e) {
      return Promise.reject(e);
    }
  };
  
  const isUnauthenticated = (response: Response) => {
    return response?.status === 401;
  };
  
  const isRedirected = (response: Response) => {
    return response.redirected;
  };
  
  const makeBareRequest = (request: Request) => {
    request.headers.set('Caller-Id', '1.2.246.562.10.00000000001.viestinvalityspalvelu');
    request.headers.set('CSRF', '1.2.246.562.10.00000000001.viestinvalityspalvelu')  
    return doFetch(request);
  };
  
  const retryWithLogin = async (request: Request, loginUrl: string) => {
    await makeBareRequest(new Request(loginUrl));
    return makeBareRequest(request);
  };

  const responseToData = async (res: Response) => {
    if (res.status === 204) {
      return { data: {} };
    }
    try {
        const result = { data: await res.json() }; // toistaiseksi kaikki kutsuttavat apit palauttavat jsonia
        return result;
      } catch (e) {
        console.error('Parsing fetch response body as JSON failed!');
        return Promise.reject(e);
      }
  };

  export const makeRequest = async (url: string, options: RequestInit = {}) => {
    // autentikointicookie
    const sessionCookie = cookies().get(cookieName);
    if (sessionCookie === undefined) {
        redirect(loginUrl);
    }
    const cookieParam = sessionCookie.name + '=' + sessionCookie.value;
    const request = new Request(url, { method: 'GET', headers: { cookie: cookieParam ?? '' }, ...options })
    try {
      const response = await makeBareRequest(request);
      const responseUrl = new URL(response.url);
      if (
        isRedirected(response) &&
        responseUrl.pathname.startsWith('/cas/login')
      ) {
        redirect(loginUrl);
      }
      return responseToData(response);
    } catch (error: unknown) {
      console.error('Virhe tietojen haussa')
      if (error instanceof FetchError) {
        console.error(error.response.status)
        console.error(error.message)
        if (isUnauthenticated(error.response)) {
          try {
            if (request?.url?.includes(apiUrl)) {
                const resp = await retryWithLogin(request, loginUrl);
                return responseToData(resp);
              }
          } catch (e) {
            if (e instanceof FetchError && isUnauthenticated(e.response)) {
                redirect(loginUrl);
            }
            return Promise.reject(e);
          }
        } else if (
          isRedirected(error.response) &&
          error.response.url === request.url
        ) {
          const response = await makeBareRequest(request);
          return responseToData(response);
        }
      }
      return Promise.reject(error);
    }
  };

  
  
