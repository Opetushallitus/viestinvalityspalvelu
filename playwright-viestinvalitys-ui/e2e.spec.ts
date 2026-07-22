import { test, expect, APIRequestContext, Page } from "@playwright/test";

const lahetysApiHost = process.env.LAHETYS_API_HOST ?? "http://localhost:8080";

const uiHost = process.env.FRONTEND_HOST ?? "http://localhost:3000";
const uiBasePath = "/viestinvalityspalvelu";

const casUserLabel =
  process.env.CAS_USER_LABEL ?? "Paula Pääkäyttäjä (OPH pääkäyttäjä)";

test("Sent viesti is visible in the new raportointi UI", async ({
  request,
  page,
}) => {
  const uniqueId = Math.random().toString(36).substring(7);
  const lahetysOtsikko = `Lähetys ${uniqueId}`;
  const viestiOtsikko = `Viesti ${uniqueId}`;

  await loginToLahetysApiWithLocalRaportointiLogin(request);
  await sendViesti(request, lahetysOtsikko, viestiOtsikko);

  await loginToUiWithLocalKeycloakCas(page);
  await page.goto(`${uiHost}${uiBasePath}/`);
  await page.getByText(lahetysOtsikko).click();
  await expect(page.getByText(viestiOtsikko)).toBeVisible();
});

// FIXME: remove this (probably needs moving lahetys lambda functionality to viestinvalitys-service)
async function loginToLahetysApiWithLocalRaportointiLogin(
  request: APIRequestContext,
) {
  const loginResponse = await request.post(`${lahetysApiHost}/login`, {
    form: {
      username: "user",
      password: "password",
    },
  });
  expect(loginResponse.ok()).toBe(true);
}

async function loginToUiWithLocalKeycloakCas(page: Page) {
  await page.goto(`${uiHost}${uiBasePath}/login`);
  await page.getByRole("button", { name: casUserLabel }).click();
  await page.waitForURL(
    (url) =>
      url.host === new URL(uiHost).host &&
      url.pathname.startsWith(uiBasePath) &&
      !url.pathname.includes("/login"),
  );
}

async function sendViesti(
  request: APIRequestContext,
  lahetysOtsikko: string,
  viestiOtsikko: string,
) {
  const apiUrl = `${lahetysApiHost}/lahetys/v1`;

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
