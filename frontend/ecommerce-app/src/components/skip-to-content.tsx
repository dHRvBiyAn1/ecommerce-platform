import * as React from "react";

/**
 * "Skip to main content" link. Hidden until focused; required for keyboard
 * users to bypass the long top navigation.
 */
export const SkipToContent: React.FC = () => (
  <a
    href="#main-content"
    className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[100] focus:rounded-md focus:bg-primary focus:px-4 focus:py-2 focus:text-sm focus:font-medium focus:text-primary-foreground focus:shadow-lg focus:ring-2 focus:ring-ring"
  >
    Skip to main content
  </a>
);
