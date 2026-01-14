'use client';

import DOMPurify from 'dompurify';

export const SanitizedHtml = ({ html }: { html: string }) => {
  return (
    <div
      dangerouslySetInnerHTML={{
        __html: DOMPurify.sanitize(html),
      }}
    />
  );
};
