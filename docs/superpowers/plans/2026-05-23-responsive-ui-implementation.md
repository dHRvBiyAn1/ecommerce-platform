# Responsive UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make all 16 pages + layout shell fully responsive across mobile (320px+), tablet (768px+), and desktop (1024px+) breakpoints.

**Architecture:** Systematic page-by-page audit and fix using existing Tailwind CSS responsive utilities. Fix the layout shell first, then critical table overflow issues, then high/medium/low priority pages. Each file change is independent.

**Tech Stack:** Angular 21, PrimeNG 21, Tailwind CSS v4, Aura theme

---

### Task 1: Global CSS — Add dvh utility and no-scrollbar class

**Files:**
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Add dvh utility and no-scrollbar class**

Edit `frontend/src/styles.css` — add after the existing body rules and before `.app-dark`:

```css
.min-h-dvh {
  min-height: 100vh;
  min-height: 100dvh;
}
.no-scrollbar::-webkit-scrollbar {
  display: none;
}
.no-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
```

- [ ] **Step 2: Verify file is valid CSS**

Run: `npx tailwindcss --help` (just confirm tailwind is available)

---

### Task 2: Layout Shell — app-layout.ts responsive padding

**Files:**
- Modify: `frontend/src/app/layout/component/app-layout.ts`

- [ ] **Step 1: Add responsive padding media query to component styles**

In `app-layout.ts`, add inside the `styles: [ ... ]` array after the `.layout-main` rule (after line 56):

```css
@media (max-width: 480px) {
  .layout-main {
    padding: 1rem;
  }
}
```

- [ ] **Step 2: Verify build**

Run: `npx tsc --noEmit` or `ng build --configuration development` to check for errors

---

### Task 3: Layout Shell — app-topbar.ts responsive

**Files:**
- Modify: `frontend/src/app/layout/component/app-topbar.ts`

- [ ] **Step 1: Add responsive padding media query to component styles**

In `app-topbar.ts`, add inside the `styles: [ ... ]` array after the existing rules (after line 82):

```css
@media (max-width: 480px) {
  .layout-topbar {
    padding: 0 1rem;
  }
}
```

- [ ] **Step 2: Collapse buttons to icons on mobile**

In `app-topbar.ts`, update the unauthenticated buttons section (lines 50-53):

Replace:
```html
@if (authService.isAuthenticated()) {
  <button pButton class="p-button-text" icon="pi pi-user" (click)="menu.toggle($event)" #menuButton></button>
  <p-menu #menu [popup]="true" [model]="userMenuItems" [appendTo]="'body'"></p-menu>
} @else {
  <button pButton class="p-button-text p-button-sm" label="Sign In" icon="pi pi-sign-in" routerLink="/login"></button>
  <button pButton class="p-button-sm" label="Get Started" routerLink="/register"></button>
}
```

With:
```html
@if (authService.isAuthenticated()) {
  <button pButton class="p-button-text" icon="pi pi-user" (click)="menu.toggle($event)" #menuButton></button>
  <p-menu #menu [popup]="true" [model]="userMenuItems" [appendTo]="'body'"></p-menu>
} @else {
  <button pButton class="p-button-text p-button-sm hidden sm:inline-flex" label="Sign In" icon="pi pi-sign-in" routerLink="/login"></button>
  <button pButton class="p-button-sm hidden sm:inline-flex" label="Get Started" routerLink="/register"></button>
  <button pButton class="p-button-text p-button-sm sm:hidden" icon="pi pi-sign-in" routerLink="/login" pTooltip="Sign In" tooltipPosition="bottom"></button>
  <button pButton class="p-button-sm sm:hidden" icon="pi pi-user-plus" routerLink="/register" pTooltip="Get Started" tooltipPosition="bottom"></button>
}
```

- [ ] **Step 2: Verify build**

Run: `npx tsc --noEmit`

---

### Task 4: Layout Shell — app-sidebar.ts iOS dvh fix

**Files:**
- Modify: `frontend/src/app/layout/component/app-sidebar.ts`

- [ ] **Step 1: Replace 100vh with 100dvh**

In `app-sidebar.ts` line 42, change:
```
height: calc(100vh - 64px);
```
To:
```
height: calc(100vh - 64px);
height: calc(100dvh - 64px);
```

(Dvh declaration after vh serves as progressive enhancement — browsers that support `dvh` use it, others fall back to `vh`)

- [ ] **Step 2: Verify build**

Run: `npx tsc --noEmit`

---

### Task 5: Layout Shell — app-footer.ts responsive links

**Files:**
- Modify: `frontend/src/app/layout/component/app-footer.ts`

- [ ] **Step 1: Add flex-wrap to footer links**

In `app-footer.ts` line 14, change:
```html
<div class="flex gap-6 justify-center mb-2">
```
To:
```html
<div class="flex gap-6 justify-center mb-2 flex-wrap">
```

- [ ] **Step 2: Verify build**

Run: `npx tsc --noEmit`

---

### Task 6: Critical Fix — admin-users.html table responsive

**Files:**
- Modify: `frontend/src/app/pages/admin-users/admin-users.html`

- [ ] **Step 1: Make p-table responsive with stack layout on mobile**

In `admin-users.html` line 39, change:
```html
<p-table [value]="filteredUsers" [paginator]="true" [rows]="10" [tableStyle]="{ 'min-width': '60rem' }">
```
To:
```html
<div class="overflow-x-auto">
  <p-table [value]="filteredUsers" [paginator]="true" [rows]="10" [tableStyle]="{ 'min-width': '100%' }" [responsiveLayout]="'stack'" [breakpoint]="'768px'">
  </p-table>
</div>
```

Also wrap the closing `</p-table>` with `</div>` (add `</div>` after `</p-table>` on line 99).

Wait, I need to look at the actual structure. The template has `<p-table>` on line 39 and `</p-table>` on line 99. I need to wrap it in a div.

Change:
```
  <p-table [value]="filteredUsers" [paginator]="true" [rows]="10" [tableStyle]="{ 'min-width': '60rem' }">
```
to:
```
  <div class="overflow-x-auto">
  <p-table [value]="filteredUsers" [paginator]="true" [rows]="10" [tableStyle]="{ 'min-width': '100%' }" [responsiveLayout]="'stack'" [breakpoint]="'768px'">
```

And before `</div>` on the last line (line 100), add `</div>`:
Actually the file ends with `</div>` on line 100 which closes the outer container. I need to add `</div>` before that closing tag.

- [ ] **Step 2: Update the closing tag**

After `</p-table>` (line 99), add `</div>` before the outer `</div>`.

- [ ] **Step 3: Verify build**

Run: `npx tsc --noEmit`

---

### Task 7: Critical Fix — inventory-management.html table responsive

**Files:**
- Modify: `frontend/src/app/pages/inventory-management/inventory-management.html`

- [ ] **Step 1: Fix overflow and add responsive stack layout**

Change `inventory-management.html` line 23-24:
```html
  <div class="rounded-xl border border-surface-border bg-surface-card overflow-hidden">
    <p-table [value]="filteredProducts">
```
To:
```html
  <div class="rounded-xl border border-surface-border bg-surface-card overflow-x-auto">
    <p-table [value]="filteredProducts" [responsiveLayout]="'stack'" [breakpoint]="'768px'">
```

- [ ] **Step 2: Verify build**

Run: `npx tsc --noEmit`

---

### Task 8: High Priority — home.html responsive

**Files:**
- Modify: `frontend/src/app/pages/home/home.html`

- [ ] **Step 1: Fix hero buttons to stack on mobile**

Change line 17:
```html
<div class="flex gap-4">
```
To:
```html
<div class="flex flex-col sm:flex-row gap-4">
```

Make buttons full-width on mobile with `w-full sm:w-auto`:
Line 18: `<button pButton routerLink="/products" label="Explore Collection" class="w-full sm:w-auto"></button>`
Line 19: `<button pButton class="p-button-outlined w-full sm:w-auto" label="View Lookbook" icon="pi pi-arrow-right" iconPos="right"></button>`

- [ ] **Step 2: Reduce auto-rows height on mobile**

Change line 33:
```html
<div class="grid grid-cols-1 md:grid-cols-3 gap-6 auto-rows-[250px]">
```
To:
```html
<div class="grid grid-cols-1 md:grid-cols-3 gap-6 auto-rows-[200px] md:auto-rows-[250px]">
```

- [ ] **Step 3: Verify build**

Run: `npx tsc --noEmit`

---

### Task 9: High Priority — login.html / register.html dvh fix

**Files:**
- Modify: `frontend/src/app/pages/login/login.html`
- Modify: `frontend/src/app/pages/register/register.html`

- [ ] **Step 1: Replace 100vh with 100dvh in login.html**

Change line 1 of `login.html`:
```html
<div class="flex items-center justify-center min-h-[calc(100vh-264px)] px-4">
```
To:
```html
<div class="flex items-center justify-center min-h-[calc(100vh-264px)] min-h-[calc(100dvh-264px)] px-4">
```

- [ ] **Step 2: Replace 100vh with 100dvh in register.html**

Change line 1 of `register.html`:
```html
<div class="flex items-center justify-center min-h-[calc(100vh-264px)] px-4 py-8">
```
To:
```html
<div class="flex items-center justify-center min-h-[calc(100vh-264px)] min-h-[calc(100dvh-264px)] px-4 py-8">
```

- [ ] **Step 3: Fix register account type buttons for small screens**

Change line 34 of `register.html`:
```html
<div class="grid grid-cols-2 gap-3">
```
To:
```html
<div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
```

- [ ] **Step 4: Verify build**

Run: `npx tsc --noEmit`

---

### Task 10: High Priority — my-account.html responsive

**Files:**
- Modify: `frontend/src/app/pages/my-account/my-account.html`

- [ ] **Step 1: Fix double padding at top**

Change line 1:
```html
<main class="flex-1 p-6 md:p-10 flex flex-col gap-8 max-w-7xl mx-auto w-full pt-16 md:pt-10">
```
To:
```html
<main class="flex-1 p-6 md:p-10 flex flex-col gap-8 max-w-7xl mx-auto w-full pt-6 md:pt-10">
```

- [ ] **Step 2: Fix footer links overflow**

Change line 127:
```html
<div class="flex gap-6">
```
To:
```html
<div class="flex gap-6 flex-wrap justify-center sm:justify-start">
```

- [ ] **Step 3: Fix shipping address truncation**

Change line 98-99:
```html
<td class="text-surface-500 text-xs truncate max-w-xs">{{ ord.shippingAddress }}</td>
```
To:
```html
<td class="text-surface-500 text-xs truncate max-w-[150px] sm:max-w-xs">{{ ord.shippingAddress }}</td>
```

- [ ] **Step 4: Add responsiveLayout to orders table**

Change line 86:
```html
<p-table [value]="ordersList">
```
To:
```html
<p-table [value]="ordersList" [responsiveLayout]="'stack'" [breakpoint]="'768px'">
```

- [ ] **Step 5: Verify build**

Run: `npx tsc --noEmit`

---

### Task 11: High Priority — checkout.html responsive

**Files:**
- Modify: `frontend/src/app/pages/checkout/checkout.html`

- [ ] **Step 1: Fix cramped 2-col grids on small screens**

Change line 31:
```html
<div class="grid grid-cols-2 gap-4">
```
To:
```html
<div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
```

Change line 57:
```html
<div class="grid grid-cols-2 gap-4">
```
To:
```html
<div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
```

- [ ] **Step 2: Remove max-h-64 on order summary to prevent clipping**

Change line 84:
```html
<div class="flex flex-col gap-4 max-h-64 overflow-y-auto pr-2">
```
To:
```html
<div class="flex flex-col gap-4 pr-2">
```

- [ ] **Step 3: Fix standalone footer**

Change lines 131-133:
```html
<footer class="w-full py-6 px-8 flex justify-center items-center text-xs text-surface-500 opacity-60 mt-auto">
  © 2024 Glacier Enterprise. Secure Checkout Environment.
</footer>
```
To:
```html
<footer class="w-full py-6 px-4 sm:px-8 flex justify-center items-center text-xs text-surface-500 opacity-60 mt-auto">
  © 2024 Glacier Enterprise. Secure Checkout Environment.
</footer>
```

- [ ] **Step 4: Verify build**

Run: `npx tsc --noEmit`

---

### Task 12: Medium Priority — product-details.html responsive

**Files:**
- Modify: `frontend/src/app/pages/product-details/product-details.html`

- [ ] **Step 1: Fix duplicate class attributes**

Change line 24:
```html
<button pButton class="p-button-text" size="small" icon="pi pi-heart" class="text-surface-500 hover:text-primary bg-surface-card">
```
To:
```html
<button pButton class="p-button-text text-surface-500 hover:text-primary bg-surface-card" size="small" icon="pi pi-heart">
```

Change line 26:
```html
<button pButton class="p-button-text" size="small" icon="pi pi-share" class="text-surface-500 hover:text-primary bg-surface-card">
```
To:
```html
<button pButton class="p-button-text text-surface-500 hover:text-primary bg-surface-card" size="small" icon="pi pi-share">
```

- [ ] **Step 2: Fix thumbnail responsive sizing**

Change line 33:
```html
<button class="w-24 h-24 rounded-xl border border-surface-border bg-surface-card flex-shrink-0 overflow-hidden relative transition-all duration-300"
```
To:
```html
<button class="w-16 sm:w-24 h-16 sm:h-24 rounded-xl border border-surface-border bg-surface-card flex-shrink-0 overflow-hidden relative transition-all duration-300"
```

- [ ] **Step 3: Make product header (breadcrumb + rating) stack on mobile**

Change line 3:
```html
<div class="flex items-center justify-between text-sm text-surface-500">
```
To:
```html
<div class="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2 text-sm text-surface-500">
```

- [ ] **Step 4: Verify build**

Run: `npx tsc --noEmit`

---

### Task 13: Medium Priority — product-listing.html responsive

**Files:**
- Modify: `frontend/src/app/pages/product-listing/product-listing.html`

- [ ] **Step 1: Fix duplicate class attributes**

Change line 6:
```html
<button pButton class="p-button-text" size="small" (click)="searchQuery = ''; selectedCategoryId = 'all'; priceRange = 3000; applyFilters()" class="text-primary">Clear all</button>
```
To:
```html
<button pButton class="p-button-text text-primary" size="small" (click)="searchQuery = ''; selectedCategoryId = 'all'; priceRange = 3000; applyFilters()">Clear all</button>
```

Change line 64:
```html
<button pButton class="p-button-text" size="small" icon="pi pi-heart" class="absolute top-3 right-3 z-10 text-surface-500 hover:text-primary bg-surface-card/50" (click)="$event.stopPropagation()">
```
To:
```html
<button pButton class="p-button-text absolute top-3 right-3 z-10 text-surface-500 hover:text-primary bg-surface-card/50" size="small" icon="pi pi-heart" (click)="$event.stopPropagation()">
```

- [ ] **Step 2: Fix sticky filter on mobile — remove sticky below lg**

Change line 3:
```html
<div class="rounded-xl border border-surface-border bg-surface-card p-5 flex flex-col gap-6 sticky top-24">
```
To:
```html
<div class="rounded-xl border border-surface-border bg-surface-card p-5 flex flex-col gap-6 lg:sticky lg:top-24">
```

- [ ] **Step 3: Fix radio touch targets — increase size**

Change lines 18 and 23 (the input elements):
```html
<input type="radio" name="category" [checked]="selectedCategoryId === 'all'" class="form-radio text-primary focus:ring-primary/30 w-4 h-4"/>
```
To:
```html
<input type="radio" name="category" [checked]="selectedCategoryId === 'all'" class="form-radio text-primary focus:ring-primary/30 w-5 h-5"/>
```

- [ ] **Step 4: Verify build**

Run: `npx tsc --noEmit`

---

### Task 14: Medium Priority — admin-dashboard.html responsive

**Files:**
- Modify: `frontend/src/app/pages/admin-dashboard/admin-dashboard.html`

- [ ] **Step 1: Add overflow-x-auto to Recent Sales table**

Change line 32:
```html
<p-table [value]="recentSales" [tableStyle]="{ 'min-width': '100%' }">
```
To:
```html
<div class="overflow-x-auto">
  <p-table [value]="recentSales" [tableStyle]="{ 'min-width': '100%' }" [responsiveLayout]="'stack'" [breakpoint]="'768px'">
</div>
```

Wait, that would break because `</p-table>` needs to come before `</div>`. Let me look at the structure again.

Lines 32-41:
```html
      <p-table [value]="recentSales" [tableStyle]="{ 'min-width': '100%' }">
        <ng-template pTemplate="body" let-sale>
          <tr>
            <td class="font-medium">{{ sale.name }}</td>
            <td>{{ sale.price }}</td>
            <td><span class="text-surface-500 text-sm">{{ sale.status }}</span></td>
          </tr>
        </ng-template>
      </p-table>
```

So the edit should be:
Line 32: `<p-table ...>` → `<div class="overflow-x-auto"><p-table ... [responsiveLayout]="'stack'" [breakpoint]="'768px'">`
Line 41: `</p-table>` → `</p-table></div>`

- [ ] **Step 2: Verify build**

Run: `npx tsc --noEmit`

---

### Task 15: Medium Priority — seller-dashboard.html responsive

**Files:**
- Modify: `frontend/src/app/pages/seller-dashboard/seller-dashboard.html`

- [ ] **Step 1: Fix chart bars container for better responsive behavior**

Change line 65:
```html
<div class="flex-grow w-full relative flex items-end justify-between px-2 pb-6 pt-4 border-b border-l border-surface-border/20">
```
To:
```html
<div class="flex-grow w-full relative flex items-end justify-between gap-1 sm:gap-2 px-2 pb-6 pt-4 border-b border-l border-surface-border/20 overflow-x-auto">
```

Change bar widths (line 71-74) for responsive sizing:
```html
<div class="min-w-[40px] w-full bg-gradient-to-t from-primary/10 to-primary/40 rounded-t-sm h-[40%] border-t border-primary/50 relative"></div>
<div class="min-w-[40px] w-full bg-gradient-to-t from-primary/10 to-primary/40 rounded-t-sm h-[55%] border-t border-primary/50 relative"></div>
<div class="min-w-[40px] w-full bg-gradient-to-t from-primary/10 to-primary/60 rounded-t-sm h-[80%] border-t border-primary relative shadow-[0_0_15px_rgba(125,211,252,0.3)]"></div>
<div class="min-w-[40px] w-full bg-gradient-to-t from-primary/10 to-primary/50 rounded-t-sm h-[95%] border-t border-primary relative shadow-[0_0_15px_rgba(125,211,252,0.3)]"></div>
```

Add `gap-1 sm:gap-2` and `overflow-x-auto` to the container. Change fixed `w-10` to `min-w-[40px] w-full`.

- [ ] **Step 2: Verify build**

Run: `npx tsc --noEmit`

---

### Task 16: Medium Priority — shopping-cart.html responsive

**Files:**
- Modify: `frontend/src/app/pages/shopping-cart/shopping-cart.html`

- [ ] **Step 1: Fix duplicate class attributes**

Change line 8:
```html
<button pButton class="p-button-text" size="small" icon="pi pi-trash" class="gap-2 text-primary" (click)="clearCart()" label="Clear Cart">
```
To:
```html
<button pButton class="p-button-text gap-2 text-primary" size="small" icon="pi pi-trash" (click)="clearCart()" label="Clear Cart">
```

Change line 31:
```html
<button pButton class="p-button-text" size="small" icon="pi pi-minus" class="size-6 text-surface-500 hover:text-primary" (click)="updateQuantity(item.product.id, item.quantity - 1)">
```
To:
```html
<button pButton class="p-button-text size-6 text-surface-500 hover:text-primary" size="small" icon="pi pi-minus" (click)="updateQuantity(item.product.id, item.quantity - 1)">
```

Change line 34:
```html
<button pButton class="p-button-text" size="small" icon="pi pi-plus" class="size-6 text-surface-500 hover:text-primary" (click)="updateQuantity(item.product.id, item.quantity + 1)">
```
To:
```html
<button pButton class="p-button-text size-6 text-surface-500 hover:text-primary" size="small" icon="pi pi-plus" (click)="updateQuantity(item.product.id, item.quantity + 1)">
```

Change line 39:
```html
<button pButton class="p-button-text" size="small" icon="pi pi-trash" class="gap-1 text-surface-500 hover:text-red-500" (click)="removeItem(item.product.id)" label="Remove">
```
To:
```html
<button pButton class="p-button-text gap-1 text-surface-500 hover:text-red-500" size="small" icon="pi pi-trash" (click)="removeItem(item.product.id)" label="Remove">
```

- [ ] **Step 2: Verify build**

Run: `npx tsc --noEmit`

---

### Task 17: Low Priority — order-success.html responsive footer

**Files:**
- Modify: `frontend/src/app/pages/order-success/order-success.html`

- [ ] **Step 1: Fix standalone footer responsiveness**

Change line 61:
```html
<footer class="w-full py-6 text-center text-xs text-surface-500">
```
To:
```html
<footer class="w-full py-6 px-4 text-center text-xs text-surface-500">
```

- [ ] **Step 2: Verify build**

Run: `npx tsc --noEmit`

---

### Task 18: Low Priority — add-product.html and edit-product.html responsive check

**Files:**
- Modify: `frontend/src/app/pages/add-product/add-product.html`
- Modify: `frontend/src/app/pages/edit-product/edit-product.html`

- [ ] **Step 1: Ensure form action buttons stack on mobile**

In `add-product.html` line 14, change:
```html
<div class="flex gap-3">
```
To:
```html
<div class="flex gap-3 w-full sm:w-auto">
```

Same change in `edit-product.html` line 20.

- [ ] **Step 2: Verify build**

Run: `npx tsc --noEmit`

---

### Task 19: Build verification

- [ ] **Step 1: Run TypeScript type check**

Run: `npx tsc --noEmit` from `frontend/`

Expected: No type errors

- [ ] **Step 2: Run build**

Run: `ng build --configuration development`

Expected: Build succeeds with no errors

- [ ] **Step 3: Verify visual check against all breakpoints**

Run: `ng serve` and open browser at 375px, 768px, and 1440px widths to verify:
- No horizontal scroll on mobile
- Tables use stack layout on small screens
- Topbar buttons fit without overflow
- Hero section buttons stack vertically
- Login/register forms properly centered
- Cart, checkout, product pages all usable at every breakpoint
