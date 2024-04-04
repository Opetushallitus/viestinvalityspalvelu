'use client';

import DOMPurify from 'dompurify';

export default function HtmlMessage(message: string) {
  return <div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(message) }} />;
}
