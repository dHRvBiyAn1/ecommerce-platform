# Angular Ecommerce Frontend Design

## Overview
Build an MVP Angular 21 standalone frontend for the ecommerce Spring Boot backend. Dark/light theme via CSS custom properties in a single parent file. Reusable shared components. Signals-based state management.

## Tech Stack
- Angular 21 (standalone components, signals, new control flow)
- Tailwind CSS 3 (darkMode: 'class')
- Angular Material (stepper, dialog, icons)
- TypeScript 5
- RxJS + Signals

## Backend Reference
- API Gateway: `http://localhost:8080`
- Google OAuth2 redirect: backend handles `/oauth2/authorization/google`, redirects to `http://localhost:4200/oauth2/redirect#token=`

## Folder Structure
```
frontend/ecommerce-app/src/
├── app/
│   ├── core/
│   │   ├── services/        # api.service, auth.service, cart.service, etc.
│   │   ├── interceptors/    # jwt.interceptor, error.interceptor
│   │   └── guards/          # auth.guard, guest.guard
│   ├── shared/
│   │   ├── components/      # AppButton, AppCard, ProductCard, etc.
│   │   ├── pipes/           # currency, discount, relative-time
│   │   └── directives/      # lazy-img, infinite-scroll, debounce
│   ├── layouts/
│   │   ├── main-layout/     # Header + Footer + RouterOutlet
│   │   └── auth-layout/     # Minimal centered card layout
│   ├── features/
│   │   ├── home/
│   │   ├── auth/            # login, register, forgot-password
│   │   ├── products/        # product-list, product-detail
│   │   ├── cart/
│   │   ├── checkout/        # multi-step (address → payment → review)
│   │   ├── orders/          # order-list, order-detail
│   │   └── profile/
│   ├── state/               # Signals-based stores
│   └── app.routes.ts
├── styles/
│   ├── _theme.scss          # ★ Parent: Material theme config
│   ├── _variables.scss      # ★ Parent: CSS custom properties for all tokens
│   └── styles.scss          # Global styles, Tailwind directives
├── assets/
├── environments/
└── index.html
```

## Theme Architecture (Parent-Driven)
Single source of truth: `styles/_variables.scss`

```scss
// _variables.scss — change ANY value here, it cascades everywhere
:root {
  --color-primary: #2563eb;
  --color-primary-hover: #1d4ed8;
  --color-secondary: #7c3aed;
  --color-surface: #ffffff;
  --color-surface-alt: #f8fafc;
  --color-text: #0f172a;
  --color-text-secondary: #64748b;
  --color-border: #e2e8f0;
  --color-success: #22c55e;
  --color-warning: #f59e0b;
  --color-error: #ef4444;
  --font-family: 'Inter', sans-serif;
  --font-size-sm: 0.875rem;
  --font-size-base: 1rem;
  --font-size-lg: 1.125rem;
  --font-size-xl: 1.25rem;
  --font-size-2xl: 1.5rem;
  --font-size-3xl: 2rem;
  --spacing-xs: 0.25rem;
  --spacing-sm: 0.5rem;
  --spacing-md: 1rem;
  --spacing-lg: 1.5rem;
  --spacing-xl: 2rem;
  --spacing-2xl: 3rem;
  --border-radius-sm: 0.25rem;
  --border-radius: 0.5rem;
  --border-radius-lg: 0.75rem;
  --border-radius-full: 9999px;
  --shadow-sm: 0 1px 2px rgba(0,0,0,0.05);
  --shadow: 0 1px 3px rgba(0,0,0,0.1);
  --shadow-lg: 0 10px 15px rgba(0,0,0,0.1);
}

[data-theme="dark"] {
  --color-primary: #60a5fa;
  --color-primary-hover: #93bbfd;
  --color-secondary: #a78bfa;
  --color-surface: #1e1e2e;
  --color-surface-alt: #2a2a3e;
  --color-text: #e2e8f0;
  --color-text-secondary: #94a3b8;
  --color-border: #334155;
  --color-success: #4ade80;
  --color-warning: #fbbf24;
  --color-error: #f87171;
}
```

Tailwind `tailwind.config.ts`: `darkMode: 'class'` with colors mapped to CSS variables.
ThemeService toggles `data-theme` attribute on `<html>` + persists to localStorage.

## Core Services
- **ApiService** — typed HTTP wrapper, base URL from env, error mapping
- **AuthService** — login, register, JWT management, `isAuthenticated` signal
- **CartService** — signal-based cart, localStorage persistence, quantity management
- **ProductService** — product CRUD, search, category filter, price filter
- **OrderService** — order CRUD, status tracking
- **ThemeService** — dark/light toggle, localStorage persistence
- **NotificationService** — toast notifications via shared AppToast component

## Interceptors
- **AuthInterceptor** — adds `Authorization: Bearer <token>` header
- **ErrorInterceptor** — 401 → refresh or logout, 4xx/5xx → toast

## Guards
- **AuthGuard** — redirects unauthenticated to `/auth/login`
- **GuestGuard** — redirects authenticated to `/`

## Shared Components
All styled via CSS variables — no hardcoded values.

| Component | Purpose | Inputs |
|-----------|---------|--------|
| AppButton | Button variants | `variant`, `size`, `loading`, `disabled` |
| AppCard | Container card | `variant`, slots for header/content/actions |
| AppModal | Dialog overlay | `open`, `title`, `size` |
| AppToast | Notification | `message`, `type`, `duration` |
| AppPagination | Page navigation | `currentPage`, `totalPages` |
| AppRating | Stars display | `rating`, `readonly`, `size` |
| AppSearchBar | Search field | `placeholder`, `value` |
| ProductCard | Product in grid/list | `product` |
| ProductGrid | Grid layout | `products`, `columns`, `viewMode` |
| Breadcrumb | Path navigation | `items: {label, url}[]` |
| SkeletonCard | Loading placeholder | (none) |
| EmptyState | Empty state display | `icon`, `title`, `message`, `action` |
| Badge | Status/count badge | `variant`, size |

## Feature Pages (MVP)
All routes lazy-loaded.

| Route | Component | Auth | Notes |
|-------|-----------|------|-------|
| `/` | HomePage | No | Hero, categories, featured |
| `/products` | ProductListPage | No | Filters, sort, grid/list, pagination |
| `/products/:id` | ProductDetailPage | No | Gallery, variants, add-to-cart |
| `/cart` | CartPage | No | Items, coupon, summary |
| `/checkout` | CheckoutPage | Yes | Stepper: address → payment → review |
| `/orders` | OrderListPage | Yes | Order table with status |
| `/orders/:id` | OrderDetailPage | Yes | Order summary, timeline |
| `/auth/login` | LoginPage | No (guest) | Email + Google OAuth |
| `/auth/register` | RegisterPage | No (guest) | Name/email/password, role |
| `/profile` | ProfilePage | Yes | Edit name/image |

## State Management
- **Auth state**: `AuthService` with signals (`currentUser`, `isAuthenticated`)
- **Cart state**: `CartService` with signal, syncs to localStorage, merges on login
- **UI state**: `ThemeService` (dark/light), `NotificationService` (toast queue)
- **API data**: Services return signals or observables; cached per-page (no global cache in MVP)

## Data Flow
```
Page Component → injects Service → calls API via ApiService
     ↓
Service updates signal
     ↓
Template renders reactively via signal()
```

## API Endpoints Used (MVP)
All proxied through API Gateway at `http://localhost:8080`

| Method | Path | Used By |
|--------|------|---------|
| POST | `/api/auth/register` | RegisterPage |
| POST | `/api/auth/token` | LoginPage |
| GET | `/api/user/profile` | ProfilePage |
| PUT | `/api/user/profile` | ProfilePage |
| GET | `/api/v1/products` | ProductListPage |
| GET | `/api/v1/products/search` | ProductListPage |
| GET | `/api/v1/products/:id` | ProductDetailPage |
| GET | `/api/v1/products/category/:categoryId` | ProductListPage |
| GET | `/api/v1/categories` | HomePage, ProductListPage |
| PATCH | `/api/v1/products/:id/stock` | (future seller) |
| POST | `/api/v1/orders` | CheckoutPage |
| GET | `/api/v1/orders` | OrderListPage |
| GET | `/api/v1/orders/:orderId` | OrderDetailPage |
| POST | `/api/v1/orders/:orderId/cancel` | OrderDetailPage |

## OAuth2 Flow
1. User clicks "Sign in with Google"
2. Redirect to `http://localhost:8080/oauth2/authorization/google`
3. Backend handles OAuth, redirects to `http://localhost:4200/oauth2/redirect#token=<jwt>`
4. Frontend parses token from URL hash, stores it, navigates to `/`
5. OAuthRedirect component handles this single route, no UI

## Performance
- Route-level lazy loading for all feature pages
- `trackBy` in all `@for` loops
- `OnPush` change detection on shared components
- Image lazy loading directive
- Skeleton loading states for all async data

## Out of Scope (MVP)
- SSR/Angular Universal
- PWA
- Admin/Vendor portals
- Multi-language
- Analytics
- Reviews/Q&A
- Wishlist
- Search autocomplete (basic search only)
