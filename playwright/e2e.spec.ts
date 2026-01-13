import {
  test,
  expect,
  APIRequestContext,
  BrowserContext,
} from "@playwright/test";

const backendHost = "http://localhost:8080";
const frontendHost = "http://localhost:3000";

test("Sent viesti is visible in raportti", async ({
  request,
  page,
  context,
}) => {
  const uniqueId = Math.random().toString(36).substring(7);
  const lahetysOtsikko = `Lähetys ${uniqueId}`;
  const viestiOtsikko = `Viesti ${uniqueId}`;

  await login(request, context);
  await sendViesti(request, lahetysOtsikko, viestiOtsikko);

  await page.goto(`${frontendHost}/raportointi`);
  await page.getByText(lahetysOtsikko).click();
  await expect(page.getByText(viestiOtsikko)).toBeVisible();
});

async function login(request: APIRequestContext, context: BrowserContext) {
  const loginResponse = await request.post(`${backendHost}/login`, {
    form: {
      username: "user",
      password: "password",
    },
  });
  expect(loginResponse.ok()).toBe(true);

  const storageState = await request.storageState();
  await context.addCookies(storageState.cookies);
}

async function sendViesti(
  request: APIRequestContext,
  lahetysOtsikko: string,
  viestiOtsikko: string,
) {
  const apiUrl = `${backendHost}/lahetys/v1`;

  const lahetysResponse = await request.post(`${apiUrl}/lahetykset`, {
    data: {
      otsikko: lahetysOtsikko,
      lahettavaPalvelu: "e2e-test",
      lahettaja: {
        nimi: "E2E Tester",
        sahkopostiOsoite: "noreply@opintopolku.fi",
      },
      prioriteetti: "normaali",
      sailytysaika: 10,
    },
  });

  expect(lahetysResponse.ok()).toBe(true);
  const lahetysTunniste = (await lahetysResponse.json()).lahetysTunniste;
  expect(lahetysTunniste).toBeDefined();

  const viestiResponse = await request.post(`${apiUrl}/viestit`, {
    data: {
      otsikko: viestiOtsikko,
      sisalto: "Tämä on E2E-testiviesti.",
      sisallonTyyppi: "text",
      vastaanottajat: [
        {
          nimi: "Vastaan Ottaja",
          sahkopostiOsoite: "vastaanottaja@example.com",
        },
      ],
      lahetysTunniste: lahetysTunniste,
      kayttooikeusRajoitukset: [
        {
          oikeus: "APP_OIKEUS",
          organisaatio: "1.2.246.562.10.240484683010",
        },
      ],
    },
  });

  expect(viestiResponse.ok()).toBe(true);
  const viestiTunniste = (await viestiResponse.json()).viestiTunniste;
  expect(viestiTunniste).toBeDefined();
}
