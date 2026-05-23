# Responsive UI Design — Glacier Commerce

## Overview

Comprehensive responsive audit and fix for all 16 pages + layout shell of the Glacier Commerce Angular 21 / PrimeNG / Tailwind CSS frontend. 26 issues identified across critical, high, medium, and low severity.

## Layout Shell

### app-layout.ts
- Main padding: `p-4 md:p-8` (responsive padding instead of fixed 2rem)
- Animation: align sidebar and main container to both use `transform` for consistent animation

### app-topbar.ts
- Padding: `px-3 md:px-8`
- On `< sm`: collapse "Sign In" + "Get Started" text buttons to icon-only buttons to prevent overflow
- Ensure cart badge, dark mode toggle, and user menu all fit without wrapping

### app-sidebar.ts
- Height: use `100dvh` (dynamic viewport height) with `@supports` fallback for `100vh`
- Fix iOS Safari bottom gap issue

### app-footer.ts
- Footer links: add `flex-wrap justify-center sm:justify-start` to prevent overflow on small screens

## Critical Fixes

### admin-users.html
- Remove `min-width: 60rem` from table
- Use PrimeNG `responsiveLayout="stack"` for mobile (rows → stacked cards)
- Wrap in `overflow-x-auto` for tablet breakpoint

### inventory-management.html
- Change `overflow-hidden` to `overflow-x-auto` on table container
- Use PrimeNG `responsiveLayout="stack"` for mobile
- 7 columns (Product, SKU, Category, Price, Stock, Status, Actions) stack vertically on mobile

## High Priority Pages

### home.html
- Hero buttons: `flex-col sm:flex-row` with full-width on mobile
- Trending grid: `auto-rows-[200px] sm:auto-rows-[250px]`

### login.html / register.html
- Replace `min-h-[calc(100vh-264px)]` with `min-h-[calc(100dvh-264px)]` + CSS `@supports` fallback

### my-account.html
- Remove `pt-16` (layout already provides top margin); use `my-4 md:my-8`
- Address `max-w-xs` → `max-w-xs sm:max-w-md`
- Footer links: add `flex-wrap`

### checkout.html
- Order summary: remove `max-h-64` to prevent cart item clipping
- City/Zip and Expiry/CVV grids: `grid-cols-1 sm:grid-cols-2`

## Medium Priority Pages

### product-details.html
- Add `no-scrollbar` utility CSS to `styles.css`:
  ```css
  .no-scrollbar::-webkit-scrollbar { display: none; }
  .no-scrollbar { -ms-overflow-style: none; scrollbar-width: none; }
  ```

### product-listing.html
- Remove sticky positioning on mobile (`sticky top-24` only above `lg`)
- Fix radio button touch targets to 44px minimum

### register.html
- Account type selector: `grid-cols-1 sm:grid-cols-2`

### admin-dashboard.html
- Recent Sales table: add `overflow-x-auto` wrapper around p-table

### seller-dashboard.html
- Chart bars: ensure flex children don't conflict with absolute-positioned overlay elements

## Low Priority (fixed inline)

- Duplicate `class` attributes across product-listing, product-details, shopping-cart
- Standalone footer elements on checkout.html and order-success.html
- Animation desync in app-layout.ts (margin-left vs transform)
- Radio button touch targets in product-listing
