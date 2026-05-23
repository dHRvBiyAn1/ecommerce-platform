# PrimeNG Migration & Sakai Admin Template Design

**Date:** 2026-05-23
**Status:** Approved

## Overview

Replace the custom Zard UI component library with PrimeNG and adopt the Sakai admin template layout across all pages of the e-commerce platform.

## Goals

1. Replace Zard UI (~45 custom components) with PrimeNG ecosystem
2. Adopt Sakai's layout (topbar + sidebar + footer) for ALL pages
3. Role-based sidebar: customer sees store links, seller sees seller links, admin sees admin links
4. Use PrimeNG MCP server (`@primeng/mcp`) for AI-assisted component knowledge
5. Remove all Zard components, ng-icons/lucide, ngx-sonner, and related dependencies

## PrimeNG MCP Server Setup

The MCP server `@primeng/mcp` provides AI assistants with comprehensive access to PrimeNG component documentation, props, events, templates, theming, and code examples.

```json
{
  "mcpServers": {
    "primeng": {
      "command": "npx",
      "args": ["-y", "@primeng/mcp"]
    }
  }
}
```

## Architecture

### Dependencies (replacements)

| Removed | Added |
|---|---|
| `zard-ui` (local `shared/components/`) | `primeng ^21.0.2` |
| class-variance-authority | `@primeuix/themes ^2.0.0` |
| clsx | `primeicons ^7.0.0` |
| tailwind-merge | `tailwindcss-primeui ^0.6.1` |
| ngx-sonner | (PrimeNG `<p-toast>` + `MessageService`) |
| @ng-icons/core + @ng-icons/lucide | (PrimeIcons `<i class="pi pi-...">`) |
| embla-carousel* | (removed, not used in PrimeNG approach) |

### PrimeNG Configuration (`app.config.ts`)

```typescript
import Aura from '@primeuix/themes/aura';
import { providePrimeNG } from 'primeng/config';

providers: [
  providePrimeNG({
    theme: {
      preset: Aura,
      options: { darkModeSelector: '.app-dark' }
    }
  })
]
```

### Sakai-Inspired Layout Architecture

```
app-layout (component: wraps ALL routes)
├── app-topbar
│   ├── Logo + app name
│   ├── Hamburger (toggles sidebar)
│   ├── Nav links (Products, etc.)
│   ├── Cart badge (p-badge on p-button)
│   └── User dropdown (p-menu) with Profile / Logout
├── app-sidebar
│   ├── Menu items filtered by role
│   │   ├── Store / Home (all roles)
│   │   ├── Products (all roles)
│   │   ├── My Account (authenticated)
│   │   ├── Seller Dashboard (seller, admin)
│   │   ├── Inventory (seller, admin)
│   │   └── Admin Panel (admin only)
│   └── Collapsible via menuMode toggle
├── router-outlet (page content)
└── app-footer
```

### Menu Modes

- **Public pages** (Home, Products, Cart, Login, etc.): overlay mode — sidebar hidden by default, accessible via hamburger
- **Admin/Seller pages**: static mode — sidebar always visible (256px fixed width)
- Toggle between modes based on route (public vs admin/seller)

### Route Structure

| Path | Component | Auth | Role | Sidebar Mode |
|---|---|---|---|---|
| `/` | Home | - | - | overlay |
| `/login` | Login | - | - | overlay |
| `/register` | Register | - | - | overlay |
| `/products` | ProductListing | - | - | overlay |
| `/product?id=` | ProductDetails | - | - | overlay |
| `/cart` | ShoppingCart | - | - | overlay |
| `/checkout` | Checkout | authGuard | - | overlay |
| `/order-success` | OrderSuccess | authGuard | - | overlay |
| `/account` | MyAccount | authGuard | - | overlay |
| `/seller` | SellerDashboard | authGuard | ROLE_SELLER | static |
| `/seller/inventory` | InventoryManagement | authGuard | ROLE_SELLER | static |
| `/seller/add-product` | AddProduct | authGuard | ROLE_SELLER | static |
| `/admin/dashboard` | AdminDashboard | authGuard | ROLE_ADMIN | static |
| `/admin/users` | AdminUsers | authGuard | ROLE_ADMIN | static |
| `/not-found` | NotFound | - | - | overlay |

### Component Mapping

| Zard UI | PrimeNG |
|---|---|
| `z-button` | `<p-button>` |
| `z-input` | `<input pInputText>` |
| `z-badge` | `<p-badge>` |
| `z-card` | `<p-card>` |
| `z-table` / `z-table-header` / `z-table-body` / etc. | `<p-table>` |
| `z-dropdown-menu` / `z-dropdown-menu-item` | `<p-menu>` |
| `z-layout` / `z-header` / `z-content` / `z-footer` | Sakai custom layout |
| `z-toast` + `ngx-sonner` | `<p-toast>` + `MessageService` |
| `z-sidebar` | Sakai sidebar component |
| `<ng-icon name="lucide*">` | `<i class="pi pi-*">` |

### Toast System

Replace `ngx-sonner` `toast()` / `toast.error()` with PrimeNG's `<p-toast>` component and `MessageService`:

```typescript
import { MessageService } from 'primeng/api';

constructor(private messageService: MessageService) {}

// Show toast
this.messageService.add({ severity: 'error', summary: 'Unauthorized', detail: 'You do not have access' });

// In interceptor: inject MessageService instead of importing toast from ngx-sonner
```

### Admin Dashboard Page

Sakai-style admin dashboard at `/admin/dashboard`:
- **Stats cards**: Total Revenue, Orders, Customers, Products (`<p-card>` grid)
- **Recent Sales table**: `<p-table>` with sortable columns, paginator
- **Revenue chart**: `<p-chart>` with Chart.js (line chart)
- **Notification feed**: scrollable list

### Icon Migration

Replace all `<ng-icon name="lucide*">` with `<i class="pi pi-*">`.

| Lucide Icon | PrimeIcon |
|---|---|
| `lucideShoppingCart` | `pi pi-shopping-cart` |
| `lucideUser` | `pi pi-user` |
| `lucideSnowflake` | `pi pi-star` (or custom logo) |
| `lucidePackage` | `pi pi-box` |
| `lucideLogOut` | `pi pi-sign-out` |
| `lucideSearch` | `pi pi-search` |
| `lucideUsers` | `pi pi-users` |
| `lucideShield` | `pi pi-shield` |
| `lucideArrowRight` | `pi pi-arrow-right` |
| `lucideChevronRight` | `pi pi-chevron-right` |
| `lucideMail` | `pi pi-envelope` |

## Phased Implementation Plan

### Phase 1: Foundation
1. Set up PrimeNG MCP server config
2. Install PrimeNG, PrimeIcons, PrimeUI themes, tailwindcss-primeui
3. Update `angular.json` styles (PrimeNG theme CSS)
4. Configure `app.config.ts` with `providePrimeNG(Aura)`
5. Create Sakai layout components: `app-layout`, `app-topbar`, `app-sidebar`, `app-footer`, layout service
6. Implement menu model with role-based filtering
7. Add dark mode toggle via `.app-dark` class
8. Delete Zard `shared/components/` directory
9. Remove Zard dependencies from `package.json`
10. Remove `provideZard()` from app config
11. Update `app.html` to use `app-layout` wrapper

### Phase 2: Public Pages Migration
1. Home page
2. Login page
3. Register page
4. Product Listing page
5. Product Details page
6. Shopping Cart page
7. Checkout page
8. Order Success page
9. My Account page
10. NotFound page

### Phase 3: Admin/Seller Pages Migration
1. Seller Dashboard
2. Inventory Management
3. Add Product
4. Admin Dashboard (new Sakai-style page)
5. Admin Users

### Phase 4: Cleanup & Polish
1. Remove `@ng-icons/core`, `@ng-icons/lucide` from package.json
2. Remove `ngx-sonner` from package.json + imports
3. Remove `class-variance-authority`, `clsx`, `tailwind-merge`
4. Remove `embla-carousel-*` packages
5. Update `styles.css` — clean up Zard CSS variables
6. Update auth interceptor to use PrimeNG MessageService
7. Update role guard to use PrimeNG MessageService
8. Test all routes with role-based access
9. Verify build passes with zero errors

## Key Decisions

1. **Sakai layout for ALL pages** (not just admin) — sidebar collapses on public pages via overlay mode
2. **Phased migration** — install and set up first, then migrate page by page (safer, verifiable)
3. **PrimeIcons + `<i class="pi">`** over `<ng-icon>` — simpler, tree-shakable, no separate icon provider needed
4. **PrimeNG MessageService** replaces ngx-sonner for toast notifications
5. **Aura theme preset** — Sakai's default, clean modern look with dark mode support
6. **Tailwind remains** — PrimeNG works alongside Tailwind; we keep existing utility classes and add `tailwindcss-primeui` plugin for better integration

## Files to Modify

### New files (Sakai layout)
- `src/app/layout/layout.service.ts`
- `src/app/layout/app-layout.ts`
- `src/app/layout/app-topbar.ts`
- `src/app/layout/app-sidebar.ts`
- `src/app/layout/app-footer.ts`
- `src/app/pages/admin-dashboard/admin-dashboard.ts`
- `src/app/pages/admin-dashboard/admin-dashboard.html`

### Modified files
- `src/index.html` — update stylesheets
- `src/styles.css` — add PrimeNG theme, remove Zard vars
- `src/app/app.config.ts` — providePrimeNG
- `src/app/app.ts` — wrap with app-layout
- `src/app/app.routes.ts` — add admin dashboard route
- `src/app/interceptors/auth.interceptor.ts` — use MessageService
- `src/app/guards/role.guard.ts` — use MessageService
- `src/app/pages/*` — all 11+ pages (replace Zard with PrimeNG)
- `angular.json` — update styles, remove Zard config

### Deleted files
- `src/app/shared/` (entire directory ~150+ files)
- `src/app/layout/page-layout.ts` (replaced by Sakai app-layout)
- `src/app/layout/admin/admin-layout.ts` (replaced by Sakai app-layout)
