import { fetchLokalisaatiot } from "../../lib/data";

async function getTranslations(lng: string) {
  const data = await fetchLokalisaatiot(lng);
  const translations: Record<string, string> = {};
  console.log(data)
  for (const translation of data) {
    translations[translation.key] = translation.value;
  }
  console.log(translations)
  return translations;
}

export async function GET(request: Request) {
    const { searchParams } = new URL(request.url);
    const lng = searchParams.get('lng') || 'fi';
    const translations: Record<string, string> = await getTranslations(lng);
    return Response.json(translations);
  }
  