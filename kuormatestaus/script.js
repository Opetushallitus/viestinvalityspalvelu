import { parseHTML } from 'k6/html';
import http from 'k6/http';
import { sleep } from 'k6';
import { crypto } from 'k6/experimental/webcrypto';
import { SharedArray } from 'k6/data';
import { scenario } from 'k6/execution';

export const options = {
  // A number specifying the number of VUs to run concurrently.
  // vus: 1,
  // A string specifying the total duration of the test run.
  // duration: '10s',

  stages: [
    { duration: '2m', target: 50 },
    { duration: '3m', target: 50 },
    { duration: '1m', target: 0 }
  ]
};

function login() {
  const loginPageResponse = http.get(`https://viestinvalitys.${__ENV.VIESTINVALITYS_ENVIRONMENT}opintopolku.fi/lahetys/login`);
  const loginPageDoc = parseHTML(loginPageResponse.body);
  const executionParam = loginPageDoc.find('input[name=execution]').attr('value')

  const loginPayload = {
    username: 'viestinvalitys-kuormatestaus',
    password: `${__ENV.VIESTINVALITYS_PASSWORD}`,
    execution: executionParam,
    _eventId: 'submit',
    geolocation: '',
  };
  const loginResponse = http.post(`https://virkailija.${__ENV.VIESTINVALITYS_ENVIRONMENT}opintopolku.fi/cas/login?service=https%3A%2F%2Fviestinvalitys.${__ENV.VIESTINVALITYS_ENVIRONMENT}opintopolku.fi%2Flahetys%2Flogin%2Fj_spring_cas_security_check`, loginPayload);
  if (loginResponse.status == 500) {
    console.log(loginResponse);
  }

  return {cookies: http.cookieJar().cookiesForURL(`https://viestinvalitys.${__ENV.VIESTINVALITYS_ENVIRONMENT}opintopolku.fi/lahetys`)};
}

const testdata = new SharedArray('data', function () {
  return JSON.parse(open('./testdata.json'));
});

export function setup() {
  return login();
}

function getViestiPayload(korkea, liite, testdataItem) {
  return '{\n' +
      '  "otsikko": "' + testdataItem.otsikko + '",\n' +
      '  "sisalto": "' + testdataItem.sisalto + '",\n' +
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
      '      "nimi": "' + testdataItem.etunimi + ' ' + testdataItem.sukunimi + '",\n' +
      '      "sahkopostiOsoite": "' + testdataItem.etunimi.toLowerCase() + '.' + testdataItem.sukunimi.toLowerCase() + '+success@oph.fi"\n' +
      '    }\n' +
      '  ],\n' +
      (liite ?
            '  "liitteidenTunnisteet": [\n' +
            '    "3fa85f64-5717-4562-b3fc-2c963f66afa6"\n' +
            '  ],\n' : '') +
      '  "lahettavaPalvelu": "hakemuspalvelu",\n' +
      '  "idempotencyKey": "' + crypto.randomUUID() + '",\n' +
      '  "lahetysTunniste": "",\n' +
      '  "prioriteetti": "' + (korkea ? 'korkea' : 'normaali') + '",\n' +
      '  "sailytysaika": 365,\n' +
      '  "kayttooikeusRajoitukset": [{\n' +
      '    "oikeus": "APP_ATARU_HAKEMUS_CRUD",\n' +
      '    "organisaatio": "1.2.246.562.00.000000000000000066' + Math.floor(Math.random()*200) + '"\n' +
      '  }],\n' +
      '  "metadata": {\n' +
      '    "key": ["value"]\n' +
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

  const viestiResponse = http.post(`https://viestinvalitys.${__ENV.VIESTINVALITYS_ENVIRONMENT}opintopolku.fi/lahetys/v1/viestit?disableRateLimiter=true`, getViestiPayload(Math.random()<0.25, Math.random()<0.1, testdata[scenario.iterationInTest]), viestiParams);
  if(viestiResponse.status!=200) {
    console.log(viestiResponse);
  }

  sleep(Math.random()*0.1);
}
