# Glacier Commerce — Sakai-NG UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign all 15 pages + layout shell to match Sakai-NG's design language (polished sidebar/topbar, theme configurator, recursive menu, animated transitions).

**Architecture:** Adopt Sakai-NG's layout component set (AppLayout → AppTopbar, AppSidebar, AppMenu, AppMenuitem, AppFooter, AppConfigurator, AppFloatingConfigurator) and extend the existing LayoutService with Sakai-NG config fields. All business logic (services, guards, interceptors, auth) stays untouched.

**Tech Stack:** Angular 21, PrimeNG 21, Tailwind CSS v4, Aura theme preset, `@primeuix/themes` (runtime theme switching), Chart.js, PrimeIcons

**Reminder:** Clone was stored at `/var/folders/8x/frmzltvx7j9g4s48mnhmshkr0000gn/T/opencode/sakai-ng` — remove it after completion.

---

### Task 1: Extend LayoutService with Sakai-NG config/state

**Files:**
- Modify: `src/app/layout/service/layout.service.ts`
- Create: (none)

- [ ] **Step 1: Rewrite LayoutService with Sakai-NG config/state fields**

The existing service has `layoutConfig` and `layoutState` signals. Extend them with Sakai-NG's config (preset, primary, surface, menuMode) and state fields (overlayMenuActive, configSidebarVisible, mobileMenuActive, menuHoverActive, activePath). Add computed signals and dark mode toggle with ViewTransition API.

```typescript
import { Injectable, effect, signal, computed } from '@angular/core';

export interface LayoutConfig {
  preset: string;
  primary: string;
  surface: string | undefined | null;
  darkTheme: boolean;
  menuMode: string;
}

interface LayoutState {
  staticMenuDesktopInactive: boolean;
  overlayMenuActive: boolean;
  configSidebarVisible: boolean;
  mobileMenuActive: boolean;
  menuHoverActive: boolean;
  activePath: string | null;
}

@Injectable({ providedIn: 'root' })
export class LayoutService {
  layoutConfig = signal<LayoutConfig>({
    preset: 'Aura',
    primary: 'emerald',
    surface: null,
    darkTheme: true,
    menuMode: 'overlay',
  });

  layoutState = signal<LayoutState>({
    staticMenuDesktopInactive: false,
    overlayMenuActive: false,
    configSidebarVisible: false,
    mobileMenuActive: false,
    menuHoverActive: false,
    activePath: null,
  });

  isDarkTheme = computed(() => this.layoutConfig().darkTheme);
  isOverlay = computed(() => this.layoutConfig().menuMode === 'overlay');
  isSidebarActive = computed(
    () => this.layoutState().overlayMenuActive || this.layoutState().mobileMenuActive
  );

  private initialized = false;

  constructor() {
    effect(() => {
      const config = this.layoutConfig();
      if (!this.initialized || !config) {
        this.initialized = true;
        return;
      }
      this.handleDarkModeTransition(config);
    });
  }

  private handleDarkModeTransition(config: LayoutConfig): void {
    if ('startViewTransition' in document) {
      document.startViewTransition(() => this.toggleDarkMode(config));
    } else {
      this.toggleDarkMode(config);
    }
  }

  toggleDarkMode(config?: LayoutConfig): void {
    const c = config || this.layoutConfig();
    document.documentElement.classList.toggle('app-dark', c.darkTheme);
  }

  onMenuToggle() {
    if (this.isOverlay()) {
      this.layoutState.update((prev) => ({
        ...prev,
        overlayMenuActive: !prev.overlayMenuActive,
      }));
    }
    if (window.innerWidth > 991) {
      this.layoutState.update((prev) => ({
        ...prev,
        staticMenuDesktopInactive: !prev.staticMenuDesktopInactive,
      }));
    } else {
      this.layoutState.update((prev) => ({
        ...prev,
        mobileMenuActive: !prev.mobileMenuActive,
      }));
    }
  }

  isDesktop(): boolean {
    return window.innerWidth > 991;
  }

  isMobile(): boolean {
    return !this.isDesktop();
  }
}
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `npx tsc --noEmit`
Expected: clean exit, no errors

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/layout/service/layout.service.ts
git commit -m "feat: extend LayoutService with Sakai-NG config and state"
```

---

### Task 2: Create AppMenuitem (recursive menu component)

**Files:**
- Create: `src/app/layout/component/app.menuitem.ts`
- Test: (manual verification in browser)

- [ ] **Step 1: Create AppMenuitem component**

This is a recursive component using an attribute selector `[app-menuitem]`. It renders menu items with icons, labels, submenu toggles, and recursive submenu lists. Uses `host` binding for active state classes and inline styles for submenu animations.

```typescript
import { Component, computed, inject, input, signal } from '@angular/core';
import { NavigationEnd, Router, RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { RippleModule } from 'primeng/ripple';
import { LayoutService } from '../service/layout.service';
import { filter } from 'rxjs/operators';

@Component({
  selector: '[app-menuitem]',
  standalone: true,
  imports: [CommonModule, RouterModule, RippleModule],
  template: `
    @if (root() && isVisible()) {
      <div class="layout-menuitem-root-text">{{ item().label }}</div>
    }
    @if ((!hasRouterLink() || hasChildren()) && isVisible()) {
      <a
        [attr.href]="item().url"
        (click)="itemClick($event)"
        [ngClass]="item().class"
        [attr.target]="item().target"
        tabindex="0"
        pRipple
      >
        <i [ngClass]="item().icon" class="layout-menuitem-icon"></i>
        <span class="layout-menuitem-text">{{ item().label }}</span>
        @if (hasChildren()) {
          <i class="pi pi-fw pi-angle-down layout-submenu-toggler"></i>
        }
      </a>
    }
    @if (hasRouterLink() && !hasChildren() && isVisible()) {
      <a
        (click)="itemClick($event)"
        [ngClass]="item().class"
        [routerLink]="item().routerLink"
        routerLinkActive="active-route"
        [routerLinkActiveOptions]="item().routerLinkActiveOptions || { paths: 'exact', queryParams: 'ignored', matrixParams: 'ignored', fragment: 'ignored' }"
        [fragment]="item().fragment"
        [queryParamsHandling]="item().queryParamsHandling"
        [preserveFragment]="item().preserveFragment"
        [skipLocationChange]="item().skipLocationChange"
        [replaceUrl]="item().replaceUrl"
        [state]="item().state"
        [queryParams]="item().queryParams"
        [attr.target]="item().target"
        tabindex="0"
        pRipple
      >
        <i [ngClass]="item().icon" class="layout-menuitem-icon"></i>
        <span class="layout-menuitem-text">{{ item().label }}</span>
        @if (hasChildren()) {
          <i class="pi pi-fw pi-angle-down layout-submenu-toggler"></i>
        }
      </a>
    }
    @if (hasChildren() && isVisible() && (root() || isActive())) {
      <ul [ngClass]="{ 'layout-root-submenulist': root() }">
        @for (child of item().items; track child.label) {
          <li app-menuitem [item]="child" [parentPath]="fullPath()" [root]="false"></li>
        }
      </ul>
    }
  `,
  host: {
    '[class.active-menuitem]': 'isActive()',
    '[class.layout-root-menuitem]': 'root()',
  },
  styles: [
    `
      .p-submenu-enter {
        animation: p-animate-submenu-expand 450ms cubic-bezier(0.86, 0, 0.07, 1) forwards;
      }
      .p-submenu-leave {
        animation: p-animate-submenu-collapse 450ms cubic-bezier(0.86, 0, 0.07, 1) forwards;
      }
      @keyframes p-animate-submenu-expand {
        from { max-height: 0; overflow: hidden; }
        to { max-height: 1000px; overflow: visible; }
      }
      @keyframes p-animate-submenu-collapse {
        from { max-height: 1000px; overflow: hidden; }
        to { max-height: 0; overflow: hidden; }
      }
    `,
  ],
})
export class AppMenuitem {
  layoutService = inject(LayoutService);
  router = inject(Router);

  item = input<any>(null);
  root = input<boolean>(false);
  parentPath = input<string | null>(null);

  isVisible = computed(() => this.item()?.visible !== false);
  hasChildren = computed(() => !!this.item()?.items?.length);
  hasRouterLink = computed(() => !!this.item()?.routerLink);

  fullPath = computed(() => {
    const itemPath = this.item()?.path;
    if (!itemPath) return this.parentPath();
    const parent = this.parentPath();
    if (parent && !itemPath.startsWith(parent)) {
      return parent + itemPath;
    }
    return itemPath;
  });

  isActive = computed(() => {
    const activePath = this.layoutService.layoutState().activePath;
    if (this.item()?.path) {
      return activePath?.startsWith(this.fullPath() ?? '') ?? false;
    }
    return false;
  });

  constructor() {
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd))
      .subscribe(() => {
        if (this.item()?.routerLink) {
          this.updateActiveStateFromRoute();
        }
      });
  }

  ngOnInit() {
    if (this.item()?.routerLink) {
      this.updateActiveStateFromRoute();
    }
  }

  updateActiveStateFromRoute() {
    const item = this.item();
    if (!item?.routerLink) return;
    const isRouteActive = this.router.isActive(item.routerLink[0], {
      paths: 'exact',
      queryParams: 'ignored',
      matrixParams: 'ignored',
      fragment: 'ignored',
    });
    if (isRouteActive) {
      const parentPath = this.parentPath();
      if (parentPath) {
        this.layoutService.layoutState.update((val) => ({
          ...val,
          activePath: parentPath,
        }));
      }
    }
  }

  itemClick(event: Event) {
    const item = this.item();
    if (item?.disabled) {
      event.preventDefault();
      return;
    }
    if (item?.command) {
      item.command({ originalEvent: event, item });
    }
    if (this.hasChildren()) {
      this.layoutService.layoutState.update((val) => ({
        ...val,
        activePath: this.isActive() ? this.parentPath() : this.fullPath(),
        menuHoverActive: !this.isActive(),
      }));
    } else {
      this.layoutService.layoutState.update((val) => ({
        ...val,
        overlayMenuActive: false,
        staticMenuMobileActive: false,
        mobileMenuActive: false,
        menuHoverActive: false,
      }));
    }
  }
}
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `npx tsc --noEmit`
Expected: clean exit

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/layout/component/app.menuitem.ts
git commit -m "feat: add recursive AppMenuitem component"
```

---

### Task 3: Create AppMenu (role-based menu model)

**Files:**
- Create: `src/app/layout/component/app.menu.ts`

- [ ] **Step 1: Create AppMenu with role-filtered items**

```typescript
import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MenuItem } from 'primeng/api';
import { AppMenuitem } from './app.menuitem';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-menu',
  standalone: true,
  imports: [CommonModule, AppMenuitem, RouterModule],
  template: `
    <ul class="layout-menu">
      @for (item of model; track item.label) {
        @if (!item.separator) {
          <li app-menuitem [item]="item" [root]="true"></li>
        } @else {
          <li class="menu-separator"></li>
        }
      }
    </ul>
  `,
})
export class AppMenu implements OnInit {
  private authService = inject(AuthService);
  model: MenuItem[] = [];

  ngOnInit() {
    const roles = this.authService.user()?.roles ?? [];
    const isAdmin = roles.includes('ROLE_ADMIN');
    const isSeller = roles.includes('ROLE_SELLER');
    const isAuth = this.authService.isAuthenticated();

    this.model = [
      {
        label: 'Home',
        items: [{ label: 'Dashboard', icon: 'pi pi-fw pi-home', routerLink: ['/'] }],
      },
      ...(isAuth
        ? [
            {
              label: 'Shop',
              items: [
                { label: 'Products', icon: 'pi pi-fw pi-th-large', routerLink: ['/products'] },
                { label: 'My Account', icon: 'pi pi-fw pi-user', routerLink: ['/account'] },
              ],
            },
          ]
        : [
            {
              label: 'Shop',
              items: [
                { label: 'Products', icon: 'pi pi-fw pi-th-large', routerLink: ['/products'] },
              ],
            },
          ]),
      ...(isSeller
        ? [
            {
              label: 'Seller',
              items: [
                { label: 'Dashboard', icon: 'pi pi-fw pi-chart-bar', routerLink: ['/seller'] },
                { label: 'Inventory', icon: 'pi pi-fw pi-table', routerLink: ['/seller/inventory'] },
                { label: 'Add Product', icon: 'pi pi-fw pi-plus', routerLink: ['/seller/add-product'] },
              ],
            },
          ]
        : []),
      ...(isAdmin
        ? [
            {
              label: 'Admin',
              items: [
                { label: 'Dashboard', icon: 'pi pi-fw pi-chart-line', routerLink: ['/admin/dashboard'] },
                { label: 'Users', icon: 'pi pi-fw pi-users', routerLink: ['/admin/users'] },
              ],
            },
          ]
        : []),
      {
        label: 'Links',
        items: [
          ...(isAuth
            ? [{ label: 'Cart', icon: 'pi pi-fw pi-shopping-cart', routerLink: ['/cart'] }]
            : []),
          ...(isAuth
            ? [{ label: 'Logout', icon: 'pi pi-fw pi-sign-out', command: () => this.authService.logout() }]
            : [
                { label: 'Sign In', icon: 'pi pi-fw pi-sign-in', routerLink: ['/login'] },
                { label: 'Register', icon: 'pi pi-fw pi-user-plus', routerLink: ['/register'] },
              ]),
        ],
      },
    ];
  }
}
```

- [ ] **Step 2: Verify TypeScript compiles**

Run: `npx tsc --noEmit`
Expected: clean exit

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/layout/component/app.menu.ts
git commit -m "feat: add role-based AppMenu component"
```

---

### Task 4: Create AppConfigurator and AppFloatingConfigurator

**Files:**
- Create: `src/app/layout/component/app.configurator.ts`
- Create: `src/app/layout/component/app.floating-configurator.ts`

- [ ] **Step 1: Create AppConfigurator with primary/surface/preset/menu-mode controls**

```typescript
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { Component, computed, inject, PLATFORM_ID, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { $t, updatePreset, updateSurfacePalette } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';
import Lara from '@primeuix/themes/lara';
import Nora from '@primeuix/themes/nora';
import { PrimeNG } from 'primeng/config';
import { SelectButtonModule } from 'primeng/selectbutton';
import { LayoutService } from '../service/layout.service';

const presets: Record<string, any> = { Aura, Lara, Nora };

interface SurfaceOption {
  name: string;
  palette?: Record<string, string>;
}

@Component({
  selector: 'app-configurator',
  standalone: true,
  imports: [CommonModule, FormsModule, SelectButtonModule],
  template: `
    <div class="flex flex-col gap-4">
      <div>
        <span class="text-sm text-muted-color font-semibold">Primary</span>
        <div class="pt-2 flex gap-2 flex-wrap justify-start">
          @for (color of primaryColors(); track color.name) {
            <button
              type="button"
              [title]="color.name"
              (click)="updateColors($event, 'primary', color)"
              [ngClass]="{ 'outline outline-primary': color.name === selectedPrimaryColor() }"
              class="cursor-pointer w-5 h-5 rounded-full flex shrink-0 items-center justify-center outline-offset-1 shadow"
              [style]="{ 'background-color': color.name === 'noir' ? 'var(--text-color)' : color.palette?.['500'] }"
            ></button>
          }
        </div>
      </div>
      <div>
        <span class="text-sm text-muted-color font-semibold">Surface</span>
        <div class="pt-2 flex gap-2 flex-wrap justify-start">
          @for (surface of surfaces; track surface.name) {
            <button
              type="button"
              [title]="surface.name"
              (click)="updateColors($event, 'surface', surface)"
              class="cursor-pointer w-5 h-5 rounded-full flex shrink-0 items-center justify-center p-0 outline-offset-1"
              [ngClass]="{ 'outline outline-primary': surface.name === (selectedSurfaceColor() ?? (layoutService.layoutConfig().darkTheme ? 'zinc' : 'slate')) }"
              [style]="{ 'background-color': surface.palette?.['500'] }"
            ></button>
          }
        </div>
      </div>
      <div class="flex flex-col gap-2">
        <span class="text-sm text-muted-color font-semibold">Presets</span>
        <p-selectbutton
          [options]="presetKeys"
          [ngModel]="selectedPreset()"
          (ngModelChange)="onPresetChange($event)"
          [allowEmpty]="false"
          size="small"
        />
      </div>
      <div class="flex flex-col gap-2">
        <span class="text-sm text-muted-color font-semibold">Menu Mode</span>
        <p-selectbutton
          [ngModel]="menuMode()"
          (ngModelChange)="onMenuModeChange($event)"
          [options]="menuModeOptions"
          [allowEmpty]="false"
          size="small"
        />
      </div>
    </div>
  `,
  host: {
    class:
      'hidden absolute top-13 right-0 w-72 p-4 bg-surface-0 dark:bg-surface-900 border border-surface rounded-border origin-top shadow-[0px_3px_5px_rgba(0,0,0,0.02),0px_0px_2px_rgba(0,0,0,0.05),0px_1px_4px_rgba(0,0,0,0.08)]',
  },
})
export class AppConfigurator {
  private router = inject(Router);
  private primeng = inject(PrimeNG);
  layoutService = inject(LayoutService);
  platformId = inject(PLATFORM_ID);

  presetKeys = Object.keys(presets);
  menuModeOptions = [
    { label: 'Static', value: 'static' },
    { label: 'Overlay', value: 'overlay' },
  ];

  surfaces: SurfaceOption[] = [
    { name: 'slate', palette: { 50: '#f8fafc', 100: '#f1f5f9', 200: '#e2e8f0', 300: '#cbd5e1', 400: '#94a3b8', 500: '#64748b', 600: '#475569', 700: '#334155', 800: '#1e293b', 900: '#0f172a', 950: '#020617' } },
    { name: 'gray', palette: { 50: '#f9fafb', 100: '#f3f4f6', 200: '#e5e7eb', 300: '#d1d5db', 400: '#9ca3af', 500: '#6b7280', 600: '#4b5563', 700: '#374151', 800: '#1f2937', 900: '#111827', 950: '#030712' } },
    { name: 'zinc', palette: { 50: '#fafafa', 100: '#f4f4f5', 200: '#e4e4e7', 300: '#d4d4d8', 400: '#a1a1aa', 500: '#71717a', 600: '#52525b', 700: '#3f3f46', 800: '#27272a', 900: '#18181b', 950: '#09090b' } },
    { name: 'neutral', palette: { 50: '#fafafa', 100: '#f5f5f5', 200: '#e5e5e5', 300: '#d4d4d4', 400: '#a3a3a3', 500: '#737373', 600: '#525252', 700: '#404040', 800: '#262626', 900: '#171717', 950: '#0a0a0a' } },
    { name: 'stone', palette: { 50: '#fafaf9', 100: '#f5f5f4', 200: '#e7e5e4', 300: '#d6d3d1', 400: '#a8a29e', 500: '#78716c', 600: '#57534e', 700: '#44403c', 800: '#292524', 900: '#1c1917', 950: '#0c0a09' } },
  ];

  selectedPrimaryColor = computed(() => this.layoutService.layoutConfig().primary);
  selectedSurfaceColor = computed(() => this.layoutService.layoutConfig().surface);
  selectedPreset = computed(() => this.layoutService.layoutConfig().preset);
  menuMode = computed(() => this.layoutService.layoutConfig().menuMode);

  primaryColors = computed<SurfaceOption[]>(() => {
    const presetPalette = presets[this.layoutService.layoutConfig().preset]?.primitive;
    const colors = ['emerald', 'green', 'lime', 'orange', 'amber', 'yellow', 'teal', 'cyan', 'sky', 'blue', 'indigo', 'violet', 'purple', 'fuchsia', 'pink', 'rose'];
    const result: SurfaceOption[] = [{ name: 'noir', palette: {} }];
    for (const color of colors) {
      result.push({ name: color, palette: presetPalette?.[color] });
    }
    return result;
  });

  ngOnInit() {
    if (isPlatformBrowser(this.platformId)) {
      this.onPresetChange(this.layoutService.layoutConfig().preset);
    }
  }

  private getPresetExt() {
    const color = this.primaryColors().find((c) => c.name === this.selectedPrimaryColor()) || { name: 'noir', palette: {} };
    const preset = this.layoutService.layoutConfig().preset;

    if (color.name === 'noir') {
      return {
        semantic: {
          primary: {
            50: '{surface.50}', 100: '{surface.100}', 200: '{surface.200}',
            300: '{surface.300}', 400: '{surface.400}', 500: '{surface.500}',
            600: '{surface.600}', 700: '{surface.700}', 800: '{surface.800}',
            900: '{surface.900}', 950: '{surface.950}',
          },
          colorScheme: {
            light: { primary: { color: '{primary.950}', contrastColor: '#ffffff', hoverColor: '{primary.800}', activeColor: '{primary.700}' }, highlight: { background: '{primary.950}', focusBackground: '{primary.700}', color: '#ffffff', focusColor: '#ffffff' } },
            dark: { primary: { color: '{primary.50}', contrastColor: '{primary.950}', hoverColor: '{primary.200}', activeColor: '{primary.300}' }, highlight: { background: '{primary.50}', focusBackground: '{primary.300}', color: '{primary.950}', focusColor: '{primary.950}' } },
          },
        },
      };
    }
    return {
      semantic: {
        primary: color.palette,
        colorScheme: {
          light: { primary: { color: '{primary.500}', contrastColor: '#ffffff', hoverColor: '{primary.600}', activeColor: '{primary.700}' }, highlight: { background: '{primary.50}', focusBackground: '{primary.100}', color: '{primary.700}', focusColor: '{primary.800}' } },
          dark: { primary: { color: '{primary.400}', contrastColor: '{surface.900}', hoverColor: '{primary.300}', activeColor: '{primary.200}' }, highlight: { background: 'color-mix(in srgb, {primary.400}, transparent 84%)', focusBackground: 'color-mix(in srgb, {primary.400}, transparent 76%)', color: 'rgba(255,255,255,.87)', focusColor: 'rgba(255,255,255,.87)' } },
        },
      },
    };
  }

  updateColors(event: Event, type: string, color: SurfaceOption) {
    if (type === 'primary') {
      this.layoutService.layoutConfig.update((state) => ({ ...state, primary: color.name }));
    } else if (type === 'surface') {
      this.layoutService.layoutConfig.update((state) => ({ ...state, surface: color.name }));
    }
    if (type === 'primary') {
      updatePreset(this.getPresetExt());
    } else if (type === 'surface' && color.palette) {
      updateSurfacePalette(color.palette);
    }
    event.stopPropagation();
  }

  onPresetChange(event: string) {
    this.layoutService.layoutConfig.update((state) => ({ ...state, preset: event }));
    const preset = presets[event];
    const surfacePalette = this.surfaces.find((s) => s.name === this.selectedSurfaceColor())?.palette;
    $t().preset(preset).preset(this.getPresetExt()).surfacePalette(surfacePalette).use({ useDefaultOptions: true });
  }

  onMenuModeChange(event: string) {
    this.layoutService.layoutConfig.update((prev) => ({ ...prev, menuMode: event }));
  }
}
```

- [ ] **Step 2: Create AppFloatingConfigurator**

```typescript
import { Component, computed, inject } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { StyleClassModule } from 'primeng/styleclass';
import { AppConfigurator } from './app.configurator';
import { LayoutService } from '../service/layout.service';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-floating-configurator',
  standalone: true,
  imports: [CommonModule, ButtonModule, StyleClassModule, AppConfigurator],
  template: `
    <div class="flex gap-4 top-8 right-8 fixed">
      <p-button
        type="button"
        (onClick)="toggleDarkMode()"
        [rounded]="true"
        [icon]="isDarkTheme() ? 'pi pi-moon' : 'pi pi-sun'"
        severity="secondary"
      />
      <div class="relative">
        <p-button
          icon="pi pi-palette"
          pStyleClass="@next"
          enterFromClass="hidden"
          enterActiveClass="animate-scalein"
          leaveToClass="hidden"
          leaveActiveClass="animate-fadeout"
          [hideOnOutsideClick]="true"
          type="button"
          rounded
        />
        <app-configurator />
      </div>
    </div>
  `,
})
export class AppFloatingConfigurator {
  private layoutService = inject(LayoutService);
  isDarkTheme = computed(() => this.layoutService.layoutConfig().darkTheme);

  toggleDarkMode() {
    this.layoutService.layoutConfig.update((state) => ({ ...state, darkTheme: !state.darkTheme }));
  }
}
```

- [ ] **Step 3: Verify TypeScript compiles**

Run: `npx tsc --noEmit`
Expected: clean exit

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/layout/component/app.configurator.ts frontend/src/app/layout/component/app.floating-configurator.ts
git commit -m "feat: add AppConfigurator and AppFloatingConfigurator"
```

---

### Task 5: Rewrite Layout Shell (AppTopbar, AppSidebar, AppFooter, AppLayout)

**Files:**
- Modify: `src/app/layout/component/app-topbar.ts`
- Modify: `src/app/layout/component/app-sidebar.ts`
- Modify: `src/app/layout/component/app-footer.ts`
- Modify: `src/app/layout/component/app-layout.ts`

- [ ] **Step 1: Rewrite AppTopbar with Sakai-NG design**

Replace the current topbar with Sakai-NG's version. Key elements: hamburger toggle, SVG logo, dark mode button, palette configurator trigger, user menu dropdown, responsive ellipsis menu.

```typescript
import { Component, computed, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { CommonModule } from '@angular/common';
import { StyleClassModule } from 'primeng/styleclass';
import { AppConfigurator } from './app.configurator';
import { LayoutService } from '../service/layout.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-topbar',
  standalone: true,
  imports: [RouterModule, CommonModule, StyleClassModule, AppConfigurator],
  templateUrl: './app-topbar.html',
})
export class AppTopbar {
  layoutService = inject(LayoutService);
  authService = inject(AuthService);

  user = computed(() => this.authService.user());

  toggleDarkMode() {
    this.layoutService.layoutConfig.update((state) => ({
      ...state,
      darkTheme: !state.darkTheme,
    }));
  }

  onLogout() {
    this.authService.logout();
  }
}
```

- [ ] **Step 2: Create AppTopbar template** (`app-topbar.html`)

```html
<div class="layout-topbar">
  <div class="layout-topbar-logo-container">
    <button class="layout-menu-button layout-topbar-action" (click)="layoutService.onMenuToggle()">
      <i class="pi pi-bars"></i>
    </button>
    <a class="layout-topbar-logo" routerLink="/">
      <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg" class="w-8 h-8">
        <rect width="40" height="40" rx="8" fill="var(--primary-color)" />
        <text x="20" y="26" text-anchor="middle" fill="white" font-size="18" font-weight="bold" font-family="Arial">G</text>
      </svg>
      <span class="font-bold text-xl ml-2">Glacier</span>
    </a>
  </div>

  <div class="layout-topbar-actions">
    <div class="layout-config-menu">
      <button type="button" class="layout-topbar-action" (click)="toggleDarkMode()">
        <i [ngClass]="{ 'pi': true, 'pi-moon': layoutService.isDarkTheme(), 'pi-sun': !layoutService.isDarkTheme() }"></i>
      </button>
      <div class="relative">
        <button
          class="layout-topbar-action layout-topbar-action-highlight"
          pStyleClass="@next"
          enterFromClass="hidden"
          enterActiveClass="animate-scalein"
          leaveToClass="hidden"
          leaveActiveClass="animate-fadeout"
          [hideOnOutsideClick]="true"
        >
          <i class="pi pi-palette"></i>
        </button>
        <app-configurator />
      </div>
    </div>

    <button
      class="layout-topbar-menu-button layout-topbar-action"
      pStyleClass="@next"
      enterFromClass="hidden"
      enterActiveClass="animate-scalein"
      leaveToClass="hidden"
      leaveActiveClass="animate-fadeout"
      [hideOnOutsideClick]="true"
    >
      <i class="pi pi-ellipsis-v"></i>
    </button>

    <div class="layout-topbar-menu hidden lg:block">
      <div class="layout-topbar-menu-content">
        <a pButton class="p-button-text" routerLink="/products" *ngIf="!authService.user()?.role || authService.user()?.role === 'CUSTOMER'">
          <i class="pi pi-th-large"></i>
          <span>Products</span>
        </a>
        <a pButton class="p-button-text" routerLink="/cart" *ngIf="authService.user()?.role === 'CUSTOMER'">
          <i class="pi pi-shopping-cart"></i>
          <span>Cart</span>
        </a>
        @if (user()) {
          <button type="button" class="layout-topbar-action" (click)="onLogout()">
            <i class="pi pi-sign-out"></i>
            <span>Logout</span>
          </button>
        } @else {
          <a pButton class="p-button-text" routerLink="/login">
            <i class="pi pi-sign-in"></i>
            <span>Sign In</span>
          </a>
        }
      </div>
    </div>
  </div>
</div>
```

Note: The `*ngIf` directives above use `CommonModule`. For a cleaner approach with Angular 17+ control flow, we could use `@if`, but the `a` + `pButton` combination with `*ngIf` is fine.

- [ ] **Step 3: Rewrite AppSidebar with outside-click handling**

```typescript
import { Component, effect, ElementRef, inject, OnDestroy, OnInit } from '@angular/core';
import { NavigationEnd, Router, RouterModule } from '@angular/router';
import { filter, Subject, takeUntil } from 'rxjs';
import { AppMenu } from './app.menu';
import { LayoutService } from '../service/layout.service';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [AppMenu, RouterModule],
  template: `<div class="layout-sidebar"><app-menu></app-menu></div>`,
})
export class AppSidebar implements OnInit, OnDestroy {
  layoutService = inject(LayoutService);
  router = inject(Router);
  el = inject(ElementRef);

  private outsideClickListener: ((event: MouseEvent) => void) | null = null;
  private destroy$ = new Subject<void>();

  constructor() {
    effect(() => {
      const state = this.layoutService.layoutState();
      if (this.layoutService.isDesktop()) {
        if (state.overlayMenuActive) this.bindOutsideClickListener();
        else this.unbindOutsideClickListener();
      } else {
        if (state.mobileMenuActive) this.bindOutsideClickListener();
        else this.unbindOutsideClickListener();
      }
    });
  }

  ngOnInit() {
    this.router.events
      .pipe(filter((event) => event instanceof NavigationEnd), takeUntil(this.destroy$))
      .subscribe(() => this.onRouteChange(this.router.url));
    this.onRouteChange(this.router.url);
  }

  ngOnDestroy() {
    this.destroy$.next();
    this.destroy$.complete();
    this.unbindOutsideClickListener();
  }

  private onRouteChange(path: string) {
    this.layoutService.layoutState.update((val) => ({
      ...val,
      activePath: path,
      overlayMenuActive: false,
      mobileMenuActive: false,
      menuHoverActive: false,
    }));
  }

  private bindOutsideClickListener() {
    if (!this.outsideClickListener) {
      this.outsideClickListener = (event: MouseEvent) => {
        if (this.isOutsideClicked(event)) {
          this.layoutService.layoutState.update((val) => ({
            ...val,
            overlayMenuActive: false,
            mobileMenuActive: false,
            menuHoverActive: false,
          }));
        }
      };
      document.addEventListener('click', this.outsideClickListener);
    }
  }

  private unbindOutsideClickListener() {
    if (this.outsideClickListener) {
      document.removeEventListener('click', this.outsideClickListener);
      this.outsideClickListener = null;
    }
  }

  private isOutsideClicked(event: MouseEvent): boolean {
    const sidebarEl = this.el.nativeElement;
    return !(
      sidebarEl?.isSameNode(event.target as Node) ||
      sidebarEl?.contains(event.target as Node)
    );
  }
}
```

- [ ] **Step 4: Rewrite AppFooter**

```typescript
import { Component } from '@angular/core';

@Component({
  selector: 'app-footer',
  standalone: true,
  template: `
    <div class="layout-footer">
      Glacier Commerce by
      <a href="https://primeng.org" target="_blank" rel="noopener noreferrer" class="text-primary font-bold hover:underline">PrimeNG</a>
    </div>
  `,
})
export class AppFooter {}
```

- [ ] **Step 5: Rewrite AppLayout shell**

```typescript
import { Component, computed, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AppTopbar } from './app-topbar';
import { AppSidebar } from './app-sidebar';
import { AppFooter } from './app-footer';
import { LayoutService } from '../service/layout.service';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [CommonModule, AppTopbar, AppSidebar, RouterModule, AppFooter],
  template: `
    <div class="layout-wrapper" [ngClass]="containerClass()">
      <app-topbar></app-topbar>
      <app-sidebar></app-sidebar>
      <div class="layout-main-container">
        <div class="layout-main">
          <router-outlet></router-outlet>
        </div>
        <app-footer></app-footer>
      </div>
      @if (isOverlayActive()) {
        <div class="layout-mask" (click)="layoutService.onMenuToggle()"></div>
      }
    </div>
  `,
})
export class AppLayout {
  layoutService = inject(LayoutService);

  constructor() {
    effect(() => {
      const state = this.layoutService.layoutState();
      document.body.classList.toggle('blocked-scroll', !!state.mobileMenuActive);
    });
  }

  containerClass = computed(() => {
    const config = this.layoutService.layoutConfig();
    const state = this.layoutService.layoutState();
    return {
      'layout-overlay': config.menuMode === 'overlay',
      'layout-static': config.menuMode === 'static',
      'layout-static-inactive': state.staticMenuDesktopInactive && config.menuMode === 'static',
      'layout-overlay-active': state.overlayMenuActive,
      'layout-mobile-active': state.mobileMenuActive,
    };
  });

  isOverlayActive = computed(
    () => this.layoutService.layoutState().overlayMenuActive || this.layoutService.layoutState().mobileMenuActive
  );
}
```

- [ ] **Step 6: Verify TypeScript compiles**

Run: `npx tsc --noEmit`
Expected: clean exit

- [ ] **Step 7: Commit**

```bash
git add frontend/src/app/layout/component/app-topbar.ts frontend/src/app/layout/component/app-topbar.html frontend/src/app/layout/component/app-sidebar.ts frontend/src/app/layout/component/app-footer.ts frontend/src/app/layout/component/app-layout.ts
git commit -m "feat: rewrite layout shell with Sakai-NG components"
```

---

### Task 6: Add Sakai-NG layout CSS to styles.css

**Files:**
- Modify: `src/styles.css`

- [ ] **Step 1: Append Sakai-NG layout CSS variables and classes to styles.css**

The Sakai-NG layout relies on custom CSS classes (`.layout-wrapper`, `.layout-sidebar`, `.layout-topbar`, `.layout-main-container`, `.layout-main`, `.layout-footer`, `.layout-mask`) and CSS variables for spacing. Add these to the global stylesheet.

```css
/* ===== Sakai-NG Layout Styles ===== */

.layout-wrapper {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.layout-topbar {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  z-index: 1000;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 2rem;
  background: var(--p-surface-0);
  border-bottom: 1px solid var(--p-surface-200);
}

:root.app-dark .layout-topbar {
  background: var(--p-surface-900);
  border-bottom: 1px solid var(--p-surface-700);
}

.layout-topbar-logo-container {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.layout-topbar-logo {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  text-decoration: none;
  color: var(--p-text-color);
}

.layout-menu-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 0.5rem;
  border: none;
  background: transparent;
  color: var(--p-text-muted-color);
  cursor: pointer;
  transition: background 0.2s;
}

.layout-menu-button:hover {
  background: var(--p-surface-100);
}

:root.app-dark .layout-menu-button:hover {
  background: var(--p-surface-800);
}

.layout-topbar-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.layout-config-menu {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.layout-topbar-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  border-radius: 0.5rem;
  border: none;
  background: transparent;
  color: var(--p-text-muted-color);
  cursor: pointer;
  transition: background 0.2s;
}

.layout-topbar-action:hover {
  background: var(--p-surface-100);
}

:root.app-dark .layout-topbar-action:hover {
  background: var(--p-surface-800);
}

.layout-topbar-menu {
  position: relative;
}

.layout-topbar-menu-content {
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.layout-topbar-menu-button {
  display: none;
}

@media (max-width: 991px) {
  .layout-topbar-menu-button {
    display: flex;
  }
}

.layout-sidebar {
  position: fixed;
  top: 64px;
  left: 0;
  width: 260px;
  height: calc(100vh - 64px);
  z-index: 999;
  background: var(--p-surface-0);
  border-right: 1px solid var(--p-surface-200);
  overflow-y: auto;
  transition: transform 0.3s, left 0.3s;
}

:root.app-dark .layout-sidebar {
  background: var(--p-surface-900);
  border-right: 1px solid var(--p-surface-700);
}

.layout-overlay .layout-sidebar {
  transform: translateX(-100%);
}

.layout-overlay.layout-overlay-active .layout-sidebar {
  transform: translateX(0);
}

.layout-static .layout-sidebar {
  transform: translateX(0);
}

.layout-static.layout-static-inactive .layout-sidebar {
  transform: translateX(-100%);
}

.layout-main-container {
  margin-left: 260px;
  margin-top: 64px;
  min-height: calc(100vh - 64px);
  display: flex;
  flex-direction: column;
  transition: margin-left 0.3s;
}

.layout-overlay .layout-main-container {
  margin-left: 0;
}

.layout-static.layout-static-inactive .layout-main-container {
  margin-left: 0;
}

.layout-main {
  flex: 1;
  padding: 2rem;
}

.layout-footer {
  padding: 1.5rem 2rem;
  border-top: 1px solid var(--p-surface-200);
  text-align: center;
  color: var(--p-text-muted-color);
  font-size: 0.875rem;
}

:root.app-dark .layout-footer {
  border-top: 1px solid var(--p-surface-700);
}

.layout-mask {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 998;
  background: rgba(0, 0, 0, 0.4);
}

@media (max-width: 991px) {
  .layout-sidebar {
    transform: translateX(-100%);
    z-index: 1001;
  }

  .layout-mobile-active .layout-sidebar {
    transform: translateX(0);
  }

  .layout-main-container {
    margin-left: 0;
  }

  .layout-main {
    padding: 1rem;
  }
}

body.blocked-scroll {
  overflow: hidden;
}

/* Menu Styles */
.layout-menu {
  list-style: none;
  padding: 1rem 0;
  margin: 0;
}

.layout-menuitem-root-text {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--p-text-muted-color);
  padding: 0.5rem 1.25rem;
  margin-top: 0.5rem;
}

.layout-menu a {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.65rem 1.25rem;
  color: var(--p-text-color);
  text-decoration: none;
  border-radius: 0;
  transition: background 0.2s, color 0.2s;
  cursor: pointer;
  border: none;
  width: 100%;
  text-align: left;
  font-size: 0.875rem;
}

.layout-menu a:hover {
  background: var(--p-surface-100);
}

:root.app-dark .layout-menu a:hover {
  background: var(--p-surface-800);
}

.layout-menu a.active-route {
  color: var(--p-primary-color);
  font-weight: 600;
}

.layout-menuitem-icon {
  width: 1.25rem;
  text-align: center;
  font-size: 0.875rem;
}

.layout-submenu-toggler {
  margin-left: auto;
  font-size: 0.75rem;
  transition: transform 0.2s;
}

.active-menuitem > a .layout-submenu-toggler {
  transform: rotate(-180deg);
}

.layout-menu ul {
  list-style: none;
  padding: 0;
  margin: 0;
  overflow: hidden;
}

.layout-root-menuitem > ul {
  max-height: 0;
  overflow: hidden;
  transition: max-height 0.45s cubic-bezier(0.86, 0, 0.07, 1);
}

.layout-root-menuitem.active-menuitem > ul {
  max-height: 1000px;
}

.menu-separator {
  height: 1px;
  background: var(--p-surface-200);
  margin: 0.5rem 1.25rem;
}

:root.app-dark .menu-separator {
  background: var(--p-surface-700);
}

@media (min-width: 992px) {
  .layout-overlay .layout-sidebar {
    transform: translateX(-100%);
  }
  .layout-overlay.layout-overlay-active .layout-sidebar {
    transform: translateX(0);
  }
}
```

- [ ] **Step 2: Verify build**

Run: `npx ng build --configuration development`
Expected: build succeeds

- [ ] **Step 3: Commit**

```bash
git add frontend/src/styles.css
git commit -m "feat: add Sakai-NG layout CSS variables and classes"
```

---

### Task 7: Rewrite standalone pages (Login, Register, Not Found)

**Files:**
- Modify: `src/app/pages/login/login.ts`, `src/app/pages/login/login.html`
- Modify: `src/app/pages/register/register.ts`, `src/app/pages/register/register.html`
- Modify: `src/app/pages/not-found/not-found.ts`

- [ ] **Step 1: Add AppFloatingConfigurator import to Login and Register**

In both `login.ts` and `register.ts`, import `AppFloatingConfigurator` and add it to the template:

```typescript
// In login.ts, register.ts — add to imports:
import { AppFloatingConfigurator } from '../../layout/component/app-floating-configurator';

// Add to @Component.imports: AppFloatingConfigurator
```

Add to template (login.html, register.html), inside the page wrapper but before the main content:

```html
<app-floating-configurator />
```

- [ ] **Step 2: Redesign Login template with Sakai-NG auth page style**

Replace `login.html` content with a centered card layout:

```html
<div class="min-h-screen flex items-center justify-center bg-surface-50 dark:bg-surface-950 p-4">
  <app-floating-configurator />
  <div class="w-full max-w-md">
    <div class="text-center mb-8">
      <div class="flex items-center justify-center gap-3 mb-4">
        <div class="w-10 h-10 rounded-lg bg-primary flex items-center justify-center">
          <span class="text-white font-bold text-xl">G</span>
        </div>
        <span class="text-2xl font-bold text-surface-900 dark:text-surface-0">Glacier</span>
      </div>
      <h1 class="text-2xl font-bold text-surface-900 dark:text-surface-0">Sign in to your account</h1>
      <p class="text-surface-500 mt-2">Enter your credentials to continue</p>
    </div>

    @if (errorMessage) {
      <div class="p-4 mb-4 rounded-lg bg-red-500/10 border border-red-500/20 text-red-500 text-sm font-medium">
        <i class="pi pi-exclamation-circle mr-2"></i>
        {{ errorMessage }}
      </div>
    }

    <p-card>
      <div class="space-y-5">
        <div>
          <label class="block text-sm font-medium text-surface-900 dark:text-surface-0 mb-1">Email</label>
          <input pInputText type="email" placeholder="you@example.com" [(ngModel)]="email" class="w-full" />
        </div>
        <div>
          <label class="block text-sm font-medium text-surface-900 dark:text-surface-0 mb-1">Password</label>
          <input pInputText type="password" placeholder="••••••••" [(ngModel)]="password" class="w-full" />
        </div>
        <button pButton class="w-full" (click)="onLogin()" [loading]="isSubmitting" label="Sign In"></button>
      </div>
    </p-card>

    <p class="text-center text-sm text-surface-500 mt-6">
      Don't have an account?
      <a routerLink="/register" class="text-primary font-semibold hover:underline">Create one</a>
    </p>
  </div>
</div>
```

- [ ] **Step 3: Redesign Register template with same pattern**

Apply the same centered card pattern as login, with Buyer/Seller toggle as `p-selectbutton`:

```html
<div class="min-h-screen flex items-center justify-center bg-surface-50 dark:bg-surface-950 p-4">
  <app-floating-configurator />
  <div class="w-full max-w-md">
    <div class="text-center mb-8">
      <div class="flex items-center justify-center gap-3 mb-4">
        <div class="w-10 h-10 rounded-lg bg-primary flex items-center justify-center">
          <span class="text-white font-bold text-xl">G</span>
        </div>
        <span class="text-2xl font-bold text-surface-900 dark:text-surface-0">Glacier</span>
      </div>
      <h1 class="text-2xl font-bold text-surface-900 dark:text-surface-0">Create your account</h1>
      <p class="text-surface-500 mt-2">Join Glacier Commerce today</p>
    </div>

    @if (errorMessage) {
      <div class="p-4 mb-4 rounded-lg bg-red-500/10 border border-red-500/20 text-red-500 text-sm font-medium">
        <i class="pi pi-exclamation-circle mr-2"></i>
        {{ errorMessage }}
      </div>
    }

    <p-card>
      <div class="space-y-5">
        <div>
          <label class="block text-sm font-medium text-surface-900 dark:text-surface-0 mb-1">Full Name</label>
          <input pInputText type="text" placeholder="John Doe" [(ngModel)]="fullName" class="w-full" />
        </div>
        <div>
          <label class="block text-sm font-medium text-surface-900 dark:text-surface-0 mb-1">Email</label>
          <input pInputText type="email" placeholder="you@example.com" [(ngModel)]="email" class="w-full" />
        </div>
        <div>
          <label class="block text-sm font-medium text-surface-900 dark:text-surface-0 mb-1">Password</label>
          <input pInputText type="password" placeholder="••••••••" [(ngModel)]="password" class="w-full" />
        </div>
        <div>
          <label class="block text-sm font-medium text-surface-900 dark:text-surface-0 mb-1">I want to</label>
          <p-selectbutton [options]="roleOptions" [(ngModel)]="selectedRole" [allowEmpty]="false" class="w-full" />
        </div>
        <button pButton class="w-full" (click)="onRegister()" [loading]="isSubmitting" label="Create Account"></button>
      </div>
    </p-card>

    <p class="text-center text-sm text-surface-500 mt-6">
      Already have an account?
      <a routerLink="/login" class="text-primary font-semibold hover:underline">Sign in</a>
    </p>
  </div>
</div>
```

In `register.ts`, add:
```typescript
roleOptions = [
  { label: 'Buy', icon: 'pi pi-shopping-cart', value: 'CUSTOMER' },
  { label: 'Sell', icon: 'pi pi-briefcase', value: 'SELLER' },
];
selectedRole = 'CUSTOMER';
```

Also import `SelectButtonModule` from `primeng/selectbutton`.

- [ ] **Step 4: Redesign Not Found page**

```typescript
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [RouterLink, ButtonModule],
  template: `
    <div class="min-h-screen flex flex-col items-center justify-center bg-surface-50 dark:bg-surface-950 p-4">
      <div class="text-center">
        <div class="text-8xl font-bold text-surface-300 dark:text-surface-700 mb-4">404</div>
        <i class="pi pi-exclamation-circle text-4xl text-primary mb-4 block"></i>
        <h1 class="text-2xl font-bold text-surface-900 dark:text-surface-0 mb-2">Page Not Found</h1>
        <p class="text-surface-500 mb-6">The page you're looking for doesn't exist or has been moved.</p>
        <a pButton routerLink="/" label="Go to Home"></a>
      </div>
    </div>
  `,
})
export class NotFound {}
```

- [ ] **Step 5: Verify build**

Run: `npx ng build --configuration development`
Expected: build succeeds

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/login/ frontend/src/app/pages/register/ frontend/src/app/pages/not-found/
git commit -m "feat: redesign standalone pages with Sakai-NG auth/error patterns"
```

---

### Task 8: Redesign Home page

**Files:**
- Modify: `src/app/pages/home/home.html`
- Modify: `src/app/pages/home/home.ts`

- [ ] **Step 1: Read current home.html to understand the structure**

Run: `cat frontend/src/app/pages/home/home.html`

- [ ] **Step 2: Replace home.html with Sakai-NG styled template**

Apply Sakai-NG design patterns:
- Hero: uses `text-5xl md:text-6xl font-bold tracking-tight text-surface-900 dark:text-surface-0` for heading, `text-surface-500` for subtitle, `pButton` primary CTAs
- Product cards: wrap each in a div with `bg-surface-0 dark:bg-surface-900 rounded-xl shadow-sm border border-surface-200 dark:border-surface-700 overflow-hidden hover:shadow-md transition-shadow`
- Section headings: `text-2xl font-bold text-surface-900 dark:text-surface-0 tracking-tight`
- Newsletter CTA: container with `bg-primary-50 dark:bg-primary-950/30 rounded-2xl p-8 md:p-12`

Example product card pattern:
```html
<div class="bg-surface-0 dark:bg-surface-900 rounded-xl shadow-sm border border-surface-200 dark:border-surface-700 overflow-hidden hover:shadow-md transition-shadow">
  <div class="aspect-square bg-surface-100 dark:bg-surface-800">
    <img [src]="..." class="w-full h-full object-cover" />
  </div>
  <div class="p-4">
    <h3 class="font-semibold text-surface-900 dark:text-surface-0 truncate">Product Name</h3>
    <p class="text-sm text-surface-500 mt-1">$99.99</p>
  </div>
</div>
```

- [ ] **Step 3: Verify build**

Run: `npx ng build --configuration development`
Expected: build succeeds

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/pages/home/
git commit -m "feat: redesign Home page with Sakai-NG styling"
```

---

### Task 9: Redesign Product Listing and Product Details pages

**Files:**
- Modify: `src/app/pages/product-listing/product-listing.html`
- Modify: `src/app/pages/product-details/product-details.html`

- [ ] **Step 1: Add Sakai-NG card wrapper classes to Product Listing**

Wrap the filter sidebar sections in elements with `bg-surface-0 dark:bg-surface-900 rounded-xl shadow-sm border border-surface-200 dark:border-surface-700 p-4`. Apply `text-surface-900 dark:text-surface-0` on all headings and `text-surface-500` on labels/descriptions.

Product cards in the grid should use:
```html
<div class="bg-surface-0 dark:bg-surface-900 rounded-xl shadow-sm border border-surface-200 dark:border-surface-700 overflow-hidden hover:shadow-md transition-all duration-200">
  <div class="aspect-square bg-surface-100 dark:bg-surface-800">
    <img [src]="..." class="w-full h-full object-cover" />
  </div>
  <div class="p-4 space-y-1">
    <p class="font-semibold text-surface-900 dark:text-surface-0 truncate">{{ product.name }}</p>
    <p class="text-sm text-surface-500">${{ product.price }}</p>
    <button pButton class="w-full mt-2" (click)="..." label="Add to Cart" icon="pi pi-shopping-cart"></button>
  </div>
</div>
```

Search input: use `pInputText class="w-full"` with a wrapper that has `pi pi-search` icon overlay.

- [ ] **Step 2: Verify build**

Run: `npx ng build --configuration development`

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/product-listing/
git commit -m "feat: redesign Product Listing with Sakai-NG card styling"
```

- [ ] **Step 4: Redesign Product Details**

Apply Sakai-NG patterns:
- Image gallery: main image in `bg-surface-0 dark:bg-surface-900 rounded-xl shadow-sm border border-surface-200 dark:border-surface-700 overflow-hidden`
- Product title: `text-2xl md:text-3xl font-bold text-surface-900 dark:text-surface-0 tracking-tight`
- Price: `text-2xl font-bold text-primary`
- Description card: `<p-card>` wrapper
- Add to cart button: `<button pButton size="large" icon="pi pi-shopping-cart" label="Add to Cart"></button>`

- [ ] **Step 5: Verify build**

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/product-details/
git commit -m "feat: redesign Product Details with Sakai-NG styling"
```

---

### Task 10: Redesign Shopping Cart and Checkout pages

**Files:**
- Modify: `src/app/pages/shopping-cart/shopping-cart.html`
- Modify: `src/app/pages/checkout/checkout.html`
- Modify: `src/app/pages/order-success/order-success.ts`, `.html`

- [ ] **Step 1: Redesign Shopping Cart**

Cart items: each row in a div with:
```
bg-surface-0 dark:bg-surface-900 rounded-xl shadow-sm border border-surface-200 dark:border-surface-700 p-4
```

Order summary: wrap in `<p-card header="Order Summary">`. Use `text-surface-900 dark:text-surface-0` for labels and `text-surface-500` for secondary text. Use `text-xl font-bold` for the total.

Buttons: `<button pButton label="Update Cart" class="p-button-outlined">`, `<button pButton label="Proceed to Checkout">`

- [ ] **Step 2: Verify build**

- [ ] **Step 3: Redesign Checkout**

Shipping and Payment sections: each in `<p-card header="...">`. Inputs use `pInputText w-full`. Place order button: `<button pButton label="Place Order" class="w-full" size="large" icon="pi pi-lock">`

Order summary sidebar: `<p-card header="Order Summary">` with line items.

- [ ] **Step 4: Redesign Order Success**

```html
<div class="max-w-2xl mx-auto py-16 px-4">
  <p-card>
    <div class="text-center py-8">
      <div class="w-16 h-16 bg-emerald-500/10 rounded-full flex items-center justify-center mx-auto mb-4">
        <i class="pi pi-check-circle text-3xl text-emerald-500"></i>
      </div>
      <h1 class="text-2xl font-bold text-surface-900 dark:text-surface-0 mb-2">Order Confirmed!</h1>
      <p class="text-surface-500 mb-6">Your order #{{ orderId }} has been placed.</p>
      <a pButton routerLink="/" label="Continue Shopping"></a>
    </div>
  </p-card>
</div>
```

- [ ] **Step 5: Verify build**

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/pages/shopping-cart/ frontend/src/app/pages/checkout/ frontend/src/app/pages/order-success/
git commit -m "feat: redesign Cart, Checkout, Order Success with Sakai-NG styling"
```

---

### Task 11: Redesign My Account page

**Files:**
- Modify: `src/app/pages/my-account/my-account.html`

- [ ] **Step 1: Add Sakai-NG card wrappers**

Profile info: `<p-card header="Profile">` with avatar, name, email displayed using `text-surface-900 dark:text-surface-0` for values and `text-surface-500 text-sm` for labels.

Order history table: wrap in `<p-card header="Order History">`. PrimeNG table gets styled automatically by the theme. Add `class="p-datatable-striped"` for stripe effect.

- [ ] **Step 2: Verify build**

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/my-account/
git commit -m "feat: redesign My Account with Sakai-NG styling"
```

---

### Task 12: Redesign Seller Dashboard and Inventory pages

**Files:**
- Modify: `src/app/pages/seller-dashboard/seller-dashboard.html`
- Modify: `src/app/pages/inventory-management/inventory-management.html`

- [ ] **Step 1: Redesign Seller Dashboard metric cards**

Each metric card pattern:
```html
<div class="bg-surface-0 dark:bg-surface-900 rounded-xl shadow-sm border border-surface-200 dark:border-surface-700 p-6">
  <div class="flex items-center justify-between mb-4">
    <span class="text-surface-500 text-sm font-medium">{{ label }}</span>
    <div class="w-10 h-10 rounded-lg bg-primary-50 dark:bg-primary-950/30 flex items-center justify-center">
      <i class="pi {{ icon }} text-primary"></i>
    </div>
  </div>
  <p class="text-3xl font-bold text-surface-900 dark:text-surface-0">{{ value }}</p>
  <p class="text-sm text-surface-500 mt-1">{{ trend }}</p>
</div>
```

- [ ] **Step 2: Wrap low-stock alerts in styled notification cards**

Use `<p-card>` or a div with `bg-red-500/5 border border-red-500/20 rounded-xl p-4` pattern.

- [ ] **Step 3: Redesign Inventory table**

Wrap in `<p-card header="Inventory">`. Use PrimeNG `<p-table>` with `[stripedRows]="true"`. Search input: `pInputText` with icon wrapper.

- [ ] **Step 4: Verify build**

- [ ] **Step 5: Commit**

```bash
git add frontend/src/app/pages/seller-dashboard/ frontend/src/app/pages/inventory-management/
git commit -m "feat: redesign Seller pages with Sakai-NG styling"
```

---

### Task 13: Redesign Add Product and Edit Product pages

**Files:**
- Modify: `src/app/pages/add-product/add-product.html`
- Modify: `src/app/pages/edit-product/edit-product.html`

- [ ] **Step 1: Apply Sakai-NG card wrappers to forms**

Form sections use `<p-card header="Basic Information">`, `<p-card header="Pricing Details">`, `<p-card header="Inventory Settings">`, etc. Each input uses `pInputText w-full` with label pattern `text-sm font-medium text-surface-900 dark:text-surface-0 mb-1`.

Buttons: `<button pButton label="Save" class="w-full sm:w-auto">` for mobile-responsive width.

- [ ] **Step 2: Verify build**

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/pages/add-product/ frontend/src/app/pages/edit-product/
git commit -m "feat: redesign Add/Edit Product forms with Sakai-NG styling"
```

---

### Task 14: Redesign Admin Dashboard and Admin Users pages

**Files:**
- Modify: `src/app/pages/admin-dashboard/admin-dashboard.html`
- Modify: `src/app/pages/admin-users/admin-users.html`

- [ ] **Step 1: Redesign Admin Dashboard metric cards**

Same Sakai-NG StatsWidget pattern as Task 12. Revenue chart: keep PrimeNG `<p-chart>` wrapped in `<p-card header="Revenue">`.

- [ ] **Step 2: Redesign Admin Users table**

Wrap in `<p-card header="User Management">`. Role badges pattern:
```html
<span class="px-2 py-0.5 rounded-full text-xs font-medium"
  [ngClass]="{
    'bg-emerald-500/10 text-emerald-500': user.role === 'CUSTOMER',
    'bg-sky-500/10 text-sky-500': user.role === 'SELLER',
    'bg-purple-500/10 text-purple-500': user.role === 'ADMIN'
  }">
  {{ user.role }}
</span>
```

- [ ] **Step 3: Verify build**

- [ ] **Step 4: Commit**

```bash
git add frontend/src/app/pages/admin-dashboard/ frontend/src/app/pages/admin-users/
git commit -m "feat: redesign Admin pages with Sakai-NG styling"
```

---

### Task 15: Add missing edit-product route

**Files:**
- Modify: `src/app/app.routes.ts`

- [ ] **Step 1: Add edit-product route**

In `src/app/app.routes.ts`, add the route between `add-product` and the admin section:

```typescript
{ path: 'seller/edit-product', component: EditProduct, canActivate: [authGuard, roleGuard], data: { roles: ['ROLE_SELLER'] } },
```

Also verify the import for `EditProduct` is present.

- [ ] **Step 2: Verify build**

- [ ] **Step 3: Commit**

```bash
git add frontend/src/app/app.routes.ts
git commit -m "fix: add missing edit-product route"
```

---

### Task 16: Clean up and final build verification

**Files:**
- Remove cloned Sakai-NG repo

- [ ] **Step 1: Remove cloned Sakai-NG repo**

```bash
rm -rf /var/folders/8x/frmzltvx7j9g4s48mnhmshkr0000gn/T/opencode/sakai-ng
```

- [ ] **Step 2: Run final build verification**

```bash
npx tsc --noEmit && npx ng build --configuration development
```

Expected: clean exit, bundle written to `dist/`

- [ ] **Step 3: If build passes, commit any remaining changes**

```bash
git add -A
git commit -m "chore: final cleanup after Sakai-NG redesign"
```
