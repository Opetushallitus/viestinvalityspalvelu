import { useEffect, useState } from 'react';
import DOMPurify from 'dompurify';

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
