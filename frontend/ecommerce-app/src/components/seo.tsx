import * as React from "react";

interface SeoProps {
  title?: string;
  description?: string;
  ogImage?: string;
  noindex?: boolean;
}

const BRAND = import.meta.env.VITE_BRAND_NAME ?? "Étoile";
const TAGLINE =
  import.meta.env.VITE_BRAND_TAGLINE ?? "A modern marketplace for considered things.";

/**
 * Tiny SEO helper. Sets document title + meta description + OG tags
 * imperatively per route. Avoids react-helmet to keep the bundle slim.
 */
export const Seo: React.FC<SeoProps> = ({ title, description, ogImage, noindex }) => {
  React.useEffect(() => {
    const originalTitle = document.title;
    const fullTitle = title ? `${title} · ${BRAND}` : `${BRAND} — ${TAGLINE}`;
    document.title = fullTitle;

    setMeta("description", description ?? TAGLINE);
    setProperty("og:title", fullTitle);
    setProperty("og:description", description ?? TAGLINE);
    setProperty("og:type", "website");
    setProperty("og:site_name", BRAND);
    if (ogImage) setProperty("og:image", ogImage);
    setMeta("twitter:card", ogImage ? "summary_large_image" : "summary");
    setMeta("twitter:title", fullTitle);
    setMeta("twitter:description", description ?? TAGLINE);

    setMeta("robots", noindex ? "noindex,nofollow" : "index,follow");

    return () => {
      document.title = originalTitle;
    };
  }, [title, description, ogImage, noindex]);

  return null;
};

function setMeta(name: string, content: string) {
  let el = document.head.querySelector<HTMLMetaElement>(`meta[name='${name}']`);
  if (!el) {
    el = document.createElement("meta");
    el.setAttribute("name", name);
    document.head.appendChild(el);
  }
  el.setAttribute("content", content);
}

function setProperty(property: string, content: string) {
  let el = document.head.querySelector<HTMLMetaElement>(`meta[property='${property}']`);
  if (!el) {
    el = document.createElement("meta");
    el.setAttribute("property", property);
    document.head.appendChild(el);
  }
  el.setAttribute("content", content);
}
