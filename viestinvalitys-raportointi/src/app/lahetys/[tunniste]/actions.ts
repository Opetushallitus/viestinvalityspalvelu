'use server';

export async function retrySend(formData: FormData) {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const rawFormData = {
    lahetystunniste: formData.get('lahetysTunniste'),
    vastaanottajaTunniste: formData.get('vastaanottajaTunniste'),
  };
  // param validation
  // api call
  // revalidate cache
}
