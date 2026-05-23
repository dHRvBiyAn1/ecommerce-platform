# PrimeNG Migration & Sakai Admin Template Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Zard UI with PrimeNG + Sakai admin layout across all pages

**Architecture:** Phased approach — (1) Foundation: install PrimeNG deps, create Sakai layout shell, remove Zard. (2) Public page migration. (3) Admin/seller page migration. (4) Cleanup. Each phase keeps the app working.

**Tech Stack:** Angular 21, PrimeNG 21, PrimeIcons 7, @primeuix/themes (Aura), PrimeFlex, Tailwind CSS 4, Chart.js

---

## File Structure

### Files to Create
- `frontend/src/app/layout/service/layout.service.ts` — layout state management (menu mode, dark mode)
- `frontend/src/app/layout/component/app-layout.ts` — root layout wrapper
- `frontend/src/app/layout/component/app-topbar.ts` — top navigation bar
- `frontend/src/app/layout/component/app-sidebar.ts` — sidebar with role-based menu
- `frontend/src/app/layout/component/app-footer.ts` — footer
- `frontend/src/app/layout/component/app.menu.ts` — menu model definition
- `frontend/src/app/pages/admin-dashboard/admin-dashboard.ts` — new admin dashboard page
- `frontend/src/app/pages/admin-dashboard/admin-dashboard.html` — admin dashboard template
- `frontend/src/primeng-theme.css` — PrimeNG theme customizations

### Files to Modify
- `frontend/package.json` — add PrimeNG deps, remove Zard deps
- `frontend/angular.json` — update styles, remove Zard config
- `frontend/src/index.html` — update stylesheets
- `frontend/src/styles.css` — add PrimeNG theme, remove Zard CSS vars
- `frontend/src/app/app.config.ts` — providePrimeNG, remove provideZard
- `frontend/src/app/app.ts` — wrap with app-layout
- `frontend/src/app/app.routes.ts` — add admin dashboard route
- `frontend/src/app/interceptors/auth.interceptor.ts` — use MessageService
- `frontend/src/app/guards/role.guard.ts` — use MessageService
- `frontend/src/app/pages/*` — all 13 pages (replace Zard with PrimeNG)

### Files to Delete
- `frontend/src/app/shared/` — entire directory (~150 Zard files)
- `frontend/src/app/layout/page-layout.ts` — replaced by Sakai layout
- `frontend/src/app/layout/admin/admin-layout.ts` — replaced by Sakai layout
- `frontend/src/app/layout/admin/` — entire directory

---

### Task 1: Install PrimeNG Dependencies

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/angular.json`

- [ ] **Step 1: Install PrimeNG packages**

```bash
cd /Users/dhruv/Developer/ecommerce-platform/frontend
npm install primeng primeicons @primeuix/themes tailwindcss-primeui chart.js
```

- [ ] **Step 2: Update angular.json styles**

Read `frontend/angular.json`, update the `styles` array under `build.options`:

```json
"styles": [
  "src/styles.css",
  "node_modules/primeicons/primeicons.css"
]
```

- [ ] **Step 3: Update index.html fonts**

Read `frontend/src/index.html` and add PrimeNG font (Inter is already there):

No change needed — Inter font is already loaded. PrimeNG works with any font.

---

### Task 2: Create Layout Service

**Files:**
- Create: `frontend/src/app/layout/service/layout.service.ts`

- [ ] **Step 1: Create layout service**

```typescript
import { Injectable, signal, computed } from '@angular/core';

export interface LayoutConfig {
  menuMode: 'static' | 'overlay';
  darkMode: boolean;
}

export interface LayoutState {
  staticMenuDesktopInactive: boolean;
  overlayMenuActive: boolean;
  mobileMenuActive: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class LayoutService {
  private config = signal<LayoutConfig>({
    menuMode: 'overlay',
    darkMode: true,
  });

  private state = signal<LayoutState>({
    staticMenuDesktopInactive: false,
    overlayMenuActive: false,
    mobileMenuActive: false,
  });

  layoutConfig = this.config.asReadonly();
  layoutState = this.state.asReadonly();

  containerClass = computed(() => {
    const config = this.config();
    const state = this.state();
    return {
      'layout-overlay': config.menuMode === 'overlay',
      'layout-static': config.menuMode === 'static',
      'layout-static-inactive': state.staticMenuDesktopInactive && config.menuMode === 'static',
      'layout-overlay-active': state.overlayMenuActive,
      'layout-mobile-active': state.mobileMenuActive,
    };
  });

  onMenuToggle() {
    const config = this.config();
    const state = this.state();

    if (config.menuMode === 'overlay') {
      this.state.update(s => ({ ...s, overlayMenuActive: !state.overlayMenuActive }));
    }

    if (config.menuMode === 'static') {
      this.state.update(s => ({ ...s, staticMenuDesktopInactive: !state.staticMenuDesktopInactive }));
    }
  }

  onOverlaySubmenuOpen() {
    this.state.update(s => ({ ...s, mobileMenuActive: true }));
  }

  toggleDarkMode() {
    this.config.update(c => ({ ...c, darkMode: !c.darkMode }));
    const isDark = this.config().darkMode;
    if (isDark) {
      document.documentElement.classList.add('app-dark');
    } else {
      document.documentElement.classList.remove('app-dark');
    }
  }

  onMenuModeChange(menuMode: 'static' | 'overlay') {
    this.config.update(c => ({ ...c, menuMode }));
  }

  hideMenu() {
    this.state.update(s => ({
      ...s,
      overlayMenuActive: false,
      staticMenuDesktopInactive: false,
      mobileMenuActive: false,
    }));
  }
}
```

- [ ] **Step 2: Verify file created**

Run: `ls -la frontend/src/app/layout/service/layout.service.ts`
Expected: File exists with LayoutService class

---

### Task 3: Create Menu Model

**Files:**
- Create: `frontend/src/app/layout/component/app.menu.ts`

- [ ] **Step 1: Create menu model with role-based items**

```typescript
export interface MenuItem {
  label: string;
  icon: string;
  routerLink: string[];
  visible?: boolean;
  roles?: string[];
}

export const publicMenuItems: MenuItem[] = [
  { label: 'Home', icon: 'pi pi-home', routerLink: ['/'] },
  { label: 'Products', icon: 'pi pi-box', routerLink: ['/products'] },
];

export const authMenuItems: MenuItem[] = [
  { label: 'My Account', icon: 'pi pi-user', routerLink: ['/account'], roles: [] },
];

export const sellerMenuItems: MenuItem[] = [
  {
    label: 'Seller Dashboard',
    icon: 'pi pi-chart-bar',
    routerLink: ['/seller'],
    roles: ['ROLE_SELLER'],
  },
  {
    label: 'Inventory',
    icon: 'pi pi-warehouse',
    routerLink: ['/seller/inventory'],
    roles: ['ROLE_SELLER'],
  },
  {
    label: 'Add Product',
    icon: 'pi pi-plus-circle',
    routerLink: ['/seller/add-product'],
    roles: ['ROLE_SELLER'],
  },
];

export const adminMenuItems: MenuItem[] = [
  {
    label: 'Admin Dashboard',
    icon: 'pi pi-chart-line',
    routerLink: ['/admin/dashboard'],
    roles: ['ROLE_ADMIN'],
  },
  {
    label: 'Users',
    icon: 'pi pi-users',
    routerLink: ['/admin/users'],
    roles: ['ROLE_ADMIN'],
  },
];
```

---

### Task 4: Create Topbar Component

**Files:**
- Create: `frontend/src/app/layout/component/app-topbar.ts`

- [ ] **Step 1: Create app-topbar**

```typescript
import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { ButtonModule } from 'primeng/button';
import { BadgeModule } from 'primeng/badge';
import { MenuModule } from 'primeng/menu';
import { AuthService } from '../../services/auth.service';
import { ApiService } from '../../services/api.service';
import { LayoutService } from '../service/layout.service';

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, ButtonModule, BadgeModule, MenuModule],
  template: `
    <div class="layout-topbar">
      <div class="layout-topbar-start">
        <button pButton class="p-button-text layout-menu-button" icon="pi pi-bars" (click)="layoutService.onMenuToggle()"></button>
        <a routerLink="/" class="layout-topbar-logo">
          <i class="pi pi-star text-2xl text-primary"></i>
          <span class="ml-2 text-xl font-semibold">Glacier</span>
        </a>
      </div>

      <div class="layout-topbar-center hidden md:flex items-center gap-4">
        <a routerLink="/products" routerLinkActive="font-semibold text-primary" class="text-surface-400 hover:text-surface-900 dark:hover:text-surface-0 transition-colors px-3 py-2 text-sm">
          <i class="pi pi-box mr-1"></i>
          Products
        </a>
      </div>

      <div class="layout-topbar-end">
        <button pButton class="p-button-text p-relative" icon="pi pi-shopping-cart" routerLink="/cart">
          @if (cartCount() > 0) {
            <p-badge [value]="cartCount()" severity="danger" class="absolute -top-1 -right-1"></p-badge>
          }
        </button>

        @if (authService.isAuthenticated()) {
          <button pButton class="p-button-text" icon="pi pi-user" (click)="menuVisible = !menuVisible" #menuButton></button>
          <p-menu #menu [popup]="true" [model]="userMenuItems" [appendTo]="'body'" (click)="menuVisible = false"></p-menu>
        } @else {
          <button pButton class="p-button-text p-button-sm" label="Sign In" icon="pi pi-sign-in" routerLink="/login"></button>
          <button pButton class="p-button-sm" label="Get Started" routerLink="/register"></button>
        }
      </div>
    </div>
  `,
  styles: [`
    .layout-topbar {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      z-index: 1000;
      height: 64px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 2rem;
      background: var(--p-surface-card);
      border-bottom: 1px solid var(--p-surface-border);
    }
    .layout-topbar-start, .layout-topbar-end {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    :host ::ng-deep .p-button.p-button-text {
      color: var(--p-text-muted-color);
    }
    :host ::ng-deep .p-button.p-button-text:hover {
      color: var(--p-text-color);
    }
  `]
})
export class AppTopbar {
  protected layoutService = inject(LayoutService);
  protected authService = inject(AuthService);
  protected apiService = inject(ApiService);

  protected cartCount = toSignal(
    this.apiService.cart$.pipe(map(c => c.reduce((acc, item) => acc + item.quantity, 0))),
    { initialValue: 0 }
  );

  menuVisible = false;

  userMenuItems = [
    { label: 'Profile', icon: 'pi pi-user', routerLink: ['/account'] },
    { separator: true },
    { label: 'Logout', icon: 'pi pi-sign-out', command: () => this.logout() },
  ];

  logout() {
    this.authService.logout();
    window.location.href = '/';
  }
}
```

---

### Task 5: Create Sidebar Component

**Files:**
- Create: `frontend/src/app/layout/component/app-sidebar.ts`

- [ ] **Step 1: Create app-sidebar**

```typescript
import { Component, inject, computed } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { LayoutService } from '../service/layout.service';
import { publicMenuItems, authMenuItems, sellerMenuItems, adminMenuItems } from './app.menu';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <div class="layout-sidebar" [class.active]="isActive()">
      <div class="layout-sidebar-content">
        <ul class="layout-menu">
          @for (item of visibleItems(); track item.label) {
            <li class="layout-menu-item">
              <a
                [routerLink]="item.routerLink"
                routerLinkActive="active-route"
                [routerLinkActiveOptions]="{ exact: true }"
                class="layout-menu-link"
                (click)="layoutService.hideMenu()"
              >
                <i [class]="item.icon" class="layout-menu-icon"></i>
                <span>{{ item.label }}</span>
              </a>
            </li>
          }
          @if (visibleItems().length === 0) {
            <li class="layout-menu-empty text-sm text-surface-400 px-4 py-3">No menu items available</li>
          }
        </ul>
      </div>
    </div>
  `,
  styles: [`
    .layout-sidebar {
      position: fixed;
      left: 0;
      top: 64px;
      width: 260px;
      height: calc(100vh - 64px);
      background: var(--p-surface-card);
      border-right: 1px solid var(--p-surface-border);
      transition: transform 0.3s;
      z-index: 900;
      overflow-y: auto;
    }
    .layout-sidebar:not(.active) {
      transform: translateX(-100%);
    }
    .layout-overlay .layout-sidebar.active {
      transform: translateX(0);
    }
    .layout-static .layout-sidebar {
      transform: translateX(0);
    }
    .layout-static-inactive .layout-sidebar {
      transform: translateX(-100%);
    }
    .layout-menu {
      list-style: none;
      padding: 1rem 0;
      margin: 0;
    }
    .layout-menu-item {
      margin: 2px 0;
    }
    .layout-menu-link {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.75rem 1.25rem;
      color: var(--p-text-muted-color);
      text-decoration: none;
      font-size: 0.875rem;
      border-radius: 0;
      transition: all 0.2s;
      cursor: pointer;
    }
    .layout-menu-link:hover {
      color: var(--p-text-color);
      background: var(--p-surface-hover);
    }
    .layout-menu-link.active-route {
      color: var(--p-primary-color);
      background: var(--p-primary-100);
    }
    .layout-menu-icon {
      width: 1.25rem;
      text-align: center;
    }
    :root.app-dark .layout-menu-link.active-route {
      background: color-mix(in srgb, var(--p-primary-color) 15%, transparent);
    }
  `]
})
export class AppSidebar {
  protected authService = inject(AuthService);
  protected layoutService = inject(LayoutService);

  protected isActive = computed(() => {
    const state = this.layoutService.layoutState();
    const config = this.layoutService.layoutConfig();
    if (config.menuMode === 'overlay') return state.overlayMenuActive;
    if (config.menuMode === 'static') return !state.staticMenuDesktopInactive;
    return false;
  });

  protected visibleItems = computed(() => {
    const user = this.authService.currentUser();
    const roles: string[] = user?.roles ?? [];
    const isAuth = this.authService.isAuthenticated();

    const items = [...publicMenuItems];
    if (isAuth) items.push(...authMenuItems);

    if (roles.includes('ROLE_SELLER') || roles.includes('ROLE_ADMIN')) {
      items.push(...sellerMenuItems);
    }
    if (roles.includes('ROLE_ADMIN')) {
      items.push(...adminMenuItems);
    }

    return items;
  });
}
```

---

### Task 6: Create Footer Component

**Files:**
- Create: `frontend/src/app/layout/component/app-footer.ts`

- [ ] **Step 1: Create app-footer**

```typescript
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="layout-footer text-center py-6 text-sm text-surface-400 border-t border-surface-border">
      <div class="flex items-center justify-center gap-2 mb-2">
        <i class="pi pi-star text-primary"></i>
        <span class="font-semibold text-surface-700 dark:text-surface-100">Glacier Commerce</span>
      </div>
      <div class="flex gap-6 justify-center mb-2">
        <a routerLink="/" class="hover:text-surface-700 dark:hover:text-surface-200 transition-colors">Privacy Policy</a>
        <a routerLink="/" class="hover:text-surface-700 dark:hover:text-surface-200 transition-colors">Terms of Service</a>
        <a routerLink="/" class="hover:text-surface-700 dark:hover:text-surface-200 transition-colors">Help Center</a>
      </div>
      <div>&copy; 2024 Glacier Enterprise. All rights reserved.</div>
    </div>
  `,
})
export class AppFooter {}
```

---

### Task 7: Create App Layout Component

**Files:**
- Create: `frontend/src/app/layout/component/app-layout.ts`

- [ ] **Step 1: Create app-layout**

```typescript
import { Component, effect, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ToastModule } from 'primeng/toast';
import { MessageService } from 'primeng/api';
import { AppTopbar } from './app-topbar';
import { AppSidebar } from './app-sidebar';
import { AppFooter } from './app-footer';
import { LayoutService } from '../service/layout.service';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, ToastModule, AppTopbar, AppSidebar, AppFooter],
  providers: [MessageService],
  template: `
    <p-toast></p-toast>
    <div class="layout-wrapper" [ngClass]="layoutService.containerClass()">
      <app-topbar></app-topbar>
      <app-sidebar></app-sidebar>
      <div class="layout-main-container">
        <div class="layout-main">
          <router-outlet></router-outlet>
        </div>
        <app-footer></app-footer>
      </div>
      @if (isOverlayActive()) {
        <div class="layout-mask" (click)="layoutService.hideMenu()"></div>
      }
    </div>
  `,
  styles: [`
    .layout-wrapper {
      display: flex;
      flex-direction: column;
      min-height: 100vh;
    }
    .layout-main-container {
      display: flex;
      flex-direction: column;
      flex: 1;
      margin-top: 64px;
      transition: margin-left 0.3s;
    }
    .layout-static .layout-main-container {
      margin-left: 260px;
    }
    .layout-static-inactive .layout-main-container,
    .layout-overlay .layout-main-container {
      margin-left: 0;
    }
    .layout-main {
      flex: 1;
      padding: 2rem;
      min-height: 0;
    }
    .layout-mask {
      position: fixed;
      inset: 0;
      background: rgba(0,0,0,0.4);
      z-index: 899;
    }
  `]
})
export class AppLayout {
  protected layoutService = inject(LayoutService);

  protected isOverlayActive = this.layoutService.layoutState;

  constructor() {
    effect(() => {
      const state = this.layoutService.layoutState();
      if (state.mobileMenuActive || state.overlayMenuActive) {
        document.body.classList.add('blocked-scroll');
      } else {
        document.body.classList.remove('blocked-scroll');
      }
    });

    // Initialize dark mode
    document.documentElement.classList.add('app-dark');
  }
}
```

---

### Task 8: Update App Component and Config

**Files:**
- Modify: `frontend/src/app/app.ts`
- Modify: `frontend/src/app/app.config.ts`
- Modify: `frontend/src/app/app.routes.ts`

- [ ] **Step 1: Update app.html to use app-layout**

Read `frontend/src/app/app.ts` and update:

```typescript
import { Component } from '@angular/core';
import { AppLayout } from './layout/component/app-layout';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [AppLayout],
  template: `<app-layout></app-layout>`,
})
export class App {}
```

- [ ] **Step 2: Update app.config.ts**

Read `frontend/src/app/app.config.ts` and update:

```typescript
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import Aura from '@primeuix/themes/aura';
import { providePrimeNG } from 'primeng/config';
import { routes } from './app.routes';
import { authInterceptor } from './interceptors/auth.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    providePrimeNG({
      theme: {
        preset: Aura,
        options: { darkModeSelector: '.app-dark' },
      },
    }),
  ],
};
```

- [ ] **Step 3: Update app.routes.ts — add admin dashboard route**

Read `frontend/src/app/app.routes.ts`. Add import for AdminDashboard and add the dashboard route under admin children:

```typescript
import { AdminDashboard } from './pages/admin-dashboard/admin-dashboard';

// In the admin children array:
children: [
  { path: 'dashboard', component: AdminDashboard },
  { path: 'users', component: AdminUsers },
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
],
```

Full updated file:

```typescript
import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';
import { roleGuard } from './guards/role.guard';
import { Home } from './pages/home/home';
import { ProductListing } from './pages/product-listing/product-listing';
import { ProductDetails } from './pages/product-details/product-details';
import { ShoppingCart } from './pages/shopping-cart/shopping-cart';
import { Checkout } from './pages/checkout/checkout';
import { OrderSuccess } from './pages/order-success/order-success';
import { MyAccount } from './pages/my-account/my-account';
import { SellerDashboard } from './pages/seller-dashboard/seller-dashboard';
import { InventoryManagement } from './pages/inventory-management/inventory-management';
import { AddProduct } from './pages/add-product/add-product';
import { Login } from './pages/login/login';
import { Register } from './pages/register/register';
import { NotFound } from './pages/not-found/not-found';
import { AdminUsers } from './pages/admin-users/admin-users';
import { AdminDashboard } from './pages/admin-dashboard/admin-dashboard';

export const routes: Routes = [
  { path: '', component: Home },
  { path: 'login', component: Login },
  { path: 'register', component: Register },
  { path: 'products', component: ProductListing },
  { path: 'product', component: ProductDetails },
  { path: 'cart', component: ShoppingCart },
  { path: 'checkout', component: Checkout, canActivate: [authGuard] },
  { path: 'order-success', component: OrderSuccess, canActivate: [authGuard] },
  { path: 'account', component: MyAccount, canActivate: [authGuard] },
  { path: 'seller', component: SellerDashboard, canActivate: [authGuard, roleGuard], data: { roles: ['ROLE_SELLER'] } },
  { path: 'seller/inventory', component: InventoryManagement, canActivate: [authGuard, roleGuard], data: { roles: ['ROLE_SELLER'] } },
  { path: 'seller/add-product', component: AddProduct, canActivate: [authGuard, roleGuard], data: { roles: ['ROLE_SELLER'] } },
  {
    path: 'admin',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ROLE_ADMIN'] },
    children: [
      { path: 'dashboard', component: AdminDashboard },
      { path: 'users', component: AdminUsers },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    ],
  },
  { path: 'not-found', component: NotFound },
  { path: '**', redirectTo: '/not-found' },
];
```

---

### Task 9: Remove Zard UI Completely

**Files:**
- Modify: `frontend/package.json`
- Delete: `frontend/src/app/shared/`
- Delete: `frontend/src/app/layout/page-layout.ts`
- Delete: `frontend/src/app/layout/admin/admin-layout.ts`
- Delete: `frontend/src/app/layout/admin/`

- [ ] **Step 1: Remove Zard component dirs and layout files**

```bash
cd /Users/dhruv/Developer/ecommerce-platform/frontend
rm -rf src/app/shared
rm -rf src/app/services/dark-mode.ts
rm -f src/app/layout/page-layout.ts
rm -rf src/app/layout/admin
```

- [ ] **Step 2: Remove Zard dependencies from package.json**

Read `frontend/package.json`, remove these from `dependencies`:
- `class-variance-authority`
- `clsx`
- `tailwind-merge`
- `ngx-sonner`
- `embla-carousel-angular`
- `embla-carousel-autoplay`
- `embla-carousel-class-names`
- `embla-carousel-wheel-gestures`

Also remove from `devDependencies`:
- `tailwindcss-animate`

- [ ] **Step 3: Uninstall packages**

```bash
cd /Users/dhruv/Developer/ecommerce-platform/frontend
npm uninstall class-variance-authority clsx tailwind-merge ngx-sonner embla-carousel-angular embla-carousel-autoplay embla-carousel-class-names embla-carousel-wheel-gestures tailwindcss-animate
```

---

### Task 10: Update Styles

**Files:**
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Replace styles.css content**

Write `frontend/src/styles.css`:

```css
@layer ng-icon, theme, base, components, utilities;
@import 'tailwindcss';
@plugin "tailwindcss-primeui";

@custom-variant dark (&:is(.dark *));

@custom-variant app-dark (&:is(.app-dark *));

:root {
  --radius: 0.625rem;
}

::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

::-webkit-scrollbar-thumb {
  background: var(--p-text-muted-color);
  border-radius: 5px;
}

::-webkit-scrollbar-track {
  border-radius: 5px;
  background: var(--p-surface-200);
}

body {
  margin: 0;
  font-family: 'Inter', sans-serif;
}

.blocked-scroll {
  overflow: hidden;
}
```

---

### Task 11: Update Auth Interceptor and Role Guard

**Files:**
- Modify: `frontend/src/app/interceptors/auth.interceptor.ts`
- Modify: `frontend/src/app/guards/role.guard.ts`

- [ ] **Step 1: Update auth.interceptor.ts**

Read `frontend/src/app/interceptors/auth.interceptor.ts` and update:

```typescript
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { AuthService } from '../services/auth.service';
import { tap } from 'rxjs/operators';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const messageService = inject(MessageService);

  const token = authService.getToken();
  if (token) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    });
  }

  return next(req).pipe(
    tap({
      error: (err) => {
        if (err.status === 401) {
          messageService.add({ severity: 'error', summary: 'Session Expired', detail: 'Please login again' });
          authService.logout();
          router.navigateByUrl('/login');
        } else if (err.status === 403) {
          messageService.add({ severity: 'error', summary: 'Unauthorized', detail: 'You do not have access' });
        }
      },
    })
  );
};
```

- [ ] **Step 2: Update role.guard.ts**

Read `frontend/src/app/guards/role.guard.ts` and update:

```typescript
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { AuthService } from '../services/auth.service';

export const roleGuard = (route: any) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const messageService = inject(MessageService);

  const user = authService.currentUser();
  const requiredRoles: string[] = route.data?.roles ?? [];

  if (!user || !requiredRoles.some(r => user.roles?.includes(r))) {
    messageService.add({ severity: 'error', summary: 'Unauthorized', detail: 'You do not have the required permissions' });
    router.navigateByUrl('/');
    return false;
  }

  return true;
};
```

---

### Task 12: Create Admin Dashboard Page

**Files:**
- Create: `frontend/src/app/pages/admin-dashboard/admin-dashboard.ts`
- Create: `frontend/src/app/pages/admin-dashboard/admin-dashboard.html`

- [ ] **Step 1: Create admin-dashboard.ts**

```typescript
import { Component } from '@angular/core';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { ChartModule } from 'primeng/chart';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CardModule, TableModule, ChartModule, ButtonModule],
  templateUrl: './admin-dashboard.html',
})
export class AdminDashboard {
  stats = [
    { label: 'Revenue', value: '$2,100', icon: 'pi pi-dollar', color: 'text-green-500', change: '+52%' },
    { label: 'Orders', value: '152', icon: 'pi pi-shopping-cart', color: 'text-blue-500', change: '+24 new' },
    { label: 'Customers', value: '28,441', icon: 'pi pi-users', color: 'text-purple-500', change: '+520' },
    { label: 'Products', value: '1,203', icon: 'pi pi-box', color: 'text-orange-500', change: '+12' },
  ];

  recentSales = [
    { image: '', name: 'Bamboo Watch', price: '$65.00', status: 'Shipped' },
    { image: '', name: 'Black Watch', price: '$72.00', status: 'Pending' },
    { image: '', name: 'Blue Band', price: '$79.00', status: 'Delivered' },
    { image: '', name: 'Blue T-Shirt', price: '$29.00', status: 'Shipped' },
    { image: '', name: 'Bracelet', price: '$15.00', status: 'Cancelled' },
  ];

  chartData = {
    labels: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun'],
    datasets: [
      {
        label: 'Revenue',
        data: [1200, 1900, 1600, 2100, 1800, 2400],
        fill: false,
        borderColor: '#3B82F6',
        tension: 0.4,
      },
    ],
  };

  chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
  };
}
```

- [ ] **Step 2: Create admin-dashboard.html**

```html
<div class="grid grid-cols-12 gap-6">
  <!-- Page Header -->
  <div class="col-span-12">
    <h2 class="text-2xl font-semibold text-surface-900 dark:text-surface-0">Admin Dashboard</h2>
    <p class="text-surface-500 mt-1">Overview of platform metrics and activity</p>
  </div>

  <!-- Stats Cards -->
  @for (stat of stats; track stat.label) {
    <div class="col-span-12 sm:col-span-6 lg:col-span-3">
      <p-card>
        <div class="flex items-center justify-between">
          <div>
            <span class="text-surface-500 text-sm font-medium">{{ stat.label }}</span>
            <h3 class="text-2xl font-bold text-surface-900 dark:text-surface-0 mt-1">{{ stat.value }}</h3>
            <span class="text-xs text-surface-400">{{ stat.change }}</span>
          </div>
          <i [class]="stat.icon + ' ' + stat.color + ' text-3xl'"></i>
        </div>
      </p-card>
    </div>
  }

  <!-- Revenue Chart -->
  <div class="col-span-12 lg:col-span-8">
    <p-card header="Revenue Stream">
      <div style="height: 300px">
        <p-chart type="line" [data]="chartData" [options]="chartOptions"></p-chart>
      </div>
    </p-card>
  </div>

  <!-- Recent Sales -->
  <div class="col-span-12 lg:col-span-4">
    <p-card header="Recent Sales">
      <p-table [value]="recentSales" [tableStyle]="{ 'min-width': '100%' }">
        <ng-template pTemplate="body" let-sale>
          <tr>
            <td class="font-medium">{{ sale.name }}</td>
            <td>{{ sale.price }}</td>
            <td><span class="text-surface-500 text-sm">{{ sale.status }}</span></td>
          </tr>
        </ng-template>
      </p-table>
    </p-card>
  </div>
</div>
```

---

### Task 13: Migrate Home Page

**Files:**
- Modify: `frontend/src/app/pages/home/home.ts`
- Modify: `frontend/src/app/pages/home/home.html`

- [ ] **Step 1: Update home.ts**

```typescript
import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ApiService, Product } from '../../services/api.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, CommonModule, ButtonModule, InputTextModule],
  templateUrl: './home.html',
})
export class Home implements OnInit {
  private apiService = inject(ApiService);
  products: Product[] = [];
  featuredProduct: Product | null = null;
  trendingProducts: Product[] = [];

  ngOnInit() {
    this.apiService.getProducts().subscribe(products => {
      this.products = products;
      if (products.length > 0) {
        this.featuredProduct = products[0];
        this.trendingProducts = products.slice(1);
      }
    });
  }

  addToCart(product: Product, event: Event) {
    event.stopPropagation();
    this.apiService.addToCart(product);
  }
}
```

- [ ] **Step 2: Update home.html**

```html
<div class="max-w-7xl mx-auto flex flex-col gap-16 pb-20">
  <!-- Hero Banner -->
  <section class="relative w-full rounded-2xl overflow-hidden min-h-[400px] flex items-center p-8 md:p-16 border border-surface-border">
    <img
      alt="Hero Background"
      class="absolute inset-0 w-full h-full object-cover opacity-30"
      src="https://lh3.googleusercontent.com/aida-public/AB6AXuA3I0xY-0pce5W3BOttV4lsPCLU2_fee0HCOMw6VzonovghjJwRHadqvS7bLCzOxrzg6uHFfFPpZpaiWUfhCRKbwCR7wxjGih8uWHBgzQSkmCCYyYt3mEYEOl4qDWqkDiGqr429bhZj0aKZTF34dxfX_L4hcY7R54i9VeKt8pudcPYX7t3ZzQ2AS79gDHhUwYrjgh-d9MF_xlxvJm1OFIZAzTwrukKqXBNqg3bm4oV0sYfr0iR6t7225WJoi1GxhKj49h3JcEFH8kM"
    />
    <div class="absolute inset-0 bg-gradient-to-r from-surface-background via-surface-background/80 to-transparent"></div>
    <div class="relative z-10 max-w-2xl">
      <span class="inline-block px-3 py-1 rounded-full border border-primary/20 bg-primary/10 text-primary text-xs font-semibold tracking-wider uppercase mb-4">New Arrival</span>
      <h1 class="text-4xl md:text-6xl font-semibold tracking-tight mb-4 leading-tight">
        Experience the <br/><span class="text-primary">Frozen Light</span>
      </h1>
      <p class="text-lg text-surface-500 mb-8 max-w-lg">
        Discover our latest collection of premium digital assets. Crafted with precision, designed for the future of interactive experiences.
      </p>
      <div class="flex gap-4">
        <button pButton routerLink="/products" label="Explore Collection"></button>
        <button pButton class="p-button-outlined" label="View Lookbook" icon="pi pi-arrow-right" iconPos="right"></button>
      </div>
    </div>
  </section>

  <!-- Trending Grid (Bento) -->
  <section>
    <div class="flex justify-between items-end mb-8">
      <div>
        <h2 class="text-2xl font-semibold">Trending Now</h2>
        <p class="text-surface-500 text-sm mt-1">High-demand items updated hourly.</p>
      </div>
      <button pButton class="p-button-text p-button-sm" label="View All" icon="pi pi-chevron-right" iconPos="right" routerLink="/products"></button>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-3 gap-6 auto-rows-[250px]">
      @if (featuredProduct) {
        <div
          class="md:col-span-2 md:row-span-2 rounded-xl border border-surface-border bg-surface-card p-6 flex flex-col justify-end relative overflow-hidden group cursor-pointer"
          [routerLink]="['/product']" [queryParams]="{ id: featuredProduct.id }"
        >
          <div class="absolute inset-0 bg-gradient-to-t from-surface-background/90 to-transparent z-10"></div>
          <img [alt]="featuredProduct.name" class="absolute inset-0 w-full h-full object-cover opacity-60 group-hover:scale-105 transition-transform duration-700" [src]="featuredProduct.imageUrls[0]"/>
          <div class="relative z-20 flex justify-between items-end">
            <div>
              <span class="text-primary text-xs font-semibold tracking-wider uppercase">Featured</span>
              <h3 class="text-2xl font-semibold mt-1">{{ featuredProduct.name }}</h3>
              <p class="text-surface-500 mt-2 max-w-sm line-clamp-2">{{ featuredProduct.description }}</p>
            </div>
            <button pButton class="p-button-text p-button-rounded" icon="pi pi-shopping-cart" (click)="addToCart(featuredProduct, $event)"></button>
          </div>
        </div>
      }

      @for (prod of trendingProducts; track prod.id) {
        <div
          class="rounded-xl border border-surface-border bg-surface-card p-5 flex flex-col relative overflow-hidden group cursor-pointer"
          [routerLink]="['/product']" [queryParams]="{ id: prod.id }"
        >
          <div class="w-full h-32 mb-4 rounded-lg overflow-hidden relative bg-surface-ground">
            <img [alt]="prod.name" class="w-full h-full object-cover opacity-70 group-hover:opacity-100 transition-opacity" [src]="prod.imageUrls[0]"/>
          </div>
          <h4 class="text-lg font-medium">{{ prod.name }}</h4>
          <div class="mt-auto flex justify-between items-center pt-4">
            <span class="text-primary font-semibold">\${{ prod.price | number:'1.2-2' }}</span>
            <button pButton class="p-button-text p-button-rounded" icon="pi pi-shopping-cart" (click)="addToCart(prod, $event)"></button>
          </div>
        </div>
      }
    </div>
  </section>

  <!-- Newsletter Section -->
  <section class="rounded-2xl border border-surface-border bg-surface-card p-8 md:p-12 text-center relative overflow-hidden">
    <div class="max-w-xl mx-auto">
      <i class="pi pi-envelope text-3xl text-primary mb-4 block"></i>
      <h2 class="text-2xl font-semibold mb-2">Join the Inner Circle</h2>
      <p class="text-surface-500 mb-6 text-sm">Subscribe to receive early access to new releases, exclusive insights, and platform updates.</p>
      <form class="flex flex-col sm:flex-row gap-3 justify-center" (submit)="$event.preventDefault()">
        <input pInputText type="email" placeholder="Enter your email address" class="w-full sm:w-auto" />
        <button pButton type="submit" label="Subscribe"></button>
      </form>
    </div>
  </section>
</div>
```

---

### Task 14: Migrate Login Page

**Files:**
- Modify: `frontend/src/app/pages/login/login.ts`
- Modify: `frontend/src/app/pages/login/login.html`

- [ ] **Step 1: Update login.ts**

```typescript
import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { CheckboxModule } from 'primeng/checkbox';
import { MessageService } from 'primeng/api';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [RouterLink, FormsModule, CommonModule, ButtonModule, InputTextModule, PasswordModule, CheckboxModule],
  templateUrl: './login.html',
})
export class Login {
  private authService = inject(AuthService);
  private router = inject(Router);
  private messageService = inject(MessageService);

  email = '';
  password = '';
  loading = false;

  login() {
    if (!this.email || !this.password) return;
    this.loading = true;
    this.authService.login(this.email, this.password).subscribe({
      next: (user) => {
        this.loading = false;
        if (user.roles?.includes('ROLE_ADMIN')) {
          this.router.navigateByUrl('/admin/dashboard');
        } else if (user.roles?.includes('ROLE_SELLER')) {
          this.router.navigateByUrl('/seller');
        } else {
          this.router.navigateByUrl('/');
        }
      },
      error: (err) => {
        this.loading = false;
        this.messageService.add({ severity: 'error', summary: 'Login Failed', detail: err.error?.message || 'Invalid credentials' });
      },
    });
  }
}
```

- [ ] **Step 2: Update login.html**

```html
<div class="min-h-[calc(100vh-200px)] flex items-center justify-center">
  <div class="w-full max-w-md p-8">
    <div class="text-center mb-8">
      <i class="pi pi-star text-4xl text-primary mb-4 block"></i>
      <h2 class="text-2xl font-semibold">Welcome back</h2>
      <p class="text-surface-500 text-sm mt-2">Sign in to your Glacier account</p>
    </div>

    <form (ngSubmit)="login()" class="flex flex-col gap-5">
      <div>
        <label class="block text-sm font-medium mb-2" for="email">Email</label>
        <input pInputText id="email" type="email" class="w-full" [(ngModel)]="email" name="email" placeholder="Enter your email" required />
      </div>

      <div>
        <label class="block text-sm font-medium mb-2" for="password">Password</label>
        <p-password id="password" [(ngModel)]="password" name="password" [feedback]="false" [toggleMask]="true" class="w-full" inputStyleClass="w-full" styleClass="w-full"></p-password>
      </div>

      <div class="flex items-center justify-between">
        <div class="flex items-center gap-2">
          <p-checkbox binary="true" inputId="remember"></p-checkbox>
          <label class="text-sm" for="remember">Remember me</label>
        </div>
        <a class="text-sm text-primary hover:underline cursor-pointer">Forgot password?</a>
      </div>

      <button pButton type="submit" [loading]="loading" label="Sign In" class="w-full"></button>
    </form>

    <p class="text-center text-sm text-surface-500 mt-6">
      Don't have an account?
      <a routerLink="/register" class="text-primary font-medium hover:underline">Create one</a>
    </p>
  </div>
</div>
```

---

### Task 15: Migrate Register Page

**Files:**
- Modify: `frontend/src/app/pages/register/register.ts`
- Modify: `frontend/src/app/pages/register/register.html`

- [ ] **Step 1: Update register.ts**

```typescript
import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { PasswordModule } from 'primeng/password';
import { SelectModule } from 'primeng/select';
import { MessageService } from 'primeng/api';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [RouterLink, FormsModule, ButtonModule, InputTextModule, PasswordModule, SelectModule],
  templateUrl: './register.html',
})
export class Register {
  private authService = inject(AuthService);
  private router = inject(Router);
  private messageService = inject(MessageService);

  displayName = '';
  email = '';
  password = '';
  confirmPassword = '';
  userType = 'CUSTOMER';
  loading = false;

  userTypeOptions = [
    { label: 'Customer', value: 'CUSTOMER' },
    { label: 'Seller', value: 'SELLER' },
  ];

  register() {
    if (!this.displayName || !this.email || !this.password) return;
    if (this.password !== this.confirmPassword) {
      this.messageService.add({ severity: 'error', summary: 'Error', detail: 'Passwords do not match' });
      return;
    }
    this.loading = true;
    this.authService.register({ displayName: this.displayName, email: this.email, password: this.password, userType: this.userType }).subscribe({
      next: () => {
        this.loading = false;
        this.messageService.add({ severity: 'success', summary: 'Registered', detail: 'Account created successfully' });
        this.router.navigateByUrl('/login');
      },
      error: (err) => {
        this.loading = false;
        this.messageService.add({ severity: 'error', summary: 'Error', detail: err.error?.message || 'Registration failed' });
      },
    });
  }
}
```

- [ ] **Step 2: Update register.html**

```html
<div class="min-h-[calc(100vh-200px)] flex items-center justify-center">
  <div class="w-full max-w-md p-8">
    <div class="text-center mb-8">
      <i class="pi pi-star text-4xl text-primary mb-4 block"></i>
      <h2 class="text-2xl font-semibold">Create account</h2>
      <p class="text-surface-500 text-sm mt-2">Join Glacier Commerce today</p>
    </div>

    <form (ngSubmit)="register()" class="flex flex-col gap-5">
      <div>
        <label class="block text-sm font-medium mb-2" for="displayName">Display Name</label>
        <input pInputText id="displayName" type="text" class="w-full" [(ngModel)]="displayName" name="displayName" placeholder="Your name" required />
      </div>

      <div>
        <label class="block text-sm font-medium mb-2" for="email">Email</label>
        <input pInputText id="email" type="email" class="w-full" [(ngModel)]="email" name="email" placeholder="you@example.com" required />
      </div>

      <div>
        <label class="block text-sm font-medium mb-2" for="password">Password</label>
        <p-password id="password" [(ngModel)]="password" name="password" [toggleMask]="true" class="w-full" inputStyleClass="w-full" styleClass="w-full"></p-password>
      </div>

      <div>
        <label class="block text-sm font-medium mb-2" for="confirmPassword">Confirm Password</label>
        <p-password id="confirmPassword" [(ngModel)]="confirmPassword" name="confirmPassword" [feedback]="false" [toggleMask]="true" class="w-full" inputStyleClass="w-full" styleClass="w-full"></p-password>
      </div>

      <div>
        <label class="block text-sm font-medium mb-2" for="userType">Account Type</label>
        <p-select id="userType" [options]="userTypeOptions" [(ngModel)]="userType" name="userType" class="w-full" styleClass="w-full"></p-select>
      </div>

      <button pButton type="submit" [loading]="loading" label="Create Account" class="w-full"></button>
    </form>

    <p class="text-center text-sm text-surface-500 mt-6">
      Already have an account?
      <a routerLink="/login" class="text-primary font-medium hover:underline">Sign in</a>
    </p>
  </div>
</div>
```

---

### Task 16: Migrate Admin Users Page

**Files:**
- Modify: `frontend/src/app/pages/admin-users/admin-users.ts`
- Modify: `frontend/src/app/pages/admin-users/admin-users.html`

- [ ] **Step 1: Update admin-users.ts**

```typescript
import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { CardModule } from 'primeng/card';
import { BadgeModule } from 'primeng/badge';
import { SelectModule } from 'primeng/select';
import { ApiService, User } from '../../services/api.service';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [CommonModule, FormsModule, TableModule, ButtonModule, InputTextModule, CardModule, BadgeModule, SelectModule],
  templateUrl: './admin-users.html',
})
export class AdminUsers implements OnInit {
  private apiService = inject(ApiService);

  users: User[] = [];
  filteredUsers: User[] = [];
  searchQuery = '';
  selectedRoleFilter = 'ALL';

  totalUsersCount = 0;
  adminsCount = 0;
  activeCount = 0;
  suspendedCount = 0;

  roleOptions = [
    { label: 'All Roles', value: 'ALL' },
    { label: 'Buyers', value: 'BUYER' },
    { label: 'Sellers', value: 'SELLER' },
    { label: 'Admins', value: 'ADMIN' },
  ];

  ngOnInit() {
    this.apiService.users$.subscribe(allUsers => {
      this.users = allUsers;
      this.calculateStats();
      this.applyFilters();
    });
  }

  calculateStats() {
    this.totalUsersCount = this.users.length;
    this.adminsCount = this.users.filter(u => u.role === 'ADMIN').length;
    this.activeCount = this.users.filter(u => u.status === 'ACTIVE').length;
    this.suspendedCount = this.users.filter(u => u.status === 'SUSPENDED').length;
  }

  applyFilters() {
    let result = [...this.users];

    if (this.selectedRoleFilter !== 'ALL') {
      result = result.filter(u => u.role === this.selectedRoleFilter);
    }

    if (this.searchQuery.trim()) {
      const query = this.searchQuery.toLowerCase();
      result = result.filter(u =>
        u.name.toLowerCase().includes(query) ||
        u.email.toLowerCase().includes(query),
      );
    }

    this.filteredUsers = result;
  }

  toggleUserStatus(user: User) {
    const nextStatus = user.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
    if (confirm(`Are you sure you want to transition ${user.name} to ${nextStatus}?`)) {
      this.apiService.updateUserStatus(user.id, nextStatus);
    }
  }

  changeUserRole(userId: string, event: Event) {
    const selectEl = event.target as HTMLSelectElement;
    const newRole = selectEl.value as 'BUYER' | 'SELLER' | 'ADMIN';
    this.apiService.updateUserRole(userId, newRole);
  }
}
```

- [ ] **Step 2: Update admin-users.html**

```html
<div class="max-w-[1400px] mx-auto flex flex-col gap-8">
  <!-- Page Header -->
  <div class="flex flex-col md:flex-row md:items-center justify-between gap-4">
    <div>
      <h2 class="text-2xl font-semibold text-surface-900 dark:text-surface-0">Platform Governance</h2>
      <p class="text-surface-500 mt-1 text-sm">Manage global user access, roles, and moderation flags dynamically.</p>
    </div>
  </div>

  <!-- Stat Cards -->
  <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
    <p-card>
      <p class="text-xs text-surface-500 uppercase font-semibold">Total Accounts</p>
      <h3 class="text-2xl font-bold text-surface-900 dark:text-surface-0 mt-1">{{ totalUsersCount }}</h3>
    </p-card>
    <p-card>
      <p class="text-xs text-surface-500 uppercase font-semibold">System Admins</p>
      <h3 class="text-2xl font-bold text-primary mt-1">{{ adminsCount }}</h3>
    </p-card>
    <p-card>
      <p class="text-xs text-surface-500 uppercase font-semibold">Active Users</p>
      <h3 class="text-2xl font-bold text-green-500 mt-1">{{ activeCount }}</h3>
    </p-card>
    <p-card>
      <p class="text-xs text-surface-500 uppercase font-semibold">Suspended Accounts</p>
      <h3 class="text-2xl font-bold text-red-500 mt-1">{{ suspendedCount }}</h3>
    </p-card>
  </div>

  <!-- Filters -->
  <div class="flex flex-col md:flex-row gap-4 items-center justify-between">
    <span class="p-input-icon-left w-full md:max-w-md">
      <i class="pi pi-search"></i>
      <input pInputText class="w-full" placeholder="Search by name or email..." [(ngModel)]="searchQuery" (ngModelChange)="applyFilters()" />
    </span>
    <div class="flex items-center gap-3 w-full md:w-auto">
      <span class="text-sm text-surface-500 font-medium">Filter Role:</span>
      <p-select [options]="roleOptions" [(ngModel)]="selectedRoleFilter" (ngModelChange)="applyFilters()" styleClass="w-40"></p-select>
    </div>
  </div>

  <!-- Data Table -->
  <p-table [value]="filteredUsers" [paginator]="true" [rows]="10" [tableStyle]="{ 'min-width': '60rem' }">
    <ng-template pTemplate="header">
      <tr>
        <th class="pl-6">User Identity</th>
        <th>Role Transitions</th>
        <th>Status</th>
        <th>Joined Date</th>
        <th class="text-center pr-6">Access Control</th>
      </tr>
    </ng-template>
    <ng-template pTemplate="body" let-user>
      <tr [class.bg-red-50]="user.status === 'SUSPENDED'">
        <td class="pl-6">
          <div class="flex items-center gap-3">
            @if (user.avatarUrl) {
              <img [alt]="user.name" class="w-10 h-10 rounded-full border border-primary/20 object-cover" [src]="user.avatarUrl" />
            } @else {
              <div class="w-10 h-10 rounded-full border border-primary/20 bg-primary/10 flex items-center justify-center font-bold text-sm text-primary">
                {{ user.name.substring(0, 2).toUpperCase() }}
              </div>
            }
            <div>
              <div class="font-medium text-surface-900 dark:text-surface-0 text-sm" [class.opacity-60]="user.status === 'SUSPENDED'">{{ user.name }}</div>
              <div class="text-xs text-surface-500">{{ user.email }}</div>
            </div>
          </div>
        </td>
        <td>
          <select class="bg-transparent border border-surface-border text-surface-700 dark:text-surface-200 text-sm rounded px-2 py-1"
                  [value]="user.role" (change)="changeUserRole(user.id, $event)">
            <option value="BUYER">BUYER</option>
            <option value="SELLER">SELLER</option>
            <option value="ADMIN">ADMIN</option>
          </select>
        </td>
        <td>
          @if (user.status === 'ACTIVE') {
            <p-badge value="Active" severity="success"></p-badge>
          } @else {
            <p-badge value="Suspended" severity="danger"></p-badge>
          }
        </td>
        <td class="text-sm text-surface-500 font-medium">{{ user.joinedDate }}</td>
        <td class="text-center pr-6">
          @if (user.status === 'ACTIVE') {
            <button pButton size="small" class="p-button-outlined p-button-danger" label="Suspend" (click)="toggleUserStatus(user)"></button>
          } @else {
            <button pButton size="small" class="p-button-outlined p-button-success" label="Reactivate" (click)="toggleUserStatus(user)"></button>
          }
        </td>
      </tr>
    </ng-template>
    <ng-template pTemplate="emptymessage">
      <tr>
        <td colspan="5" class="text-center py-16 text-surface-500">
          <i class="pi pi-users text-3xl text-primary mb-3 block"></i>
          <p class="font-medium text-surface-700 dark:text-surface-200">No users matched search criteria.</p>
        </td>
      </tr>
    </ng-template>
  </p-table>
</div>
```

---

### Task 17: Migrate Remaining Pages (Batch)

**Files:**
- Modify: `frontend/src/app/pages/product-listing/product-listing.ts` + `.html`
- Modify: `frontend/src/app/pages/product-details/product-details.ts` + `.html`
- Modify: `frontend/src/app/pages/shopping-cart/shopping-cart.ts` + `.html`
- Modify: `frontend/src/app/pages/checkout/checkout.ts` + `.html`
- Modify: `frontend/src/app/pages/order-success/order-success.ts` + `.html`
- Modify: `frontend/src/app/pages/my-account/my-account.ts` + `.html`
- Modify: `frontend/src/app/pages/seller-dashboard/seller-dashboard.ts` + `.html`
- Modify: `frontend/src/app/pages/inventory-management/inventory-management.ts` + `.html`
- Modify: `frontend/src/app/pages/add-product/add-product.ts` + `.html`
- Modify: `frontend/src/app/pages/not-found/not-found.ts`

The pattern for each page is:
1. Remove `NgIcon` + `provideIcons` from imports
2. Remove Zard component imports (`ZardButtonComponent`, `ZardInputDirective`, `ZardBadgeComponent`, `ZardCardComponent`, `ZardTableComponent`, etc.)
3. Add PrimeNG module imports (`ButtonModule`, `InputTextModule`, `BadgeModule`, `CardModule`, `TableModule`, `InputNumberModule`, `SelectModule`, `TextareaModule`, `FileUploadModule`, etc.)
4. Replace `<ng-icon name="lucide*">` with `<i class="pi pi-*">`
5. Replace `<button z-button [zType]="...">` with `<button pButton class="p-button-...">`
6. Replace `<input z-input>` with `<input pInputText>`
7. Replace `<z-card>` with `<p-card>`
8. Replace `<z-badge>` with `<p-badge>`

- [ ] **Step 1: Product Listing page** — replace Zard imports, use PrimeNG data components. Remove `ZardButtonComponent`, `ZardInputDirective`, `ZardBadgeComponent`, `ZardCardComponent`; add `ButtonModule`, `InputTextModule`, `BadgeModule`, `CardModule`. Replace `<button z-button>` with `<button pButton>`, `<input z-input>` with `<input pInputText>`, `<z-badge>` with `<p-badge>`, `<z-card>` with `<p-card>`, `<ng-icon>` with `<i class="pi pi-...">`.
- [ ] **Step 2: Product Details page** — read `product-details.ts` and `.html`. Remove `NgIcon`, `provideIcons`, `ZardButtonComponent`, `ZardBadgeComponent`. Add `ButtonModule`, `BadgeModule`. Replace `<ng-icon>` icons with `<i class="pi pi-*">`, `<button z-button>` with `<button pButton>`, `<z-badge>` with `<p-badge>`.
- [ ] **Step 3: Shopping Cart page** — read `shopping-cart.ts` and `.html`. Remove `NgIcon`, `ZardButtonComponent`, `ZardInputDirective`, `ZardBadgeComponent`. Add `ButtonModule`, `InputTextModule`, `InputNumberModule`, `BadgeModule`. Replace Zard table with `<p-table>` template-based approach. Replace `<ng-icon>` with `<i class="pi pi-*">`.
- [ ] **Step 4: Checkout page** — read `checkout.ts` and `.html`. Remove `NgIcon`, `ZardButtonComponent`, `ZardInputDirective`. Add `ButtonModule`, `InputTextModule`, `SelectModule`, `CardModule`. Replace `<ng-icon>` with `<i class="pi pi-*">`. Use `<p-select>` for dropdown.
- [ ] **Step 5: Order Success page** — read `order-success.ts` and `.html`. Remove `NgIcon`, `ZardButtonComponent`, `ZardCardComponent`. Add `ButtonModule`, `CardModule`. Replace `<ng-icon>` with `<i class="pi pi-*">`.
- [ ] **Step 6: My Account page** — read `my-account.ts` and `.html`. Remove `NgIcon`, `ZardButtonComponent`, `ZardCardComponent`, `ZardInputDirective`. Add `ButtonModule`, `CardModule`, `InputTextModule`. Replace `<ng-icon>` with `<i class="pi pi-*">`.
- [ ] **Step 7: Seller Dashboard page** — read `seller-dashboard.ts` and `.html`. Remove `NgIcon`, `ZardButtonComponent`, `ZardCardComponent`. Add `ButtonModule`, `CardModule`, `ChartModule`, `TableModule`. Replace `<ng-icon>` with `<i class="pi pi-*">`.
- [ ] **Step 8: Inventory Management page** — read `inventory-management.ts` and `.html`. Remove `NgIcon`, `ZardButtonComponent`, `ZardInputDirective`, `ZardBadgeComponent`, `ZardTableComponent`. Add `ButtonModule`, `InputTextModule`, `BadgeModule`, `TableModule`. Replace Zard table with `<p-table>`, `<ng-icon>` with `<i class="pi pi-*">`.
- [ ] **Step 9: Add Product page** — read `add-product.ts` and `.html`. Remove `NgIcon`, `ZardButtonComponent`, `ZardInputDirective`. Add `ButtonModule`, `InputTextModule`, `TextareaModule`, `SelectModule`, `FileUploadModule`. Replace `<ng-icon>` with `<i class="pi pi-*">`. Use `<p-select>` for category dropdown, `<p-fileupload>` for image upload.
- [ ] **Step 10: Not Found page** — read `not-found.ts`. Remove `NgIcon`, `provideIcons`, `ZardButtonComponent`. Add `ButtonModule`. Replace `<ng-icon>` with `<i class="pi pi-*">`, `<button z-button>` with `<button pButton>`.

---

### Task 18: Build and Verify

- [ ] **Step 1: Build the project**

```bash
cd /Users/dhruv/Developer/ecommerce-platform/frontend
ng build 2>&1
```

Expected: Build succeeds with zero errors and zero warnings.

- [ ] **Step 2: Check for any remaining Zard references**

```bash
cd /Users/dhruv/Developer/ecommerce-platform/frontend
grep -r "zard\|z-\|Zard\|ngx-sonner\|ng-icon\|@ng-icons" --include="*.ts" --include="*.html" src/ 2>/dev/null || echo "No Zard/ng-icons references found"
```

Expected: No references found.

- [ ] **Step 3: Verify bundle size**

```bash
cd /Users/dhruv/Developer/ecommerce-platform/frontend
ng build --stats-json 2>&1 | tail -5
```

Expected: Build completes successfully with reasonable bundle size.
