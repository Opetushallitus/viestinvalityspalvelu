'use server'

export async function retrySend(formData: FormData) {
 
    const rawFormData = {
      lahetystunniste: formData.get('lahetysTunniste'),
      vastaanottajaTunniste: formData.get('vastaanottajaTunniste'),
    }
    // param validation
    // api call
    // revalidate cache
  }