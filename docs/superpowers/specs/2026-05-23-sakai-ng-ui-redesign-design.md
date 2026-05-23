# Glacier Commerce — Sakai-NG UI Redesign

## Motivation

Adopt the Sakai-NG (PrimeNG admin template) design language across all Glacier Commerce pages. Both projects share Angular 21, PrimeNG 21, Tailwind CSS v4, and the Aura theme preset, making the design transfer seamless.

## Scope

All 15 pages + layout shell redesigned to match Sakai-NG's visual language. No business logic changes — all API calls, guards, interceptors, services, and auth logic remain untouched.

## Architecture

### Layout Shell

```
AppLayout (wrapper)
├── AppTopbar       — hamburger + logo + floating configurator trigger + dark toggle + user profile
├── AppSidebar      — outside-click dismiss, closes on navigation
│   └── AppMenu     — iterates MenuItem[] model
│       └── AppMenuitem  — recursive submenu component with animation
├── <router-outlet> — page content
├── AppFooter       — "Glacier Commerce by PrimeNG"
└── AppConfigurator — primary/surface color picker, preset switcher, menu mode
```

### Standalone Pages (outside AppLayout)

Login, Register — use `AppFloatingConfigurator` (dark mode + palette buttons, fixed top-8 right-8).

### Menu Modes

- **Static**: sidebar always visible, content shifts by sidebar width
- **Overlay**: sidebar slides over content when toggled

Configurable from the configurator panel.

### Layout Service

The existing `LayoutService` is extended with Sakai-NG config fields — no breaking changes.

```ts
interface LayoutConfig {
  preset: string;     // 'Aura' | 'Lara' | 'Nora'
  primary: string;    // 'emerald' | 'green' | ... | 'noir'
  surface: string;    // 'slate' | 'gray' | ... | 'ocean'
  darkTheme: boolean;
  menuMode: string;   // 'static' | 'overlay'
}
```

## Navigation & Menu

### Menu Model (role-based)

```
Glacier Commerce
├── Home              — Dashboard          [/]
├── Shop              — Products           [/products]
│                     — My Account         [/account]
├── Seller            — Dashboard          [/seller]
│                     — Inventory          [/seller/inventory]
│                     — Add Product        [/seller/add-product]
├── Admin             — Dashboard          [/admin/dashboard]
│                     — User Management    [/admin/users]
```

- Menu items are built in `AppMenu.ngOnInit()` based on role from `authService.user()?.role`
- Root labels ("Home", "Shop", "Seller", "Admin") render as `.layout-menuitem-root-text`
- Nested items use recursive `AppMenuitem` component with animated expand/collapse
- Active path tracked in `layoutService.layoutState().activePath`

### Configurator Panel

| Control | Options |
|---------|---------|
| Primary color | 16 swatches (emerald, green, lime, orange, amber, yellow, teal, cyan, sky, blue, indigo, violet, purple, fuchsia, pink, rose, noir) |
| Surface palette | 8 swatches (slate, gray, zinc, neutral, stone, soho, viva, ocean) |
| Preset | Aura / Lara / Nora (p-selectbutton) |
| Menu mode | Static / Overlay (p-selectbutton) |

## Theme System

### Runtime Theme Switching

Uses `@primeuix/themes` APIs:
- `updatePreset(ext)` — applies primary color overrides via semantic palette
- `updateSurfacePalette(palette)` — swaps surface color scales at runtime
- `$t().preset(p).preset(ext).surfacePalette(palette).use({ useDefaultOptions: true })` — full preset + color change

### Dark Mode

- Toggles `.app-dark` class on `<html>` matching `darkModeSelector: '.app-dark'` in PrimeNG config
- Uses `document.startViewTransition()` when available, falls back to class toggle
- Preference saved to `localStorage`

### Default Config

```ts
{
  preset: 'Aura',
  primary: 'emerald',
  surface: null,     // preset default
  darkTheme: true,   // matches current app default
  menuMode: 'overlay'
}
```

## Pages

### Pages Inside AppLayout

#### Seller Dashboard
- Metric cards using Sakai-NG `StatsWidget` pattern (icon + value + label + trend indicator)
- Bar chart via PrimeNG Chart (Chart.js) — stays as-is

#### Inventory Management
- PrimeNG table with striped rows, paginator, search input
- Inline edit for price/stock via cell templates

#### Add/Edit Product
- Form sections in Sakai-NG styled p-card
- Dynamic category p-select from API
- 2-column grid layout with Sakai-NG spacing/typography

#### Admin Dashboard
- Same widget pattern as seller dashboard
- Line chart for revenue

#### Admin Users
- PrimeNG table with role badges (severity-tagged)

#### My Account
- Profile card, contact/billing info card
- Order history table

#### Shopping Cart
- Cart items in styled cards with quantity controls
- Order summary sidebar card

#### Checkout
- Shipping and payment forms in p-card sections

#### Order Success
- Confirmation card with checkmark and order details

#### Home
- Hero section with Sakai-NG typography scale and button styles
- Featured product card, trending grid with Sakai-NG card styling

### Standalone Pages

#### Login / Register
- Centered card layout with logo
- Styled inputs matching Sakai-NG auth page pattern
- Floating configurator visible for theme switching
- Buyer/Seller toggle as p-selectbutton (register)

#### Product Listing
- Filter sidebar + product grid
- Product cards with Sakai-NG card/button styling

#### Product Details
- Image gallery + details
- Description card, add-to-cart button, quantity selector

#### Not Found
- Centered 404 with large icon and navigation link

## Implementation Order

1. **Layout shell** — `LayoutService`, `AppLayout`, `AppTopbar`, `AppSidebar`, `AppMenu`, `AppMenuitem`, `AppFooter`, `AppConfigurator`, `AppFloatingConfigurator`
2. **Theme system** — config wiring, dark mode, preset/color integration
3. **Standalone pages** — Login, Register, Product Listing, Product Details, Not Found
4. **Admin/Seller pages** — Seller Dashboard, Inventory, Add/Edit Product, Admin Dashboard, Admin Users
5. **Customer pages** — Home, My Account, Cart, Checkout, Order Success
6. **Routes** — add missing `edit-product` route
7. **Cleanup** — remove cloned Sakai-NG repo, verify `ng build` passes

## What Stays the Same

- All services (`ApiService`, `AuthService`)
- All guards and interceptors
- All API call logic
- Auth flow (JWT, token storage, login/register)
- Cart state management
- Route structure (paths, guards, roles)
