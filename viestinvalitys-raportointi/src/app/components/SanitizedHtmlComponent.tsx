'use client';

import DOMPurify from 'dompurify';
import { useEffect, useState } from 'react';

export const SanitizedHtml = ({ html }: { html: string }) => {
  const [sanitizedHtml, setSanitizedHtml] = useState('');

  useEffect(() => {
    setSanitizedHtml(DOMPurify.sanitize(html));
  }, [html]);

  return (
    <div
      dangerouslySetInnerHTML={{
        __html: sanitizedHtml,
      }}
    />
  );
};
