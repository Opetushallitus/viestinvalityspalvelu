import { test, expect, Page } from "@playwright/test";

const uiHost = process.env.FRONTEND_HOST ?? "http://localhost:3000";
const uiBasePath = "/viestinvalityspalvelu";

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

test("Viesti content opens in a dialog from the vastaanottajat table", async ({
  page,
}) => {
  const uniqueId = Math.random().toString(36).substring(7);
  const lahetysOtsikko = `Lähetys ${uniqueId}`;

  await loginToUiWithLocalKeycloakCas(page);
  // Two viestit so the lähetys is not rendered as a massaviesti and the
  // vastaanottajat table shows the per-viesti "Näytä viesti" action.
  const lahetysTunniste = await createLahetys(page, lahetysOtsikko);
  await createViesti(
    page,
    lahetysTunniste,
    `Eka ${uniqueId}`,
    `Eka sisältö ${uniqueId}`,
  );
  await createViesti(
    page,
    lahetysTunniste,
    `Toka ${uniqueId}`,
    `Toka sisältö ${uniqueId}`,
  );

  await page.goto(`${uiHost}${uiBasePath}/`);
  await page.getByText(lahetysOtsikko).click();
  await page.getByRole("button", { name: "Näytä viesti" }).first().click();
  await expect(page.getByRole("dialog")).toContainText(`sisältö ${uniqueId}`);
  await page.getByRole("button", { name: "Sulje" }).click();
  await expect(page.getByRole("dialog")).toBeHidden();
});

async function loginToUiWithLocalKeycloakCas(page: Page) {
  await page.goto(`${uiHost}${uiBasePath}/`);
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
  const lahetysTunniste = await createLahetys(page, lahetysOtsikko);
  await createViesti(
    page,
    lahetysTunniste,
    viestiOtsikko,
    "Tämä on E2E-testiviesti.",
  );
}

async function createLahetys(page: Page, otsikko: string): Promise<string> {
  // page.request shares cookies (incl. the CAS JSESSIONID) with the authenticated page.
  const lahetysResponse = await page.request.post(`${apiUrl}/lahetykset`, {
    data: {
      otsikko: otsikko,
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
  return lahetysTunniste;
}

async function createViesti(
  page: Page,
  lahetysTunniste: string,
  otsikko: string,
  sisalto: string,
) {
  const viestiResponse = await page.request.post(`${apiUrl}/viestit`, {
    data: {
      otsikko: otsikko,
      sisalto: sisalto,
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
