import { parseHTML } from 'k6/html';
import http from 'k6/http';
import { sleep } from 'k6';

export const options = {
  // A number specifying the number of VUs to run concurrently.
  // vus: 1,
  // A string specifying the total duration of the test run.
  // duration: '10s',

  stages: [
    { duration: '2m', target: 40 },
    { duration: '3m', target: 40 },
    { duration: '1m', target: 0 }
  ]
};

function login() {
  const healthcheckResponse = http.get(`https://viestinvalitys.${__ENV.VIESTINVALITYS_ENVIRONMENT}opintopolku.fi/lahetys/v1/healthcheck`, {redirects: 0});
  const loginUrl = healthcheckResponse.headers.Location;
  const loginPageResponse = http.get(loginUrl);

  const loginPageDoc = parseHTML(loginPageResponse.body);
  const executionParam = loginPageDoc.find('input[name=execution]').attr('value')

  const loginPayload = {
    username: 'viestinvalitys-kuormatestaus',
    password: `${__ENV.VIESTINVALITYS_PASSWORD}`,
    execution: executionParam,
    _eventId: 'submit',
    geolocation: '',
  };
  const loginResponse = http.post(`https://virkailija.${__ENV.VIESTINVALITYS_ENVIRONMENT}opintopolku.fi/cas/login?service=https%3A%2F%2Fviestinvalitys.${__ENV.VIESTINVALITYS_ENVIRONMENT}opintopolku.fi%2Fj_spring_cas_security_check`, loginPayload);
  if (loginResponse.status == 500) {
    console.log(loginResponse);
  }

  return {cookies: http.cookieJar().cookiesForURL(`https://viestinvalitys.${__ENV.VIESTINVALITYS_ENVIRONMENT}opintopolku.fi`)};
}

export function setup() {
  return login();
}

function getViestiPayload(korkea, liite) {
  return '{\n' +
      '  "otsikko": "Onnistunut otsikko",\n' +
      '  "sisalto": "Syvällinen sisältö",\n' +
      '  "sisallonTyyppi": "text",\n' +
      '  "kielet": [\n' +
      '    "fi",\n' +
      '    "sv"\n' +
      '  ],\n' +
      '  "lahettavanVirkailijanOid": "1.2.246.562.00.00000000000000006666",\n' +
      '  "lahettaja": {\n' +
      '    "nimi": "Opintopolku",\n' +
      '    "sahkopostiOsoite": "noreply@opintopolku.fi"\n' +
      '  },\n' +
      '  "vastaanottajat": [\n' +
      '    {\n' +
      '      "nimi": "Vallu Vastaanottaja",\n' +
      '      "sahkopostiOsoite": "santeri.korri+success@knowit.fi"\n' +
      '    }\n' +
      '  ],\n' +
      (liite ?
            '  "liitteidenTunnisteet": [\n' +
            '    "3fa85f64-5717-4562-b3fc-2c963f66afa6"\n' +
            '  ],\n' : '') +
      '  "lahettavaPalvelu": "hakemuspalvelu",\n' +
      '  "lahetysTunniste": "3fa85f64-5717-4562-b3fc-2c963f66afa6",\n' +
      '  "prioriteetti": "' + (korkea ? 'korkea' : 'normaali') + '",\n' +
      '  "sailytysAika": 365,\n' +
      '  "kayttooikeusRajoitukset": [\n' +
      '    "APP_ATARU_HAKEMUS_CRUD_1.2.246.562.00.00000000000000006666"\n' +
      '  ],\n' +
      '  "metadata": {\n' +
      '    "key": "value"\n' +
      '  }\n' +
      '}';
}

const viestiParams = {
  headers: {
    "Content-Type": 'application/json',
    "accept": 'application/json',
  },
  redirects: 0,
}

export default function(data) {
  let jar = http.cookieJar();
  Object.keys(data.cookies).forEach(key => {
    jar.set(`https://viestinvalitys.${__ENV.VIESTINVALITYS_ENVIRONMENT}opintopolku.fi`, key, data.cookies[key][0]);
  });

  for(var i=0;i<60;i++) {
    const viestiResponse = http.post(`https://viestinvalitys.${__ENV.VIESTINVALITYS_ENVIRONMENT}opintopolku.fi/lahetys/v1/viestit`, getViestiPayload(Math.random()<0.2, Math.random()<0.1), viestiParams);
    if(viestiResponse.status!=200) {
      console.log(viestiResponse);
    }
    sleep(Math.random()*0.1);
  }
}
