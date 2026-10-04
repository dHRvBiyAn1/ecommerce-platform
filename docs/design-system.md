# Frontend design system

The frontend brand is **Étoile**. The live implementation is the source of
truth: [Tailwind configuration](../frontend/ecommerce-app/tailwind.config.js)
and [global styles](../frontend/ecommerce-app/src/styles/globals.css). This
document records their current design direction; it does not prescribe a new UI.

## Foundations

- **Typography:** Rubik for display headings and Nunito Sans for body text;
  JetBrains Mono is the configured monospace stack. Tailwind exposes these as
  `font-display`, `font-sans`, and `font-mono`.
- **Light palette:** editorial black `#18181B`, foreground `#09090B`, near-white
  background `#FAFAFA`, and pink accent `#EC4899`. Surfaces are white; borders,
  muted text, and secondary colors come from semantic HSL variables in
  `globals.css`.
- **Dark mode:** `.dark` overrides semantic background, foreground, surface,
  border, and muted tokens while retaining the pink accent. Components should
  use semantic Tailwind colors such as `bg-background`, `text-foreground`,
  `bg-primary`, and `bg-accent` rather than hard-coded light-theme colors.
- **Shape and layout:** the shared radius is `0.5rem`; the centered container
  uses `1.5rem` horizontal padding and a `1440px` 2xl breakpoint. Prefer the
  existing Tailwind utilities and component primitives.
- **Motion and access:** preserve visible keyboard focus, sufficient contrast,
  responsive layouts, and the existing `prefers-reduced-motion` override.
  Avoid layout-shifting hover effects and emoji icons.

Update the CSS variables and Tailwind configuration together when changing
shared tokens. Keep Étoile branding and verify both light and dark themes without
adding generated page-specific specifications.
