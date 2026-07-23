import { test, expect, Page } from "@playwright/test";

const uiHost = process.env.FRONTEND_HOST ?? "http://localhost:3000";
const uiBasePath = "/viestinvalityspalvelu";

// The create API is now served by viestinvalitys-service and reached through the UI dev-server
// proxy (:3000 -> :8081), i.e. the same origin as the UI. This lets the create calls reuse the
// CAS session cookie established by the browser login, so no separate lambda login is needed.
const apiUrl = `${uiHost}${uiBasePath}/v1`;

const casUserLabel =
  process.env.CAS_USER_LABEL ?? "Paula Pääkäyttäjä (OPH pääkäyttäjä)";

test("Sent viesti is visible in the new raportointi UI", async ({ page }) => {
  const uniqueId = Math.random().toString(36).substring(7);
  const lahetysOtsikko = `Lähetys ${uniqueId}`;
  const viestiOtsikko = `Viesti ${uniqueId}`;

  // One CAS login, reused for both the create calls and the UI.
  await loginToUiWithLocalKeycloakCas(page);
  await sendViesti(page, lahetysOtsikko, viestiOtsikko);

  await page.goto(`${uiHost}${uiBasePath}/`);
  await page.getByText(lahetysOtsikko).click();
  await expect(page.getByText(viestiOtsikko)).toBeVisible();
});

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
  page: Page,
  lahetysOtsikko: string,
  viestiOtsikko: string,
) {
  // page.request shares cookies (incl. the CAS JSESSIONID) with the authenticated page.
  const lahetysResponse = await page.request.post(`${apiUrl}/lahetykset`, {
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

  const viestiResponse = await page.request.post(`${apiUrl}/viestit`, {
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
