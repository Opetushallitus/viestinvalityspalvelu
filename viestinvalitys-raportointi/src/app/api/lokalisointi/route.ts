import { fetchLokalisaatiot } from "../../lib/data";

export async function getKaannokset(lng: string) {
  const data = await fetchLokalisaatiot(lng);
  const translations: Record<string, string> = {};
  for (const translation of data) {
    translations[translation.key] = translation.value;
  }
  return translations;
}

export async function GET(request: Request) {
    const { searchParams } = new URL(request.url);
    const lng = searchParams.get('lng') || 'fi';
    const translations: Record<string, string> = await getKaannokset(lng);
    return Response.json(translations);
  }
  