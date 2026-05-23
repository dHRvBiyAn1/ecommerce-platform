# Angular Ecommerce Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an MVP Angular 21 standalone ecommerce frontend with dark/light theme, reusable components, signals-based state management, and high design quality.

**Architecture:** Feature-based folder structure with lazy-loaded routes. Core singleton services for auth/cart/API. Shared presentational components. Theme driven by CSS custom properties in a single `_variables.scss` parent file. Signals for reactive state.

**Design Direction:** Clean conversion-optimized marketplace look. Search-first navigation. Category discovery. Trust signals (badges, reviews). Bold color accents (cyan/teal primary with warm accents). Typography: Rubik (headings) + Nunito Sans (body) — distinctive, not generic. Smooth micro-interactions (200-300ms).

**Tech Stack:** Angular 21 standalone, TypeScript 5, RxJS + Signals, Tailwind CSS 3, Angular Material

**Backend:** Spring Boot microservices via API Gateway at `http://localhost:8080`

---

### Task 1: Scaffold Angular Project

**Files:**
- Create: `frontend/ecommerce-app/` (entire project scaffold)

- [ ] **Step 1: Create Angular project**

```bash
cd /Users/dhruv/Developer/ecommerce-platform/frontend
ng new ecommerce-app --standalone --routing --style=scss --ssr=false --directory=./ecommerce-app --skip-git
```

Expected: Project scaffolded, `npm install` completes.

- [ ] **Step 2: Verify scaffold**

```bash
ls frontend/ecommerce-app/src/app/
```

Expected: `app.component.ts`, `app.config.ts`, `app.routes.ts` exist.

---

### Task 2: Install and Configure Dependencies

- [ ] **Step 1: Install Tailwind CSS, Material, icons**

```bash
cd /Users/dhruv/Developer/ecommerce-platform/frontend/ecommerce-app
npm install -D tailwindcss @tailwindcss/postcss postcss
npm install @angular/material @angular/cdk
```

- [ ] **Step 2: Create `tailwind.config.ts`**

```typescript
import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./src/**/*.{html,ts}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        primary: 'var(--color-primary)',
        'primary-hover': 'var(--color-primary-hover)',
        secondary: 'var(--color-secondary)',
        accent: 'var(--color-accent)',
        surface: 'var(--color-surface)',
        'surface-alt': 'var(--color-surface-alt)',
        'surface-elevated': 'var(--color-surface-elevated)',
        'text-primary': 'var(--color-text)',
        'text-secondary': 'var(--color-text-secondary)',
        border: 'var(--color-border)',
        success: 'var(--color-success)',
        warning: 'var(--color-warning)',
        error: 'var(--color-error)',
      },
      fontFamily: {
        heading: ['Rubik', 'sans-serif'],
        body: ['Nunito Sans', 'sans-serif'],
      },
      borderRadius: {
        sm: 'var(--border-radius-sm)',
        DEFAULT: 'var(--border-radius)',
        lg: 'var(--border-radius-lg)',
        xl: 'var(--border-radius-xl)',
      },
      boxShadow: {
        sm: 'var(--shadow-sm)',
        DEFAULT: 'var(--shadow)',
        lg: 'var(--shadow-lg)',
        xl: 'var(--shadow-xl)',
      },
      animation: {
        'fade-in': 'fadeIn 0.3s ease-out',
        'slide-up': 'slideUp 0.3s ease-out',
        'scale-in': 'scaleIn 0.2s ease-out',
      },
      keyframes: {
        fadeIn: { '0%': { opacity: '0' }, '100%': { opacity: '1' } },
        slideUp: { '0%': { transform: 'translateY(10px)', opacity: '0' }, '100%': { transform: 'translateY(0)', opacity: '1' } },
        scaleIn: { '0%': { transform: 'scale(0.95)', opacity: '0' }, '100%': { transform: 'scale(1)', opacity: '1' } },
      },
    },
  },
  plugins: [],
};

export default config;
```

- [ ] **Step 3: Create `postcss.config.js`**

```javascript
module.exports = { plugins: { '@tailwindcss/postcss': {} } };
```

- [ ] **Step 4: Add font links to `src/index.html`**

Inside `<head>`:
```html
<link rel="preconnect" href="https://fonts.googleapis.com" />
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
<link href="https://fonts.googleapis.com/css2?family=Rubik:wght@400;500;600;700&family=Nunito+Sans:wght@300;400;500;600;700&display=swap" rel="stylesheet" />
<link href="https://fonts.googleapis.com/icon?family=Material+Icons" rel="stylesheet" />
```

- [ ] **Step 5: Verify build**

```bash
npx ng build
```

- [ ] **Step 6: Commit**

```bash
git add frontend/ecommerce-app/
git commit -m "feat: scaffold Angular 21 project with Tailwind, Material, Rubik+Nunito fonts"
```

---

### Task 3: Create Theme System (Parent Files)

**Design system:** Cyan primary (#0891B2), cyan light secondary (#22D3EE), green accent (#22C55E) for CTAs. Light bg (#ECFEFF), dark bg (#0F172A). Typography: Rubik headings + Nunito Sans body.

**Files:**
- Create: `src/styles/_variables.scss`
- Create: `src/styles/_theme.scss`
- Modify: `src/styles/styles.scss`
- Modify: `angular.json`

- [ ] **Step 1: Create `src/styles/_variables.scss`**

```scss
:root {
  --color-primary: #0891b2;
  --color-primary-hover: #0e7490;
  --color-primary-light: #ecfeff;
  --color-secondary: #22d3ee;
  --color-accent: #22c55e;
  --color-accent-hover: #16a34a;
  --color-surface: #ffffff;
  --color-surface-alt: #f0f9ff;
  --color-surface-elevated: #ffffff;
  --color-text: #164e63;
  --color-text-secondary: #64748b;
  --color-border: #cbd5e1;
  --color-success: #22c55e;
  --color-warning: #f59e0b;
  --color-error: #ef4444;
  --color-info: #0891b2;
  --font-heading: 'Rubik', sans-serif;
  --font-body: 'Nunito Sans', sans-serif;
  --font-size-xs: 0.75rem;
  --font-size-sm: 0.875rem;
  --font-size-base: 1rem;
  --font-size-lg: 1.125rem;
  --font-size-xl: 1.25rem;
  --font-size-2xl: 1.5rem;
  --font-size-3xl: 2rem;
  --font-size-4xl: 2.5rem;
  --spacing-xs: 0.25rem;
  --spacing-sm: 0.5rem;
  --spacing-md: 1rem;
  --spacing-lg: 1.5rem;
  --spacing-xl: 2rem;
  --spacing-2xl: 3rem;
  --spacing-3xl: 4rem;
  --border-radius-sm: 0.375rem;
  --border-radius: 0.5rem;
  --border-radius-lg: 0.75rem;
  --border-radius-xl: 1rem;
  --border-radius-full: 9999px;
  --shadow-sm: 0 1px 2px 0 rgba(8, 145, 178, 0.05);
  --shadow: 0 1px 3px 0 rgba(8, 145, 178, 0.1);
  --shadow-lg: 0 4px 6px -1px rgba(8, 145, 178, 0.1);
  --shadow-xl: 0 10px 15px -3px rgba(8, 145, 178, 0.1);
  --transition-fast: 150ms ease;
  --transition-base: 200ms ease;
  --transition-slow: 300ms ease;
}

[data-theme='dark'] {
  --color-primary: #22d3ee;
  --color-primary-hover: #67e8f9;
  --color-primary-light: #0f172a;
  --color-secondary: #0891b2;
  --color-accent: #4ade80;
  --color-surface: #0f172a;
  --color-surface-alt: #1e293b;
  --color-surface-elevated: #1e293b;
  --color-text: #e2e8f0;
  --color-text-secondary: #94a3b8;
  --color-border: #334155;
  --color-success: #4ade80;
  --color-warning: #fbbf24;
  --color-error: #f87171;
  --color-info: #22d3ee;
  --shadow-sm: 0 1px 2px 0 rgba(0, 0, 0, 0.3);
  --shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.4);
  --shadow-lg: 0 4px 6px -1px rgba(0, 0, 0, 0.5);
  --shadow-xl: 0 10px 15px -3px rgba(0, 0, 0, 0.5);
}
```

- [ ] **Step 2: Create `src/styles/_theme.scss`**

```scss
@use '@angular/material' as mat;

$theme: mat.define-theme((
  color: ( theme-type: light, primary: mat.$cyan-palette, tertiary: mat.$green-palette ),
  typography: ( plain-family: var(--font-body), brand-family: var(--font-heading) ),
  density: (scale: 0),
));

:root { @include mat.all-component-themes($theme); }

[data-theme='dark'] {
  @include mat.all-component-colors((color: ( theme-type: dark, primary: mat.$cyan-palette, tertiary: mat.$green-palette )));
}
```

- [ ] **Step 3: Update `src/styles/styles.scss`**

```scss
@use 'variables' as *;
@use 'theme' as *;
@tailwind base;
@tailwind components;
@tailwind utilities;

@layer base {
  *, *::before, *::after { box-sizing: border-box; }
  html {
    font-family: var(--font-body);
    font-size: var(--font-size-base);
    color: var(--color-text);
    background-color: var(--color-surface);
    -webkit-font-smoothing: antialiased;
    scroll-behavior: smooth;
  }
  body { margin: 0; min-height: 100vh; }
  h1, h2, h3, h4, h5, h6 { font-family: var(--font-heading); font-weight: 600; }
  a { color: var(--color-primary); text-decoration: none; transition: color var(--transition-base); }
  a:hover { color: var(--color-primary-hover); }
  img { max-width: 100%; height: auto; }
  *:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 2px; border-radius: var(--border-radius-sm); }
  .shimmer {
    background: linear-gradient(90deg, var(--color-surface-alt) 25%, var(--color-surface-elevated) 50%, var(--color-surface-alt) 75%);
    background-size: 200% 100%;
    animation: shimmer 2s infinite;
  }
}

@keyframes shimmer { 0% { background-position: -200% 0; } 100% { background-position: 200% 0; } }
```

- [ ] **Step 4: Update `angular.json`** — add stylePreprocessorOptions

```json
"styles": ["src/styles/styles.scss"],
"stylePreprocessorOptions": { "includePaths": ["src/styles"] }
```

- [ ] **Step 5: Verify build + commit**

```bash
npx ng build
git add src/styles/ angular.json
git commit -m "feat: add theme system — cyan marketplace palette, Rubik+Nunito fonts, parent CSS variables"
```

---

### Task 4: Create Data Models

**Files:** All under `src/app/models/`

- [ ] **Step 1: `product.model.ts`**

```typescript
export interface Product {
  id: string; sku: string; name: string; description: string;
  categoryId: string; price: number; stockQuantity: number;
  imageUrls: string[]; sellerId: string; active: boolean;
}
export interface ProductSearchParams {
  keyword?: string; categoryId?: string; minPrice?: number;
  maxPrice?: number; page?: number; size?: number; sort?: string;
}
```

- [ ] **Step 2: `category.model.ts`**

```typescript
export interface Category {
  id: string; name: string; description: string;
  parentCategoryId?: string; imageUrl?: string;
}
```

- [ ] **Step 3: `user.model.ts`**

```typescript
export interface User { id: string; email: string; displayName: string; imageUrl?: string; roles: string[]; active: boolean; }
export interface LoginRequest { email: string; password: string; }
export interface RegisterRequest { email: string; password: string; displayName: string; userType: 'CUSTOMER' | 'SELLER'; }
export interface AuthTokenResponse { accessToken: string; refreshToken?: string; tokenType: string; expiresIn: number; }
```

- [ ] **Step 4: `order.model.ts`**

```typescript
export interface OrderItem { productId: string; sku: string; productName: string; imageUrl: string; quantity: number; unitPrice: number; totalPrice: number; }
export interface ShippingAddress { fullName: string; phone: string; street: string; city: string; state: string; zipCode: string; country: string; }
export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED' | 'REFUNDED';
export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'REFUNDED';
export interface Order {
  id: string; orderNumber: string; userId: string; userEmail: string;
  status: OrderStatus; items: OrderItem[]; subtotal: number; taxAmount: number;
  shippingCost: number; discountAmount: number; totalAmount: number; currency: string;
  shippingAddress: ShippingAddress; paymentMethod: string; paymentStatus: PaymentStatus;
  couponCode?: string; notes?: string; createdAt: string; updatedAt: string;
}
export interface CreateOrderRequest { items: { productId: string; quantity: number }[]; shippingAddress: ShippingAddress; paymentMethod: string; couponCode?: string; }
```

- [ ] **Step 5: `cart.model.ts`**

```typescript
export interface CartItem { productId: string; sku: string; name: string; imageUrl: string; unitPrice: number; quantity: number; stockQuantity: number; }
export interface Cart { items: CartItem[]; couponCode?: string; }
```

- [ ] **Step 6: `api-response.model.ts`**

```typescript
export interface PageResponse<T> { content: T[]; totalPages: number; totalElements: number; number: number; size: number; first: boolean; last: boolean; }
export interface ApiError { status: number; message: string; timestamp: string; }
```

- [ ] **Step 7: `models/index.ts`**

```typescript
export * from './api-response.model';
export * from './cart.model';
export * from './category.model';
export * from './order.model';
export * from './product.model';
export * from './user.model';
```

- [ ] **Step 8: Commit**

```bash
npx ng build && git add src/app/models/ && git commit -m "feat: add TypeScript data models"
```

---

### Task 5: Create Core Services

**Files:** `src/environments/` and `src/app/core/services/`

- [ ] **Step 1: `environments/environment.ts`**

```typescript
export const environment = { production: false, apiUrl: 'http://localhost:8080' };
```

- [ ] **Step 2: `environments/environment.production.ts`**

```typescript
export const environment = { production: true, apiUrl: '/api' };
```

- [ ] **Step 3: `core/services/api.service.ts`**

```typescript
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly baseUrl = environment.apiUrl;
  constructor(private http: HttpClient) {}

  get<T>(path: string, params?: Record<string, string | number | boolean | undefined>): Observable<T> {
    let p = new HttpParams();
    if (params) for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== null) p = p.set(k, String(v));
    return this.http.get<T>(`${this.baseUrl}${path}`, { params: p }).pipe(
      catchError((e: HttpErrorResponse) => throwError(() => ({ status: e.status, message: e.error?.message || e.message || 'Error' }))));
  }

  post<T>(path: string, body?: unknown): Observable<T> {
    return this.http.post<T>(`${this.baseUrl}${path}`, body).pipe(
      catchError((e: HttpErrorResponse) => throwError(() => ({ status: e.status, message: e.error?.message || e.message || 'Error' }))));
  }

  put<T>(path: string, body?: unknown): Observable<T> {
    return this.http.put<T>(`${this.baseUrl}${path}`, body).pipe(
      catchError((e: HttpErrorResponse) => throwError(() => ({ status: e.status, message: e.error?.message || e.message || 'Error' }))));
  }

  patch<T>(path: string, body?: unknown): Observable<T> {
    return this.http.patch<T>(`${this.baseUrl}${path}`, body).pipe(
      catchError((e: HttpErrorResponse) => throwError(() => ({ status: e.status, message: e.error?.message || e.message || 'Error' }))));
  }

  delete<T>(path: string): Observable<T> {
    return this.http.delete<T>(`${this.baseUrl}${path}`).pipe(
      catchError((e: HttpErrorResponse) => throwError(() => ({ status: e.status, message: e.error?.message || e.message || 'Error' }))));
  }
}
```

- [ ] **Step 4: `core/services/auth.service.ts`**

```typescript
import { Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { ApiService } from './api.service';
import { AuthTokenResponse, LoginRequest, RegisterRequest, User } from '../../models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly currentUser = signal<User | null>(null);
  readonly isAuthenticated = signal(false);
  private readonly TOKEN_KEY = 'auth_token';
  private readonly USER_KEY = 'current_user';

  constructor(private api: ApiService, private router: Router) { this.restoreSession(); }

  login(creds: LoginRequest) {
    const body = new URLSearchParams();
    body.set('grant_type', 'password');
    body.set('email', creds.email);
    body.set('password', creds.password);
    return this.api.post<AuthTokenResponse>('/api/auth/token', body.toString()).pipe(
      tap((res) => { this.setSession(res); this.loadProfile(); }));
  }

  register(req: RegisterRequest) {
    return this.api.post<User>('/api/auth/register', req).pipe(
      tap(() => this.login({ email: req.email, password: req.password }).subscribe()));
  }

  logout() {
    this.api.post<void>('/api/auth/logout').subscribe({ error: () => {}, complete: () => this.clearSession() });
    this.clearSession();
  }

  loadProfile() {
    this.api.get<User>('/api/user/profile').subscribe({
      next: (u) => { this.currentUser.set(u); this.isAuthenticated.set(true); localStorage.setItem(this.USER_KEY, JSON.stringify(u)); },
      error: () => this.clearSession(),
    });
  }

  getToken(): string | null { return localStorage.getItem(this.TOKEN_KEY); }

  handleOAuthRedirect(token: string) {
    this.setSession({ accessToken: token, tokenType: 'Bearer', expiresIn: 3600 });
    this.loadProfile();
    this.router.navigate(['/']);
  }

  private setSession(r: AuthTokenResponse) { localStorage.setItem(this.TOKEN_KEY, r.accessToken); }
  private clearSession() {
    localStorage.removeItem(this.TOKEN_KEY); localStorage.removeItem(this.USER_KEY);
    this.currentUser.set(null); this.isAuthenticated.set(false); this.router.navigate(['/auth/login']);
  }
  private restoreSession() {
    const t = this.getToken();
    if (t) {
      this.isAuthenticated.set(true);
      const s = localStorage.getItem(this.USER_KEY);
      if (s) { try { this.currentUser.set(JSON.parse(s)); } catch { this.loadProfile(); } } else { this.loadProfile(); }
    }
  }
}
```

- [ ] **Step 5: `core/services/cart.service.ts`**

```typescript
import { Injectable, signal } from '@angular/core';
import { CartItem } from '../../models';

@Injectable({ providedIn: 'root' })
export class CartService {
  readonly items = signal<CartItem[]>([]);
  readonly couponCode = signal<string | undefined>(undefined);
  private readonly KEY = 'cart_items';

  constructor() { this.restore(); }

  readonly totalItems = () => this.items().reduce((s, i) => s + i.quantity, 0);
  readonly subtotal = () => this.items().reduce((s, i) => s + i.unitPrice * i.quantity, 0);

  addItem(item: CartItem) {
    this.items.update((cur) => {
      const ex = cur.find((i) => i.productId === item.productId);
      return ex ? cur.map((i) => i.productId === item.productId ? { ...i, quantity: i.quantity + item.quantity } : i) : [...cur, item];
    });
    this.save();
  }

  updateQuantity(id: string, q: number) {
    if (q <= 0) { this.removeItem(id); return; }
    this.items.update((cur) => cur.map((i) => i.productId === id ? { ...i, quantity: q } : i));
    this.save();
  }

  removeItem(id: string) { this.items.update((cur) => cur.filter((i) => i.productId !== id)); this.save(); }
  clear() { this.items.set([]); this.couponCode.set(undefined); localStorage.removeItem(this.KEY); }
  setCoupon(c: string | undefined) { this.couponCode.set(c); }

  private save() { localStorage.setItem(this.KEY, JSON.stringify(this.items())); }
  private restore() {
    const s = localStorage.getItem(this.KEY);
    if (s) { try { this.items.set(JSON.parse(s)); } catch { localStorage.removeItem(this.KEY); } }
  }
}
```

- [ ] **Step 6: `core/services/product.service.ts`**

```typescript
import { Injectable } from '@angular/core';
import { ApiService } from './api.service';
import { Category, PageResponse, Product, ProductSearchParams } from '../../models';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ProductService {
  constructor(private api: ApiService) {}
  getProducts(p?: ProductSearchParams): Observable<PageResponse<Product>> { return this.api.get('/api/v1/products', p as any); }
  getProduct(id: string): Observable<Product> { return this.api.get(`/api/v1/products/${id}`); }
  searchProducts(k: string, page?: number): Observable<PageResponse<Product>> { return this.api.get('/api/v1/products/search', { keyword: k, page }); }
  getProductsByCategory(c: string, page?: number): Observable<PageResponse<Product>> { return this.api.get(`/api/v1/products/category/${c}`, { page }); }
  filterByPrice(min?: number, max?: number): Observable<PageResponse<Product>> { return this.api.get('/api/v1/products/filter', { minPrice: min, maxPrice: max }); }
  getCategories(): Observable<Category[]> { return this.api.get('/api/v1/categories'); }
  getCategory(id: string): Observable<Category> { return this.api.get(`/api/v1/categories/${id}`); }
}
```

- [ ] **Step 7: `core/services/order.service.ts`**

```typescript
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { CreateOrderRequest, Order } from '../../models';

@Injectable({ providedIn: 'root' })
export class OrderService {
  constructor(private api: ApiService) {}
  createOrder(r: CreateOrderRequest): Observable<Order> { return this.api.post('/api/v1/orders', r); }
  getOrders(): Observable<Order[]> { return this.api.get('/api/v1/orders'); }
  getOrder(id: string): Observable<Order> { return this.api.get(`/api/v1/orders/${id}`); }
  cancelOrder(id: string): Observable<Order> { return this.api.post(`/api/v1/orders/${id}/cancel`); }
}
```

- [ ] **Step 8: `core/services/theme.service.ts`**

```typescript
import { Injectable, signal } from '@angular/core';
export type Theme = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly currentTheme = signal<Theme>('light');
  private readonly KEY = 'app_theme';
  constructor() { this.restore(); }

  toggle() { this.set(this.currentTheme() === 'light' ? 'dark' : 'light'); }

  set(t: Theme) {
    this.currentTheme.set(t);
    document.documentElement.setAttribute('data-theme', t);
    document.documentElement.classList.toggle('dark', t === 'dark');
    localStorage.setItem(this.KEY, t);
  }

  private restore() {
    const s = localStorage.getItem(this.KEY) as Theme | null;
    this.set(s || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'));
  }
}
```

- [ ] **Step 9: `core/services/notification.service.ts`**

```typescript
import { Injectable, signal } from '@angular/core';
export interface Toast { id: string; message: string; type: 'success' | 'error' | 'warning' | 'info'; duration?: number; }

@Injectable({ providedIn: 'root' })
export class NotificationService {
  readonly toasts = signal<Toast[]>([]);

  show(msg: string, type: Toast['type'] = 'info', duration = 4000) {
    const t: Toast = { id: crypto.randomUUID(), message: msg, type, duration };
    this.toasts.update((cur) => [...cur, t]);
    if (duration > 0) setTimeout(() => this.dismiss(t.id), duration);
  }

  dismiss(id: string) { this.toasts.update((cur) => cur.filter((t) => t.id !== id)); }
}
```

- [ ] **Step 10: `core/services/index.ts`**

```typescript
export * from './api.service';
export * from './auth.service';
export * from './cart.service';
export * from './notification.service';
export * from './order.service';
export * from './product.service';
export * from './theme.service';
```

- [ ] **Step 11: Commit**

```bash
npx ng build && git add src/environments/ src/app/core/services/ && git commit -m "feat: add core services"
```

---

### Task 6: Create Interceptors and Guards

- [ ] **Step 1: `core/interceptors/auth.interceptor.ts`**

```typescript
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.url.includes('/api/auth/')) return next(req);
  const token = inject(AuthService).getToken();
  return token ? next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })) : next(req);
};
```

- [ ] **Step 2: `core/interceptors/error.interceptor.ts`**

```typescript
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { NotificationService } from '../services/notification.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const n = inject(NotificationService);
  return next(req).pipe(catchError((e) => {
    if (e.status === 401) { auth.logout(); n.show('Session expired.', 'error'); }
    else if (e.status === 403) n.show('Permission denied.', 'warning');
    else if (e.status === 404) n.show('Not found.', 'warning');
    else if (e.status >= 500) n.show('Server error.', 'error');
    return throwError(() => e);
  }));
};
```

- [ ] **Step 3: `core/guards/auth.guard.ts`**

```typescript
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
export const authGuard = () => inject(AuthService).isAuthenticated() ? true : inject(Router).parseUrl('/auth/login');
```

- [ ] **Step 4: `core/guards/guest.guard.ts`**

```typescript
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
export const guestGuard = () => !inject(AuthService).isAuthenticated() ? true : inject(Router).parseUrl('/');
```

- [ ] **Step 5: Update `app.config.ts`**

```typescript
import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
    provideAnimationsAsync(),
  ],
};
```

- [ ] **Step 6: Commit**

```bash
npx ng build && git add src/app/core/interceptors/ src/app/core/guards/ src/app/app.config.ts && git commit -m "feat: add interceptors and guards"
```

---

### Task 7: Create Shared Components

**Frontend-design principles:** CSS variables only, cursor-pointer on all clickable, smooth transitions (200ms), proper focus states, aria attributes.

- [ ] **Step 1: `shared/components/app-button/app-button.component.ts`**

```typescript
import { Component, input, output } from '@angular/core';
export type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'app-button', standalone: true,
  template: `
    <button [disabled]="disabled()||loading()" [attr.aria-busy]="loading()"
      class="inline-flex items-center justify-center font-medium rounded-lg transition-all duration-200 focus:outline-none focus:ring-2 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
      [class]="cls()" (click)="handleClick.emit()">
      @if (loading()) { <svg class="animate-spin -ml-1 mr-2 h-4 w-4" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"/></svg> }
      <ng-content />
    </button>`,
  host: { class: 'inline-block' },
})
export class AppButton {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<ButtonSize>('md');
  readonly loading = input(false);
  readonly disabled = input(false);
  readonly handleClick = output<void>();

  protected cls() {
    const v: Record<ButtonVariant, string> = {
      primary: 'bg-[var(--color-primary)] text-white hover:bg-[var(--color-primary-hover)] focus:ring-[var(--color-primary)]',
      secondary: 'bg-[var(--color-secondary)] text-white hover:opacity-90 focus:ring-[var(--color-secondary)]',
      outline: 'border-2 border-[var(--color-border)] text-[var(--color-text)] hover:bg-[var(--color-surface-alt)] focus:ring-[var(--color-primary)]',
      ghost: 'text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-alt)] hover:text-[var(--color-text)] focus:ring-[var(--color-primary)]',
      danger: 'bg-[var(--color-error)] text-white hover:opacity-90 focus:ring-[var(--color-error)]',
    };
    const s: Record<ButtonSize, string> = { sm: 'px-3 py-1.5 text-sm gap-1.5', md: 'px-4 py-2 text-sm gap-2', lg: 'px-6 py-3 text-base gap-2' };
    return `${v[this.variant()]} ${s[this.size()]}`;
  }
}
```

- [ ] **Step 2: `shared/components/app-card/app-card.component.ts`**

```typescript
import { Component, input } from '@angular/core';
@Component({
  selector: 'app-card', standalone: true,
  template: `<div class="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] shadow-sm overflow-hidden transition-shadow duration-200 hover:shadow" [class]="padding()?'p-4 sm:p-6':''"><ng-content/></div>`,
})
export class AppCard { readonly padding = input(true); }
```

- [ ] **Step 3: `shared/components/app-modal/app-modal.component.ts`**

```typescript
import { Component, input, output } from '@angular/core';
@Component({
  selector: 'app-modal', standalone: true,
  template: `@if(open()){<div class="fixed inset-0 z-50 flex items-center justify-center p-4 animate-fade-in"><div class="fixed inset-0 bg-black/50 backdrop-blur-sm cursor-pointer" (click)="close.emit()"></div><div class="relative z-10 w-full max-w-lg rounded-2xl bg-[var(--color-surface)] shadow-xl border border-[var(--color-border)] p-6 animate-scale-in">@if(title()){<div class="flex items-center justify-between mb-4"><h2 class="text-lg font-semibold text-[var(--color-text)] font-heading">{{title()}}</h2><button (click)="close.emit()" class="p-1 rounded-lg text-[var(--color-text-secondary)] hover:text-[var(--color-text)] hover:bg-[var(--color-surface-alt)] transition-colors cursor-pointer"><span class="material-icons text-xl">close</span></button></div>}<ng-content/></div></div>}`,
})
export class AppModal { readonly open = input(false); readonly title = input(''); readonly close = output<void>(); }
```

- [ ] **Step 4: `shared/components/app-toast/app-toast.component.ts`**

```typescript
import { Component, inject } from '@angular/core';
import { NotificationService } from '../../../core/services/notification.service';

@Component({
  selector: 'app-toast', standalone: true,
  template: `<div class="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-sm" aria-live="polite">@for(t of n.toasts();track t.id){<div class="flex items-center gap-3 px-4 py-3 rounded-lg shadow-lg border text-sm font-medium animate-slide-up"[class]="t.type==='success'?'bg-[var(--color-success)] text-white':t.type==='error'?'bg-[var(--color-error)] text-white':t.type==='warning'?'bg-[var(--color-warning)] text-black':'bg-[var(--color-primary)] text-white'"><span class="material-icons text-lg">{{t.type==='success'?'check_circle':t.type==='error'?'error':t.type==='warning'?'warning':'info'}}</span><span class="flex-1">{{t.message}}</span><button (click)="n.dismiss(t.id)" class="opacity-60 hover:opacity-100 transition-opacity cursor-pointer" aria-label="Dismiss"><span class="material-icons text-lg">close</span></button></div>}</div>`,
  styles: [`@keyframes s{from{transform:translateY(1rem);opacity:0}to{transform:translateY(0);opacity:1}}.animate-slide-up{animation:s .3s ease-out}`],
})
export class AppToast { protected n = inject(NotificationService); }
```

- [ ] **Step 5: `shared/components/app-pagination/app-pagination.component.ts`**

```typescript
import { Component, input, output } from '@angular/core';
@Component({
  selector: 'app-pagination', standalone: true,
  template: `@if(totalPages()>1){<nav class="flex items-center justify-center gap-1" aria-label="Pagination"><button (click)="pc.emit(currentPage()-1)"[disabled]="currentPage()===0" class="px-3 py-2 rounded-lg text-sm font-medium text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-alt)] disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer" aria-label="Previous"><span class="material-icons text-base">chevron_left</span></button>@for(p of pages();track p){<button (click)="pc.emit(p)" class="min-w-[2.25rem] px-3 py-2 rounded-lg text-sm font-medium transition-all duration-200 cursor-pointer"[class]="p===currentPage()?'bg-[var(--color-primary)] text-white shadow-sm':'text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-alt)]'">{{p+1}}</button>}<button (click)="pc.emit(currentPage()+1)"[disabled]="currentPage()===totalPages()-1" class="px-3 py-2 rounded-lg text-sm font-medium text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-alt)] disabled:opacity-40 disabled:cursor-not-allowed transition-colors cursor-pointer" aria-label="Next"><span class="material-icons text-base">chevron_right</span></button></nav>}`,
})
export class AppPagination {
  readonly currentPage = input.required<number>(); readonly totalPages = input.required<number>();
  readonly pageChange = output<number>(); readonly pc = this.pageChange;
  protected pages() { const t = this.totalPages(), c = this.currentPage(), r: number[] = []; for (let i = Math.max(0, c - 2); i <= Math.min(t - 1, c + 2); i++) r.push(i); return r; }
}
```

- [ ] **Step 6: `shared/components/breadcrumb/breadcrumb.component.ts`**

```typescript
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
export interface BreadcrumbItem { label: string; url?: string; }
@Component({
  selector: 'app-breadcrumb', standalone: true, imports: [RouterLink],
  template: `<nav class="flex items-center gap-2 text-sm text-[var(--color-text-secondary)] mb-4" aria-label="Breadcrumb">@for(item of items();track$index){@if($index>0){<span class="material-icons text-base text-[var(--color-text-secondary)]">chevron_right</span>}@if(item.url&&$index<items().length-1){<a [routerLink]="item.url" class="hover:text-[var(--color-primary)] transition-colors">{{item.label}}</a>}@else{<span class="text-[var(--color-text)] font-medium" aria-current="page">{{item.label}}</span>}}</nav>`,
})
export class Breadcrumb { readonly items = input.required<BreadcrumbItem[]>(); }
```

- [ ] **Step 7: `shared/components/badge/badge.component.ts`**

```typescript
import { Component, input } from '@angular/core';
export type BadgeVariant = 'default' | 'success' | 'warning' | 'error' | 'info';
@Component({
  selector: 'app-badge', standalone: true,
  template: `<span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium"[class]="v()==='success'?'bg-[var(--color-success)]/10 text-[var(--color-success)]':v()==='warning'?'bg-[var(--color-warning)]/10 text-[var(--color-warning)]':v()==='error'?'bg-[var(--color-error)]/10 text-[var(--color-error)]':v()==='info'?'bg-[var(--color-primary)]/10 text-[var(--color-primary)]':'bg-[var(--color-surface-alt)] text-[var(--color-text-secondary)]'"><ng-content/></span>`,
})
export class Badge { readonly variant = input<BadgeVariant>('default'); protected v = this.variant; }
```

- [ ] **Step 8: `shared/components/empty-state/empty-state.component.ts`**

```typescript
import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
@Component({
  selector: 'app-empty-state', standalone: true, imports: [RouterLink],
  template: `<div class="flex flex-col items-center justify-center py-16 px-4 text-center animate-fade-in"><span class="material-icons text-5xl mb-4 text-[var(--color-text-secondary)]">{{icon()}}</span><h3 class="text-lg font-semibold text-[var(--color-text)] font-heading mb-2">{{title()}}</h3>@if(message()){<p class="text-sm text-[var(--color-text-secondary)] mb-6 max-w-sm">{{message()}}</p>}@if(actionLabel()){<a routerLink="/products" class="px-4 py-2 bg-[var(--color-primary)] text-white rounded-lg text-sm font-medium hover:bg-[var(--color-primary-hover)] transition-colors cursor-pointer">{{actionLabel()}}</a>}</div>`,
})
export class EmptyState { readonly icon = input('inventory_2'); readonly title = input.required<string>(); readonly message = input(''); readonly actionLabel = input(''); }
```

- [ ] **Step 9: `shared/components/skeleton-card/skeleton-card.component.ts`**

```typescript
import { Component } from '@angular/core';
@Component({
  selector: 'app-skeleton-card', standalone: true,
  template: `<div class="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4"><div class="aspect-square shimmer rounded-lg mb-4"></div><div class="h-4 shimmer rounded w-3/4 mb-2"></div><div class="h-4 shimmer rounded w-1/2 mb-3"></div><div class="h-6 shimmer rounded w-1/3"></div></div>`,
})
export class SkeletonCard {}
```

- [ ] **Step 10: `shared/components/product-card/product-card.component.ts`**

```typescript
import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Product } from '../../../models';
import { CurrencyPipe } from '../../pipes/currency.pipe';
import { Badge } from '../badge/badge.component';

@Component({
  selector: 'app-product-card', standalone: true, imports: [RouterLink, CurrencyPipe, Badge],
  template: `
    <div class="group rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] overflow-hidden transition-all duration-300 hover:shadow-lg hover:-translate-y-0.5 cursor-pointer">
      <a [routerLink]="['/products',product().id]" class="block">
        <div class="aspect-square bg-[var(--color-surface-alt)] overflow-hidden relative">
          <img [src]="product().imageUrls[0]||'assets/placeholder.svg'" [alt]="product().name" class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" loading="lazy"/>
          @if(product().stockQuantity<=0){<div class="absolute top-2 left-2"><app-badge variant="error">Out of Stock</app-badge></div>}
          @else if(product().stockQuantity<10){<div class="absolute top-2 left-2"><app-badge variant="warning">Only {{product().stockQuantity}} left</app-badge></div>}
        </div>
        <div class="p-4">
          <h3 class="font-heading font-medium text-[var(--color-text)] text-sm line-clamp-2 mb-1 group-hover:text-[var(--color-primary)] transition-colors">{{product().name}}</h3>
          <p class="text-xs text-[var(--color-text-secondary)] mb-2 truncate">{{product().description}}</p>
          <span class="text-lg font-bold text-[var(--color-primary)]">{{product().price|appCurrency}}</span>
        </div>
      </a>
      <div class="px-4 pb-4">
        <button (click)="addToCart.emit(product())"[disabled]="product().stockQuantity<=0"
          class="w-full px-4 py-2 bg-[var(--color-primary)] text-white rounded-lg text-sm font-medium hover:bg-[var(--color-primary-hover)] disabled:opacity-50 disabled:cursor-not-allowed transition-colors cursor-pointer">
          {{product().stockQuantity<=0?'Sold Out':'Add to Cart'}}</button>
      </div>
    </div>`,
})
export class ProductCard { readonly product = input.required<Product>(); readonly addToCart = output<Product>(); }
```

- [ ] **Step 11: `shared/components/product-grid/product-grid.component.ts`**

```typescript
import { Component, input, output } from '@angular/core';
import { Product } from '../../../models';
import { ProductCard } from '../product-card/product-card.component';
import { SkeletonCard } from '../skeleton-card/skeleton-card.component';
import { EmptyState } from '../empty-state/empty-state.component';

@Component({
  selector: 'app-product-grid', standalone: true, imports: [ProductCard, SkeletonCard, EmptyState],
  template: `@if(loading()){<div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">@for(_ of[1,2,3,4];track _){<app-skeleton-card/>}</div>}@else if(products().length===0){<app-empty-state icon="search_off" title="No products found" message="Try adjusting filters."/>}@else{<div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">@for(p of products();track p.id){<app-product-card [product]="p"(addToCart)="addToCart.emit($event)"/>}</div>}`,
})
export class ProductGrid { readonly products = input.required<Product[]>(); readonly loading = input(false); readonly addToCart = output<Product>(); }
```

- [ ] **Step 12: `shared/components/search-bar/search-bar.component.ts`**

```typescript
import { Component, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-search-bar', standalone: true, imports: [FormsModule],
  template: `<div class="relative"><span class="material-icons absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-text-secondary)] text-lg">search</span><input [ngModel]="value()"(ngModelChange)="valueChange.emit($event)"(keydown.enter)="search.emit()"[placeholder]="placeholder()" class="w-full pl-10 pr-10 py-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-text)] text-sm placeholder:text-[var(--color-text-secondary)] focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)] focus:border-transparent transition-all duration-200" aria-label="Search"/>@if(value()){<button (click)="valueChange.emit('')" class="absolute right-3 top-1/2 -translate-y-1/2 text-[var(--color-text-secondary)] hover:text-[var(--color-text)] transition-colors cursor-pointer" aria-label="Clear"><span class="material-icons text-base">close</span></button>}</div>`,
})
export class SearchBar { readonly placeholder = input('Search products...'); readonly value = input(''); readonly valueChange = output<string>(); readonly search = output<void>(); }
```

- [ ] **Step 13: `shared/pipes/currency.pipe.ts`**

```typescript
import { Pipe, PipeTransform } from '@angular/core';
@Pipe({ name: 'appCurrency', standalone: true })
export class CurrencyPipe implements PipeTransform { transform(v: number | undefined | null): string { return v == null ? '' : new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(v); } }
```

- [ ] **Step 14: `shared/pipes/relative-time.pipe.ts`**

```typescript
import { Pipe, PipeTransform } from '@angular/core';
@Pipe({ name: 'appRelativeTime', standalone: true })
export class RelativeTimePipe implements PipeTransform {
  transform(v: string | undefined | null): string {
    if (!v) return '';
    const d = Date.now() - new Date(v).getTime(), s = Math.floor(d / 1000);
    if (s < 60) return 'just now';
    const m = Math.floor(s / 60); if (m < 60) return `${m}m ago`;
    const h = Math.floor(m / 60); if (h < 24) return `${h}h ago`;
    const dd = Math.floor(h / 24); if (dd < 7) return `${dd}d ago`;
    return new Date(v).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  }
}
```

- [ ] **Step 15: Commit**

```bash
npx ng build && git add src/app/shared/ && git commit -m "feat: add shared components with animations and accessibility"
```

---

### Task 8: Create Layouts

- [ ] **Step 1: `layouts/main-layout/main-layout.component.ts`**

```typescript
import { Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { CartService } from '../../core/services/cart.service';
import { ThemeService } from '../../core/services/theme.service';
import { SearchBar } from '../../shared/components/search-bar/search-bar.component';
import { AppToast } from '../../shared/components/app-toast/app-toast.component';

@Component({
  selector: 'app-main-layout', standalone: true, imports: [RouterOutlet, RouterLink, SearchBar, AppToast],
  template: `
<div class="min-h-screen flex flex-col bg-[var(--color-surface)]">
  <div class="bg-[var(--color-primary)] text-white text-center text-xs py-1.5 font-medium">Free shipping on orders over $50</div>
  <header class="sticky top-0 z-40 border-b border-[var(--color-border)] bg-[var(--color-surface)]/90 backdrop-blur-md">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="flex items-center justify-between h-16 gap-4">
        <a routerLink="/" class="flex items-center gap-2 shrink-0 group">
          <div class="w-8 h-8 rounded-lg bg-[var(--color-primary)] flex items-center justify-center"><span class="material-icons text-white text-sm">shopping_bag</span></div>
          <span class="text-xl font-bold text-[var(--color-text)] font-heading group-hover:text-[var(--color-primary)] transition-colors">Shop</span>
        </a>
        <div class="flex-1 max-w-md hidden sm:block"><app-search-bar [value]="q"(search)="onSearch()"(valueChange)="q=$event"/></div>
        <nav class="flex items-center gap-1">
          <button (click)="t.toggle()" class="p-2 rounded-lg text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-alt)] hover:text-[var(--color-text)] transition-all duration-200 cursor-pointer" [attr.aria-label]="'Switch to '+(t.currentTheme()==='light'?'dark':'light')+' mode'">
            <span class="material-icons text-lg">{{t.currentTheme()==='light'?'dark_mode':'light_mode'}}</span>
          </button>
          <a routerLink="/cart" class="relative p-2 rounded-lg text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-alt)] hover:text-[var(--color-text)] transition-all duration-200 cursor-pointer">
            <span class="material-icons text-lg">shopping_cart</span>
            @if(c.totalItems()>0){<span class="absolute -top-0.5 -right-0.5 bg-[var(--color-primary)] text-white text-[10px] w-[18px] h-[18px] flex items-center justify-center rounded-full font-bold shadow-sm animate-scale-in">{{c.totalItems()>99?'99+':c.totalItems()}}</span>}
          </a>
          @if(auth.isAuthenticated()){
            <a routerLink="/orders" class="p-2 rounded-lg text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-alt)] hover:text-[var(--color-text)] transition-all cursor-pointer" aria-label="Orders"><span class="material-icons text-lg">receipt_long</span></a>
            <a routerLink="/profile" class="p-2 rounded-lg text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-alt)] hover:text-[var(--color-text)] transition-all cursor-pointer" aria-label="Profile"><span class="material-icons text-lg">person</span></a>
            <button (click)="auth.logout()" class="p-2 rounded-lg text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-alt)] hover:text-[var(--color-text)] transition-all cursor-pointer" aria-label="Logout"><span class="material-icons text-lg">logout</span></button>
          }@else{<a routerLink="/auth/login" class="px-4 py-2 bg-[var(--color-primary)] text-white rounded-lg text-sm font-medium hover:bg-[var(--color-primary-hover)] transition-all cursor-pointer shadow-sm hover:shadow">Sign In</a>}
        </nav>
      </div>
    </div>
  </header>
  <main class="flex-1"><router-outlet/></main>
  <footer class="border-t border-[var(--color-border)] bg-[var(--color-surface-alt)]">
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-8 mb-8">
        <div><div class="flex items-center gap-2 mb-3"><div class="w-6 h-6 rounded bg-[var(--color-primary)] flex items-center justify-center"><span class="material-icons text-white text-xs">shopping_bag</span></div><span class="font-bold text-[var(--color-text)] font-heading">Shop</span></div><p class="text-sm text-[var(--color-text-secondary)]">Your one-stop shop for amazing products.</p></div>
        <div><h4 class="font-semibold text-[var(--color-text)] text-sm mb-3 font-heading">Quick Links</h4><div class="space-y-2 text-sm"><a routerLink="/products" class="block text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] transition-colors">Products</a><a routerLink="/cart" class="block text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] transition-colors">Cart</a></div></div>
        <div><h4 class="font-semibold text-[var(--color-text)] text-sm mb-3 font-heading">Support</h4><div class="space-y-2 text-sm"><a routerLink="/contact" class="block text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] transition-colors">Contact</a><a routerLink="/faq" class="block text-[var(--color-text-secondary)] hover:text-[var(--color-primary)] transition-colors">FAQ</a></div></div>
      </div>
      <div class="border-t border-[var(--color-border)] pt-6 text-center text-sm text-[var(--color-text-secondary)]"><p>&copy; 2026 Shop. All rights reserved.</p></div>
    </div>
  </footer>
</div>
<app-toast/>`,
})
export class MainLayout {
  protected auth = inject(AuthService); protected c = inject(CartService); protected t = inject(ThemeService);
  private router = inject(Router); protected q = '';
  protected onSearch() { if (this.q.trim()) this.router.navigate(['/products'], { queryParams: { q: this.q.trim() } }); }
}
```

- [ ] **Step 2: `layouts/auth-layout/auth-layout.component.ts`**

```typescript
import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AppToast } from '../../shared/components/app-toast/app-toast.component';

@Component({
  selector: 'app-auth-layout', standalone: true, imports: [RouterOutlet, AppToast],
  template: `<div class="min-h-screen flex items-center justify-center bg-gradient-to-br from-[var(--color-primary-light)] to-[var(--color-surface)] dark:from-[var(--color-surface)] dark:to-[var(--color-surface-alt)] px-4 py-8"><div class="w-full max-w-md animate-fade-in"><router-outlet/></div></div><app-toast/>`,
})
export class AuthLayout {}
```

- [ ] **Step 3: Commit**

```bash
npx ng build && git add src/app/layouts/ && git commit -m "feat: add layouts — main with announcement bar, auth with gradient"
```

---

### Task 9: Configure Routing

- [ ] **Step 1: `app.routes.ts`**

```typescript
import { Routes } from '@angular/router';
import { MainLayout } from './layouts/main-layout/main-layout.component';
import { AuthLayout } from './layouts/auth-layout/auth-layout.component';
import { authGuard } from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';

export const routes: Routes = [
  { path: '', component: MainLayout, children: [
    { path: '', loadComponent: () => import('./features/home/home.component').then(m => m.HomeComponent) },
    { path: 'products', loadComponent: () => import('./features/products/product-list/product-list.component').then(m => m.ProductListComponent) },
    { path: 'products/:id', loadComponent: () => import('./features/products/product-detail/product-detail.component').then(m => m.ProductDetailComponent) },
    { path: 'cart', loadComponent: () => import('./features/cart/cart.component').then(m => m.CartComponent) },
    { path: 'checkout', loadComponent: () => import('./features/checkout/checkout.component').then(m => m.CheckoutComponent), canActivate: [authGuard] },
    { path: 'orders', loadComponent: () => import('./features/orders/order-list/order-list.component').then(m => m.OrderListComponent), canActivate: [authGuard] },
    { path: 'orders/:id', loadComponent: () => import('./features/orders/order-detail/order-detail.component').then(m => m.OrderDetailComponent), canActivate: [authGuard] },
    { path: 'profile', loadComponent: () => import('./features/profile/profile.component').then(m => m.ProfileComponent), canActivate: [authGuard] },
  ]},
  { path: 'auth', component: AuthLayout, children: [
    { path: 'login', loadComponent: () => import('./features/auth/login/login.component').then(m => m.LoginComponent), canActivate: [guestGuard] },
    { path: 'register', loadComponent: () => import('./features/auth/register/register.component').then(m => m.RegisterComponent), canActivate: [guestGuard] },
    { path: 'oauth2/redirect', loadComponent: () => import('./features/auth/oauth-redirect/oauth-redirect.component').then(m => m.OauthRedirectComponent) },
  ]},
  { path: '**', loadComponent: () => import('./features/not-found/not-found.component').then(m => m.NotFoundComponent) },
];
```

- [ ] **Step 2: `app.component.ts`**

```typescript
import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
@Component({ selector: 'app-root', standalone: true, imports: [RouterOutlet], template: `<router-outlet/>` })
export class AppComponent {}
```

- [ ] **Step 3: Commit**

```bash
git add src/app/app.routes.ts src/app/app.component.ts && git commit -m "feat: configure lazy-loaded routes"
```

---

### Task 10: Create Home Page

- [ ] **Step 1: `features/home/home.component.ts`**

```typescript
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProductService } from '../../core/services/product.service';
import { CartService } from '../../core/services/cart.service';
import { ProductGrid } from '../../shared/components/product-grid/product-grid.component';
import { Category, Product } from '../../models';

@Component({
  selector: 'app-home', standalone: true, imports: [RouterLink, ProductGrid],
  template: `
<section class="relative overflow-hidden bg-gradient-to-br from-[var(--color-primary)] via-[var(--color-primary)]/90 to-[var(--color-secondary)] py-20 sm:py-28">
  <div class="absolute inset-0 opacity-10"><div class="absolute top-10 left-10 w-72 h-72 bg-white rounded-full blur-3xl"></div><div class="absolute bottom-10 right-10 w-96 h-96 bg-white rounded-full blur-3xl"></div></div>
  <div class="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
    <h1 class="text-4xl sm:text-5xl lg:text-6xl font-bold text-white font-heading mb-4 animate-slide-up">Discover Amazing Products</h1>
    <p class="text-lg sm:text-xl text-white/80 mb-8 max-w-2xl mx-auto">Shop the latest trends with confidence. Free shipping on orders over $50.</p>
    <a routerLink="/products" class="inline-flex items-center gap-2 px-8 py-3.5 bg-white text-[var(--color-primary)] rounded-xl text-sm font-bold hover:bg-white/90 hover:shadow-lg hover:-translate-y-0.5 transition-all duration-300 shadow-md cursor-pointer">Shop Now<span class="material-icons text-base">arrow_forward</span></a>
  </div>
</section>
@if(categories().length>0){
<section class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
  <h2 class="text-2xl sm:text-3xl font-bold text-[var(--color-text)] font-heading mb-8">Shop by Category</h2>
  <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
    @for(cat of categories();track cat.id){
      <a [routerLink]="['/products']"[queryParams]="{category:cat.id}" class="flex flex-col items-center p-6 rounded-2xl border border-[var(--color-border)] bg-[var(--color-surface)] hover:shadow-lg hover:border-[var(--color-primary)] hover:-translate-y-1 transition-all duration-300 group cursor-pointer">
        @if(cat.imageUrl){<img [src]="cat.imageUrl"[alt]="cat.name" class="w-16 h-16 object-cover rounded-full mb-3" loading="lazy"/>}@else{<div class="w-16 h-16 rounded-full bg-[var(--color-primary)]/10 flex items-center justify-center mb-3 group-hover:bg-[var(--color-primary)]/20 transition-colors"><span class="material-icons text-2xl text-[var(--color-primary)]">category</span></div>}
        <span class="text-sm font-medium text-[var(--color-text)] group-hover:text-[var(--color-primary)] transition-colors text-center font-heading">{{cat.name}}</span>
      </a>
    }
  </div>
</section>
}
<section class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16">
  <div class="flex items-center justify-between mb-8"><h2 class="text-2xl sm:text-3xl font-bold text-[var(--color-text)] font-heading">Featured Products</h2><a routerLink="/products" class="text-sm font-medium text-[var(--color-primary)] hover:text-[var(--color-primary-hover)] hover:underline transition-colors">View All &rarr;</a></div>
  <app-product-grid [products]="featuredProducts()"[loading]="loading()"(addToCart)="onAddToCart($event)"/>
</section>
<section class="bg-[var(--color-surface-alt)] py-20">
  <div class="max-w-xl mx-auto px-4 text-center">
    <span class="material-icons text-4xl text-[var(--color-primary)] mb-4">mail</span>
    <h2 class="text-2xl sm:text-3xl font-bold text-[var(--color-text)] font-heading mb-2">Stay Updated</h2>
    <p class="text-[var(--color-text-secondary)] mb-6">Get notified about new products and exclusive deals.</p>
    <div class="flex gap-2 max-w-md mx-auto"><input type="email" placeholder="Enter your email" class="flex-1 px-4 py-3 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]" aria-label="Email"/><button class="px-6 py-3 bg-[var(--color-primary)] text-white rounded-xl text-sm font-bold hover:bg-[var(--color-primary-hover)] transition-all cursor-pointer shadow-sm hover:shadow shrink-0">Subscribe</button></div>
  </div>
</section>`,
})
export class HomeComponent implements OnInit {
  private ps = inject(ProductService); private cs = inject(CartService);
  readonly categories = signal<Category[]>([]); readonly featuredProducts = signal<Product[]>([]); readonly loading = signal(true);
  ngOnInit() {
    this.ps.getCategories().subscribe({ next: (c) => this.categories.set(c), error: () => {} });
    this.ps.getProducts({ page: 0, size: 8, sort: 'createdAt,desc' }).subscribe({ next: (r) => { this.featuredProducts.set(r.content); this.loading.set(false); }, error: () => this.loading.set(false) });
  }
  protected onAddToCart(p: Product) { this.cs.addItem({ productId: p.id, sku: p.sku, name: p.name, imageUrl: p.imageUrls[0] || '', unitPrice: p.price, quantity: 1, stockQuantity: p.stockQuantity }); }
}
```

- [ ] **Step 2: Commit**

```bash
npx ng build && git add src/app/features/home/ && git commit -m "feat: add home page with gradient hero, categories, featured products"
```

---

### Task 11: Create Auth Pages

- [ ] **Step 1: `features/auth/login/login.component.ts`**

```typescript
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';

@Component({ selector: 'app-login', standalone: true, imports: [RouterLink, FormsModule],
  template: `
<div class="bg-[var(--color-surface)] rounded-2xl shadow-lg border border-[var(--color-border)] p-8">
  <div class="text-center mb-8"><div class="w-12 h-12 rounded-xl bg-[var(--color-primary)] flex items-center justify-center mx-auto mb-4"><span class="material-icons text-white text-xl">shopping_bag</span></div><h1 class="text-2xl font-bold text-[var(--color-text)] font-heading">Welcome Back</h1><p class="text-sm text-[var(--color-text-secondary)] mt-1">Sign in to your account</p></div>
  <form (ngSubmit)="onSubmit()" class="space-y-4">
    <div><label for="le" class="block text-sm font-medium text-[var(--color-text)] mb-1.5">Email</label><input id="le" type="email" [(ngModel)]="email" name="email" required class="w-full px-3 py-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]" placeholder="you@example.com"/></div>
    <div><label for="lp" class="block text-sm font-medium text-[var(--color-text)] mb-1.5">Password</label><input id="lp" type="password" [(ngModel)]="password" name="password" required class="w-full px-3 py-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]" placeholder="Enter password"/></div>
    @if(error()){<p class="text-sm text-[var(--color-error)] flex items-center gap-1"><span class="material-icons text-sm">error</span>{{error()}}</p>}
    <button type="submit" [disabled]="loading()" class="w-full px-4 py-2.5 bg-[var(--color-primary)] text-white rounded-xl text-sm font-bold hover:bg-[var(--color-primary-hover)] disabled:opacity-50 transition-all cursor-pointer shadow-sm hover:shadow">{{loading()?'Signing in...':'Sign In'}}</button>
  </form>
  <div class="mt-6"><div class="relative mb-6"><div class="absolute inset-0 flex items-center"><div class="w-full border-t border-[var(--color-border)]"></div></div><div class="relative flex justify-center text-sm"><span class="px-2 bg-[var(--color-surface)] text-[var(--color-text-secondary)]">or continue with</span></div></div>
    <a href="http://localhost:8080/oauth2/authorization/google" class="w-full flex items-center justify-center gap-2 px-4 py-2.5 border border-[var(--color-border)] rounded-xl text-sm font-medium text-[var(--color-text)] hover:bg-[var(--color-surface-alt)] hover:border-[var(--color-primary)] transition-all cursor-pointer">
      <svg class="w-5 h-5" viewBox="0 0 24 24"><path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92a5.06 5.06 0 01-2.2 3.32v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.1z"/><path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/><path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/><path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/></svg>
      Google
    </a>
  </div>
  <p class="mt-6 text-center text-sm text-[var(--color-text-secondary)]">Don't have an account? <a routerLink="/auth/register" class="text-[var(--color-primary)] font-medium hover:underline">Sign up</a></p>
</div>`,
})
export class LoginComponent {
  private auth = inject(AuthService); private router = inject(Router); private n = inject(NotificationService);
  protected email = ''; protected password = ''; protected loading = signal(false); protected error = signal('');
  protected onSubmit() {
    if (!this.email || !this.password) return; this.loading.set(true); this.error.set('');
    this.auth.login({ email: this.email, password: this.password }).subscribe({ next: () => { this.n.show('Logged in!', 'success'); this.router.navigate(['/']); }, error: (e) => { this.error.set(e.message || 'Invalid credentials'); this.loading.set(false); } });
  }
}
```

- [ ] **Step 2: `features/auth/register/register.component.ts`**

```typescript
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService } from '../../../core/services/notification.service';

@Component({ selector: 'app-register', standalone: true, imports: [RouterLink, FormsModule],
  template: `
<div class="bg-[var(--color-surface)] rounded-2xl shadow-lg border border-[var(--color-border)] p-8">
  <div class="text-center mb-8"><div class="w-12 h-12 rounded-xl bg-[var(--color-primary)] flex items-center justify-center mx-auto mb-4"><span class="material-icons text-white text-xl">person_add</span></div><h1 class="text-2xl font-bold text-[var(--color-text)] font-heading">Create Account</h1><p class="text-sm text-[var(--color-text-secondary)] mt-1">Join us today</p></div>
  <form (ngSubmit)="onSubmit()" class="space-y-4">
    <div><label for="rn" class="block text-sm font-medium text-[var(--color-text)] mb-1.5">Full Name</label><input id="rn" type="text" [(ngModel)]="displayName" name="displayName" required class="w-full px-3 py-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]" placeholder="John Doe"/></div>
    <div><label for="re" class="block text-sm font-medium text-[var(--color-text)] mb-1.5">Email</label><input id="re" type="email" [(ngModel)]="email" name="email" required class="w-full px-3 py-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]" placeholder="you@example.com"/></div>
    <div><label for="rp" class="block text-sm font-medium text-[var(--color-text)] mb-1.5">Password</label><input id="rp" type="password" [(ngModel)]="password" name="password" required minlength="6" class="w-full px-3 py-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]" placeholder="6+ characters"/></div>
    <div><label for="rt" class="block text-sm font-medium text-[var(--color-text)] mb-1.5">I want to</label><select id="rt" [(ngModel)]="userType" name="userType" class="w-full px-3 py-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]"><option value="CUSTOMER">Buy Products</option><option value="SELLER">Sell Products</option></select></div>
    @if(error()){<p class="text-sm text-[var(--color-error)]">{{error()}}</p>}
    <button type="submit" [disabled]="loading()" class="w-full px-4 py-2.5 bg-[var(--color-primary)] text-white rounded-xl text-sm font-bold hover:bg-[var(--color-primary-hover)] disabled:opacity-50 transition-all cursor-pointer shadow-sm hover:shadow">{{loading()?'Creating...':'Create Account'}}</button>
  </form>
  <p class="mt-6 text-center text-sm text-[var(--color-text-secondary)]">Already have an account? <a routerLink="/auth/login" class="text-[var(--color-primary)] font-medium hover:underline">Sign in</a></p>
</div>`,
})
export class RegisterComponent {
  private auth = inject(AuthService); private router = inject(Router); private n = inject(NotificationService);
  protected displayName = ''; protected email = ''; protected password = ''; protected userType: 'CUSTOMER'|'SELLER' = 'CUSTOMER';
  protected loading = signal(false); protected error = signal('');
  protected onSubmit() {
    if (!this.displayName || !this.email || !this.password) return; this.loading.set(true); this.error.set('');
    this.auth.register({ displayName: this.displayName, email: this.email, password: this.password, userType: this.userType }).subscribe({ next: () => { this.n.show('Account created!', 'success'); this.router.navigate(['/']); }, error: (e) => { this.error.set(e.message || 'Failed.'); this.loading.set(false); } });
  }
}
```

- [ ] **Step 3: `features/auth/oauth-redirect/oauth-redirect.component.ts`**

```typescript
import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({ selector: 'app-oauth-redirect', standalone: true,
  template: `<div class="flex items-center justify-center min-h-[200px]"><div class="text-center"><div class="animate-spin w-8 h-8 border-2 border-[var(--color-primary)] border-t-transparent rounded-full mx-auto mb-4"></div><p class="text-sm text-[var(--color-text-secondary)]">Completing sign in...</p></div></div>` })
export class OauthRedirectComponent implements OnInit {
  private route = inject(ActivatedRoute); private auth = inject(AuthService);
  ngOnInit() { this.route.fragment.subscribe(f => { if (f) { const t = new URLSearchParams(f).get('token'); if (t) this.auth.handleOAuthRedirect(t); } }); }
}
```

- [ ] **Step 4: `features/not-found/not-found.component.ts`**

```typescript
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
@Component({ selector: 'app-not-found', standalone: true, imports: [RouterLink],
  template: `<div class="flex flex-col items-center justify-center py-24 px-4 text-center"><span class="text-8xl font-bold text-[var(--color-primary)] font-heading mb-4">404</span><h1 class="text-2xl font-bold text-[var(--color-text)] font-heading mb-2">Page Not Found</h1><p class="text-[var(--color-text-secondary)] mb-8">The page you're looking for doesn't exist.</p><a routerLink="/" class="px-6 py-2.5 bg-[var(--color-primary)] text-white rounded-xl text-sm font-bold hover:bg-[var(--color-primary-hover)] transition-all cursor-pointer shadow-sm hover:shadow">Go Home</a></div>` })
export class NotFoundComponent {}
```

- [ ] **Step 5: Commit**

```bash
npx ng build && git add src/app/features/auth/ src/app/features/not-found/ && git commit -m "feat: add auth pages (login, register, oauth, 404)"
```

---

### Task 12: Create Product Pages

- [ ] **Step 1: `features/products/product-list/product-list.component.ts`**

```typescript
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ProductService } from '../../../core/services/product.service';
import { CartService } from '../../../core/services/cart.service';
import { ProductGrid } from '../../../shared/components/product-grid/product-grid.component';
import { AppPagination } from '../../../shared/components/app-pagination/app-pagination.component';
import { Breadcrumb } from '../../../shared/components/breadcrumb/breadcrumb.component';
import { Category, Product, PageResponse } from '../../../models';

@Component({ selector: 'app-product-list', standalone: true, imports: [FormsModule, ProductGrid, AppPagination, Breadcrumb],
  template: `
<div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
  <app-breadcrumb [items]="[{label:'Home',url:'/'},{label:'Products'}]"/>
  <div class="flex flex-col lg:flex-row gap-8">
    <aside class="w-full lg:w-56 shrink-0">
      <div class="sticky top-24 space-y-6">
        <div><h3 class="text-xs font-bold text-[var(--color-text-secondary)] mb-3 uppercase tracking-widest font-heading">Categories</h3><div class="space-y-0.5">
          <button (click)="filterByCategory('')" class="w-full text-left px-3 py-2 rounded-lg text-sm transition-all duration-200 cursor-pointer"[class]="!selectedCategory()?'bg-[var(--color-primary)]/10 text-[var(--color-primary)] font-semibold':'text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-alt)] hover:text-[var(--color-text)]'">All Products</button>
          @for(cat of categories();track cat.id){<button (click)="filterByCategory(cat.id)" class="w-full text-left px-3 py-2 rounded-lg text-sm transition-all duration-200 cursor-pointer"[class]="selectedCategory()===cat.id?'bg-[var(--color-primary)]/10 text-[var(--color-primary)] font-semibold':'text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-alt)] hover:text-[var(--color-text)]'">{{cat.name}}</button>}
        </div></div>
        <div><h3 class="text-xs font-bold text-[var(--color-text-secondary)] mb-3 uppercase tracking-widest font-heading">Sort By</h3><select [(ngModel)]="sortBy"(ngModelChange)="onSortChange()" class="w-full px-3 py-2 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]"><option value="createdAt,desc">Newest</option><option value="price,asc">Price: Low to High</option><option value="price,desc">Price: High to Low</option></select></div>
      </div>
    </aside>
    <div class="flex-1 min-w-0">
      <h1 class="text-2xl font-bold text-[var(--color-text)] font-heading mb-6">{{selectedCategoryName()||'All Products'}}</h1>
      <app-product-grid [products]="products()"[loading]="loading()"(addToCart)="onAddToCart($event)"/>
      @if(!loading()&&products().length>0){<div class="mt-8"><app-pagination [currentPage]="currentPage()"[totalPages]="totalPages()"(pageChange)="onPageChange($event)"/></div>}
    </div>
  </div>
</div>`})
export class ProductListComponent implements OnInit {
  private ps = inject(ProductService); private cs = inject(CartService); private route = inject(ActivatedRoute);
  readonly products = signal<Product[]>([]); readonly categories = signal<Category[]>([]);
  readonly loading = signal(true); readonly currentPage = signal(0); readonly totalPages = signal(0);
  readonly selectedCategory = signal(''); readonly selectedCategoryName = signal('All Products');
  protected sortBy = 'createdAt,desc';

  ngOnInit() {
    this.ps.getCategories().subscribe({ next: (c) => this.categories.set(c) });
    this.route.queryParams.subscribe(p => { if(p['category']){this.selectedCategory.set(p['category']);const c=this.categories().find(x=>x.id===p['category']);if(c)this.selectedCategoryName.set(c.name);} this.load(); });
  }

  private load() {
    this.loading.set(true);
    const h = (r: PageResponse<Product>) => { this.products.set(r.content); this.totalPages.set(r.totalPages); this.currentPage.set(r.number); this.loading.set(false); };
    if (this.selectedCategory()) { this.ps.getProductsByCategory(this.selectedCategory(), this.currentPage()).subscribe({ next: h, error: () => this.loading.set(false) }); return; }
    this.ps.getProducts({ page: this.currentPage(), size: 12, sort: this.sortBy }).subscribe({ next: h, error: () => this.loading.set(false) });
  }

  protected filterByCategory(c: string) { this.selectedCategory.set(c); this.currentPage.set(0); this.selectedCategoryName.set(c?this.categories().find(x=>x.id===c)?.name||'':'All Products'); this.load(); }
  protected onSortChange() { this.currentPage.set(0); this.load(); }
  protected onPageChange(p: number) { this.currentPage.set(p); this.load(); }
  protected onAddToCart(p: Product) { this.cs.addItem({productId:p.id,sku:p.sku,name:p.name,imageUrl:p.imageUrls[0]||'',unitPrice:p.price,quantity:1,stockQuantity:p.stockQuantity}); }
}
```

- [ ] **Step 2: `features/products/product-detail/product-detail.component.ts`**

```typescript
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ProductService } from '../../../core/services/product.service';
import { CartService } from '../../../core/services/cart.service';
import { NotificationService } from '../../../core/services/notification.service';
import { Breadcrumb } from '../../../shared/components/breadcrumb/breadcrumb.component';
import { Badge } from '../../../shared/components/badge/badge.component';
import { CurrencyPipe } from '../../../shared/pipes/currency.pipe';
import { Product } from '../../../models';

@Component({ selector: 'app-product-detail', standalone: true, imports: [RouterLink, Breadcrumb, Badge, CurrencyPipe],
  template: `@if(loading()){<div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 grid grid-cols-1 lg:grid-cols-2 gap-8"><div class="aspect-square shimmer rounded-xl"></div><div class="space-y-4"><div class="h-8 shimmer rounded w-3/4"></div><div class="h-4 shimmer rounded w-1/4"></div><div class="h-6 shimmer rounded w-1/3"></div><div class="h-20 shimmer rounded"></div></div></div>}@else if(product();as p){<div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8"><app-breadcrumb [items]="b()"/><div class="grid grid-cols-1 lg:grid-cols-2 gap-8 lg:gap-12"><div class="aspect-square rounded-2xl overflow-hidden bg-[var(--color-surface-alt)] border border-[var(--color-border)]"><img [src]="p.imageUrls[0]||'assets/placeholder.svg'"[alt]="p.name" class="w-full h-full object-cover"/></div><div class="space-y-4"><h1 class="text-2xl lg:text-3xl font-bold text-[var(--color-text)] font-heading">{{p.name}}</h1><p class="text-sm text-[var(--color-text-secondary)]">SKU: {{p.sku}}</p><div class="text-3xl font-bold text-[var(--color-primary)]">{{p.price|appCurrency}}</div><div>@if(p.stockQuantity>10){<app-badge variant="success">In Stock</app-badge>}@else if(p.stockQuantity>0){<app-badge variant="warning">Only {{p.stockQuantity}} left</app-badge>}@else{<app-badge variant="error">Out of Stock</app-badge>}</div><div class="flex items-center gap-4"><label class="text-sm font-medium text-[var(--color-text)]">Quantity:</label><div class="flex items-center border border-[var(--color-border)] rounded-xl overflow-hidden"><button (click)="dec()" class="px-3 py-2 text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-alt)] transition-colors cursor-pointer" aria-label="Decrease"><span class="material-icons text-base">remove</span></button><span class="px-4 py-2 text-sm font-medium text-[var(--color-text)] min-w-[3rem] text-center border-x border-[var(--color-border)]">{{qty()}}</span><button (click)="inc()" class="px-3 py-2 text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-alt)] transition-colors cursor-pointer" aria-label="Increase"><span class="material-icons text-base">add</span></button></div></div><button (click)="add(p)"[disabled]="p.stockQuantity<=0" class="w-full px-6 py-3 bg-[var(--color-primary)] text-white rounded-xl text-sm font-bold hover:bg-[var(--color-primary-hover)] disabled:opacity-50 disabled:cursor-not-allowed transition-all cursor-pointer shadow-sm hover:shadow">{{p.stockQuantity<=0?'Sold Out':'Add to Cart - '+((p.price*qty())|appCurrency)}}</button><div class="pt-6 border-t border-[var(--color-border)]"><h2 class="text-lg font-semibold text-[var(--color-text)] font-heading mb-3">Description</h2><p class="text-sm text-[var(--color-text-secondary)] leading-relaxed whitespace-pre-wrap">{{p.description}}</p></div></div></div></div>}`})
export class ProductDetailComponent implements OnInit {
  private route = inject(ActivatedRoute); private ps = inject(ProductService);
  private cs = inject(CartService); private n = inject(NotificationService);
  readonly product = signal<Product|undefined>(undefined); readonly loading = signal(true);
  readonly qty = signal(1); readonly b = signal([{label:'Home',url:'/'},{label:'Products',url:'/products'},{label:'Details'}]);
  ngOnInit() { const id=this.route.snapshot.paramMap.get('id'); if(id)this.ps.getProduct(id).subscribe({next:(p)=>{this.product.set(p);this.b.set([{label:'Home',url:'/'},{label:'Products',url:'/products'},{label:p.name}]);this.loading.set(false);},error:()=>this.loading.set(false)}); }
  protected inc() { this.qty.update(q=>Math.min(q+1,this.product()?.stockQuantity||99)); }
  protected dec() { this.qty.update(q=>Math.max(q-1,1)); }
  protected add(p:Product) { this.cs.addItem({productId:p.id,sku:p.sku,name:p.name,imageUrl:p.imageUrls[0]||'',unitPrice:p.price,quantity:this.qty(),stockQuantity:p.stockQuantity}); this.n.show(`${p.name} added to cart!`,'success'); }
}
```

- [ ] **Step 3: Commit**

```bash
npx ng build && git add src/app/features/products/ && git commit -m "feat: add product list and detail pages"
```

---

### Task 13: Create Cart Page

- [ ] **Step 1: `features/cart/cart.component.ts`**

```typescript
import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CartService } from '../../core/services/cart.service';
import { EmptyState } from '../../shared/components/empty-state/empty-state.component';
import { Breadcrumb } from '../../shared/components/breadcrumb/breadcrumb.component';
import { CurrencyPipe } from '../../shared/pipes/currency.pipe';

@Component({ selector: 'app-cart', standalone: true, imports: [RouterLink, EmptyState, Breadcrumb, CurrencyPipe],
  template: `
<div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
  <app-breadcrumb [items]="[{label:'Home',url:'/'},{label:'Cart'}]"/>
  @if(cart.items().length===0){<app-empty-state icon="shopping_cart" title="Your cart is empty" message="Looks like you haven't added anything yet." actionLabel="Start Shopping"/>}@else{
    @if(cart.subtotal()<50){<div class="mb-6 p-4 bg-[var(--color-primary)]/5 rounded-xl border border-[var(--color-primary)]/20 text-sm text-[var(--color-primary)] flex items-center gap-2"><span class="material-icons text-lg">local_shipping</span>Add {{50-cart.subtotal()|appCurrency}} more for free shipping!</div>}
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
      <div class="lg:col-span-2 space-y-4">
        @for(item of cart.items();track item.productId){
          <div class="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4 flex gap-4 items-start hover:shadow-sm transition-shadow">
            <img [src]="item.imageUrl||'assets/placeholder.svg'"[alt]="item.name" class="w-20 h-20 object-cover rounded-lg bg-[var(--color-surface-alt)]" loading="lazy"/>
            <div class="flex-1 min-w-0"><a [routerLink]="['/products',item.productId]" class="font-medium text-[var(--color-text)] hover:text-[var(--color-primary)] transition-colors">{{item.name}}</a><p class="text-sm text-[var(--color-text-secondary)] mt-0.5">{{item.unitPrice|appCurrency}} each</p><div class="flex items-center gap-4 mt-3"><div class="flex items-center border border-[var(--color-border)] rounded-lg overflow-hidden"><button (click)="dec(item)" class="px-2 py-1 text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-alt)] transition-colors cursor-pointer"><span class="material-icons text-sm">remove</span></button><span class="px-3 py-1 text-sm font-medium text-[var(--color-text)] border-x border-[var(--color-border)]">{{item.quantity}}</span><button (click)="inc(item)" class="px-2 py-1 text-[var(--color-text-secondary)] hover:bg-[var(--color-surface-alt)] transition-colors cursor-pointer"><span class="material-icons text-sm">add</span></button></div><button (click)="remove(item.productId)" class="text-sm text-[var(--color-error)] hover:underline transition-colors cursor-pointer">Remove</button></div></div>
            <div class="text-right shrink-0"><p class="font-semibold text-[var(--color-text)]">{{item.unitPrice*item.quantity|appCurrency}}</p></div>
          </div>
        }
      </div>
      <div><div class="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-6 sticky top-24"><h3 class="text-lg font-semibold text-[var(--color-text)] font-heading mb-4">Order Summary</h3><div class="space-y-3 text-sm"><div class="flex justify-between"><span class="text-[var(--color-text-secondary)]">Subtotal</span><span class="font-medium">{{cart.subtotal()|appCurrency}}</span></div><div class="flex justify-between"><span class="text-[var(--color-text-secondary)]">Shipping</span><span class="font-medium">{{cart.subtotal()>=50?'FREE':(5.99|appCurrency)}}</span></div><div class="border-t border-[var(--color-border)] pt-3 flex justify-between font-semibold"><span>Total</span><span class="font-bold text-lg text-[var(--color-primary)]">{{(cart.subtotal()+(cart.subtotal()>=50?0:5.99))|appCurrency}}</span></div></div><a routerLink="/checkout" class="mt-6 w-full flex items-center justify-center px-4 py-3 bg-[var(--color-primary)] text-white rounded-xl text-sm font-bold hover:bg-[var(--color-primary-hover)] transition-all cursor-pointer shadow-sm hover:shadow">Proceed to Checkout</a></div></div>
    </div>
  }
</div>`})
export class CartComponent {
  protected cart = inject(CartService);
  protected inc(i: {productId:string;quantity:number}) { this.cart.updateQuantity(i.productId, i.quantity+1); }
  protected dec(i: {productId:string;quantity:number}) { this.cart.updateQuantity(i.productId, i.quantity-1); }
  protected remove(id:string) { this.cart.removeItem(id); }
}
```

- [ ] **Step 2: Commit**

```bash
npx ng build && git add src/app/features/cart/ && git commit -m "feat: add cart page with shipping threshold banner"
```

---

### Task 14: Create Checkout Page

- [ ] **Step 1: `features/checkout/checkout.component.ts`**

```typescript
import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CartService } from '../../core/services/cart.service';
import { OrderService } from '../../core/services/order.service';
import { NotificationService } from '../../core/services/notification.service';
import { Breadcrumb } from '../../shared/components/breadcrumb/breadcrumb.component';
import { CurrencyPipe } from '../../shared/pipes/currency.pipe';
import { ShippingAddress } from '../../models';

@Component({ selector: 'app-checkout', standalone: true, imports: [RouterLink, FormsModule, Breadcrumb, CurrencyPipe],
  template: `@if(cart.items().length===0){<div class="max-w-7xl mx-auto px-4 py-16 text-center"><h2 class="text-xl font-semibold text-[var(--color-text)] font-heading mb-2">Your cart is empty</h2><p class="text-[var(--color-text-secondary)] mb-4">Add some items first.</p><a routerLink="/products" class="text-[var(--color-primary)] hover:underline">Browse Products</a></div>}@else{<div class="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8"><app-breadcrumb [items]="[{label:'Home',url:'/'},{label:'Cart',url:'/cart'},{label:'Checkout'}]"/><h1 class="text-2xl font-bold text-[var(--color-text)] font-heading mb-8">Checkout</h1>
<div class="flex items-center gap-2 mb-8">@for(s of['Shipping','Payment','Review'];track$index){<div class="flex items-center gap-2"><div class="w-8 h-8 rounded-full flex items-center justify-center text-sm font-bold"[class]="$index<=step()?'bg-[var(--color-primary)] text-white':'bg-[var(--color-surface-alt)] text-[var(--color-text-secondary)]'">{{$index+1}}</div><span class="text-sm font-medium hidden sm:inline"[class]="$index===step()?'text-[var(--color-text)] font-semibold':'text-[var(--color-text-secondary)]'">{{s}}</span>@if($index<2){<div class="w-8 h-0.5"[class]="$index<step()?'bg-[var(--color-primary)]':'bg-[var(--color-border)]'"></div>}</div>}</div>
<div class="grid grid-cols-1 lg:grid-cols-3 gap-8"><div class="lg:col-span-2">@switch(step()){@case(0){<div class="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-6"><h2 class="text-lg font-semibold text-[var(--color-text)] font-heading mb-4">Shipping Address</h2><div class="grid grid-cols-1 sm:grid-cols-2 gap-4"><div class="sm:col-span-2"><input [(ngModel)]="addr.fullName" placeholder="Full Name" class="w-full px-3 py-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]" aria-label="Full Name"/></div><div class="sm:col-span-2"><input [(ngModel)]="addr.phone" placeholder="Phone" class="w-full px-3 py-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]" aria-label="Phone"/></div><div class="sm:col-span-2"><input [(ngModel)]="addr.street" placeholder="Street Address" class="w-full px-3 py-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]" aria-label="Street"/></div><div><input [(ngModel)]="addr.city" placeholder="City" class="w-full px-3 py-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]" aria-label="City"/></div><div><input [(ngModel)]="addr.state" placeholder="State" class="w-full px-3 py-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]" aria-label="State"/></div><div><input [(ngModel)]="addr.zipCode" placeholder="ZIP" class="w-full px-3 py-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]" aria-label="ZIP"/></div><div><input [(ngModel)]="addr.country" placeholder="Country" class="w-full px-3 py-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]" aria-label="Country"/></div></div><div class="mt-6 flex justify-end"><button (click)="next()" class="px-6 py-2.5 bg-[var(--color-primary)] text-white rounded-xl text-sm font-bold hover:bg-[var(--color-primary-hover)] transition-all cursor-pointer shadow-sm">Continue</button></div></div>}@case(1){<div class="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-6"><h2 class="text-lg font-semibold text-[var(--color-text)] font-heading mb-4">Payment Method</h2><div class="space-y-3">@for(m of['Credit Card','PayPal','Cash on Delivery'];track m){<label class="flex items-center gap-3 p-3 rounded-xl border transition-all cursor-pointer"[class]="pay()===m?'border-[var(--color-primary)] bg-[var(--color-primary)]/5':'border-[var(--color-border)] hover:border-[var(--color-primary)]'"><input type="radio" name="pm"[value]="m"[(ngModel)]="pay" class="accent-[var(--color-primary)]"/><span class="text-sm font-medium text-[var(--color-text)]">{{m}}</span></label>}</div><div class="mt-6 flex justify-between"><button (click)="prev()" class="px-4 py-2 text-sm font-medium text-[var(--color-text-secondary)] hover:text-[var(--color-text)] transition-colors cursor-pointer">Back</button><button (click)="next()" class="px-6 py-2.5 bg-[var(--color-primary)] text-white rounded-xl text-sm font-bold hover:bg-[var(--color-primary-hover)] transition-all cursor-pointer shadow-sm">Review</button></div></div>}@case(2){<div class="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-6"><h2 class="text-lg font-semibold text-[var(--color-text)] font-heading mb-4">Review Order</h2><div class="space-y-4 mb-4"><div><h3 class="text-xs font-bold text-[var(--color-text-secondary)] uppercase tracking-widest mb-1 font-heading">Shipping</h3><p class="text-sm text-[var(--color-text)]">{{addr.fullName}}, {{addr.street}}, {{addr.city}}, {{addr.state}} {{addr.zipCode}}</p></div><div><h3 class="text-xs font-bold text-[var(--color-text-secondary)] uppercase tracking-widest mb-1 font-heading">Payment</h3><p class="text-sm text-[var(--color-text)]">{{pay()}}</p></div><div><h3 class="text-xs font-bold text-[var(--color-text-secondary)] uppercase tracking-widest mb-1 font-heading">Items</h3>@for(i of cart.items();track i.productId){<div class="flex justify-between text-sm py-1"><span class="text-[var(--color-text)]">{{i.name}} x{{i.quantity}}</span><span class="font-medium">{{i.unitPrice*i.quantity|appCurrency}}</span></div>}</div></div><div class="flex justify-between"><button (click)="prev()" class="px-4 py-2 text-sm font-medium text-[var(--color-text-secondary)] hover:text-[var(--color-text)] transition-colors cursor-pointer">Back</button><button (click)="place()"[disabled]="submitting()" class="px-6 py-2.5 bg-[var(--color-primary)] text-white rounded-xl text-sm font-bold hover:bg-[var(--color-primary-hover)] disabled:opacity-50 transition-all cursor-pointer shadow-sm">{{submitting()?'Placing...':'Place Order'}}</button></div></div>}}</div>
<div class="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-6 h-fit sticky top-24"><h3 class="text-lg font-semibold text-[var(--color-text)] font-heading mb-4">Summary</h3><div class="space-y-2 text-sm">@for(i of cart.items();track i.productId){<div class="flex justify-between"><span class="text-[var(--color-text-secondary)] truncate max-w-[12rem]">{{i.name}} x{{i.quantity}}</span><span class="font-medium">{{i.unitPrice*i.quantity|appCurrency}}</span></div>}</div><div class="border-t border-[var(--color-border)] mt-4 pt-4 space-y-2"><div class="flex justify-between"><span class="text-[var(--color-text-secondary)]">Subtotal</span><span class="font-medium">{{cart.subtotal()|appCurrency}}</span></div><div class="flex justify-between"><span class="text-[var(--color-text-secondary)]">Shipping</span><span class="font-medium">{{cart.subtotal()>=50?'FREE':(5.99|appCurrency)}}</span></div><div class="flex justify-between font-semibold border-t border-[var(--color-border)] pt-2"><span>Total</span><span class="text-[var(--color-primary)]">{{(cart.subtotal()+(cart.subtotal()>=50?0:5.99))|appCurrency}}</span></div></div></div></div></div>}`})
export class CheckoutComponent {
  private cart = inject(CartService); private os = inject(OrderService); private n = inject(NotificationService); private router = inject(Router);
  protected step = signal(0); protected submitting = signal(false);
  protected addr: ShippingAddress = {fullName:'',phone:'',street:'',city:'',state:'',zipCode:'',country:'US'};
  protected pay = signal('Credit Card');
  protected next() { if(this.step()<2)this.step.update(s=>s+1); }
  protected prev() { if(this.step()>0)this.step.update(s=>s-1); }
  protected place() {
    this.submitting.set(true);
    this.os.createOrder({items:this.cart.items().map(i=>({productId:i.productId,quantity:i.quantity})),shippingAddress:this.addr,paymentMethod:this.pay()}).subscribe({
      next:(o)=>{this.cart.clear();this.n.show(`Order #${o.orderNumber} placed!`,'success');this.router.navigate(['/orders',o.id]);},
      error:()=>{this.n.show('Failed to place order.','error');this.submitting.set(false);},
    });
  }
}
```

- [ ] **Step 3: Commit**

```bash
npx ng build && git add src/app/features/checkout/ && git commit -m "feat: add multi-step checkout page"
```

---

### Task 15: Create Order Pages

- [ ] **Step 1: `features/orders/order-list/order-list.component.ts`**

```typescript
import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { OrderService } from '../../../core/services/order.service';
import { EmptyState } from '../../../shared/components/empty-state/empty-state.component';
import { Breadcrumb } from '../../../shared/components/breadcrumb/breadcrumb.component';
import { CurrencyPipe } from '../../../shared/pipes/currency.pipe';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { Order, OrderStatus } from '../../../models';

@Component({ selector: 'app-order-list', standalone: true, imports: [RouterLink, EmptyState, Breadcrumb, CurrencyPipe, RelativeTimePipe],
  template: `<div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8"><app-breadcrumb [items]="[{label:'Home',url:'/'},{label:'My Orders'}]"/><h1 class="text-2xl font-bold text-[var(--color-text)] font-heading mb-6">My Orders</h1>@if(loading()){<div class="space-y-4">@for(_ of[1,2,3];track _){<div class="animate-pulse h-24 rounded-xl bg-[var(--color-surface-alt)]"></div>}</div>}@else if(orders().length===0){<app-empty-state icon="receipt_long" title="No orders yet" message="Your order history will appear here." actionLabel="Start Shopping"/>}@else{<div class="space-y-3">@for(o of orders();track o.id){<a [routerLink]="['/orders',o.id]" class="block"><div class="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-4 sm:p-6 flex items-center justify-between hover:shadow-md hover:-translate-y-0.5 transition-all cursor-pointer group"><div><p class="font-medium text-[var(--color-text)] group-hover:text-[var(--color-primary)] transition-colors">{{o.orderNumber}}</p><p class="text-sm text-[var(--color-text-secondary)] mt-0.5">{{o.createdAt|appRelativeTime}}</p><p class="text-sm text-[var(--color-text-secondary)]">{{o.items.length}} item(s)</p></div><div class="text-right"><p class="font-bold text-[var(--color-text)]">{{o.totalAmount|appCurrency}}</p><span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium mt-1"[class]="sc(o.status)">{{o.status}}</span></div></div></a>}</div>}</div>`})
export class OrderListComponent implements OnInit {
  private os = inject(OrderService); readonly orders = signal<Order[]>([]); readonly loading = signal(true);
  ngOnInit() { this.os.getOrders().subscribe({ next: (o) => { this.orders.set(o); this.loading.set(false); }, error: () => this.loading.set(false) }); }
  protected sc(s: OrderStatus): string {
    const m: Record<OrderStatus, string> = {PENDING:'bg-[var(--color-warning)]/10 text-[var(--color-warning)]',CONFIRMED:'bg-[var(--color-primary)]/10 text-[var(--color-primary)]',PROCESSING:'bg-[var(--color-primary)]/10 text-[var(--color-primary)]',SHIPPED:'bg-[var(--color-primary)]/10 text-[var(--color-primary)]',DELIVERED:'bg-[var(--color-success)]/10 text-[var(--color-success)]',CANCELLED:'bg-[var(--color-error)]/10 text-[var(--color-error)]',REFUNDED:'bg-[var(--color-error)]/10 text-[var(--color-error)]'};
    return m[s]||'bg-[var(--color-surface-alt)] text-[var(--color-text-secondary)]';
  }
}
```

- [ ] **Step 2: `features/orders/order-detail/order-detail.component.ts`**

```typescript
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { OrderService } from '../../../core/services/order.service';
import { NotificationService } from '../../../core/services/notification.service';
import { Breadcrumb } from '../../../shared/components/breadcrumb/breadcrumb.component';
import { Badge } from '../../../shared/components/badge/badge.component';
import { CurrencyPipe } from '../../../shared/pipes/currency.pipe';
import { RelativeTimePipe } from '../../../shared/pipes/relative-time.pipe';
import { Order, OrderStatus } from '../../../models';

@Component({ selector: 'app-order-detail', standalone: true, imports: [RouterLink,Breadcrumb,Badge,CurrencyPipe,RelativeTimePipe],
  template: `@if(loading()){<div class="max-w-4xl mx-auto px-4 py-8 animate-pulse space-y-4"><div class="h-8 shimmer rounded w-1/3"></div><div class="h-40 shimmer rounded-xl"></div></div>}@else if(order();as o){<div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8"><app-breadcrumb [items]="[{label:'Home',url:'/'},{label:'Orders',url:'/orders'},{label:o.orderNumber}]"/><div class="flex items-center justify-between mb-6"><h1 class="text-2xl font-bold text-[var(--color-text)] font-heading">Order {{o.orderNumber}}</h1><app-badge [variant]="sb(o.status)">{{o.status}}</app-badge></div><div class="grid grid-cols-1 lg:grid-cols-3 gap-8"><div class="lg:col-span-2 space-y-4"><div class="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-6"><h3 class="text-xs font-bold text-[var(--color-text-secondary)] uppercase tracking-widest mb-4 font-heading">Items</h3><div class="divide-y divide-[var(--color-border)]">@for(i of o.items;track i.productId){<div class="flex items-center gap-4 py-3"><img [src]="i.imageUrl||'assets/placeholder.svg'"[alt]="i.productName" class="w-16 h-16 object-cover rounded-lg bg-[var(--color-surface-alt)]" loading="lazy"/><div class="flex-1 min-w-0"><p class="font-medium text-[var(--color-text)] text-sm">{{i.productName}}</p><p class="text-sm text-[var(--color-text-secondary)]">Qty: {{i.quantity}}</p></div><p class="font-medium text-[var(--color-text)]">{{i.totalPrice|appCurrency}}</p></div>}</div></div></div><div class="space-y-4"><div class="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-6"><h3 class="text-xs font-bold text-[var(--color-text-secondary)] uppercase tracking-widest mb-3 font-heading">Details</h3><div class="space-y-2 text-sm"><div class="flex justify-between"><span class="text-[var(--color-text-secondary)]">Subtotal</span><span>{{o.subtotal|appCurrency}}</span></div><div class="flex justify-between"><span class="text-[var(--color-text-secondary)]">Shipping</span><span>{{o.shippingCost|appCurrency}}</span></div><div class="flex justify-between"><span class="text-[var(--color-text-secondary)]">Tax</span><span>{{o.taxAmount|appCurrency}}</span></div><div class="flex justify-between font-semibold border-t border-[var(--color-border)] pt-2"><span>Total</span><span class="text-[var(--color-primary)]">{{o.totalAmount|appCurrency}}</span></div></div></div><div class="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-6"><h3 class="text-xs font-bold text-[var(--color-text-secondary)] uppercase tracking-widest mb-3 font-heading">Shipping</h3><p class="text-sm text-[var(--color-text)]">{{o.shippingAddress.fullName}}<br/>{{o.shippingAddress.street}}<br/>{{o.shippingAddress.city}}, {{o.shippingAddress.state}} {{o.shippingAddress.zipCode}}</p></div><div class="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-6"><h3 class="text-xs font-bold text-[var(--color-text-secondary)] uppercase tracking-widest mb-3 font-heading">Timeline</h3><div class="space-y-2 text-sm"><div class="flex justify-between"><span class="text-[var(--color-text-secondary)]">Placed</span><span>{{o.createdAt|appRelativeTime}}</span></div>@if(o.deliveredAt){<div class="flex justify-between"><span class="text-[var(--color-success)]">Delivered</span><span>{{o.deliveredAt|appRelativeTime}}</span></div>}</div></div>@if(o.status==='PENDING'||o.status==='CONFIRMED'){<button (click)="cancel(o.id)" class="w-full px-4 py-2 border-2 border-[var(--color-error)] text-[var(--color-error)] rounded-xl text-sm font-bold hover:bg-[var(--color-error)]/5 transition-all cursor-pointer">Cancel Order</button>}</div></div></div>}`})
export class OrderDetailComponent implements OnInit {
  private route = inject(ActivatedRoute); private os = inject(OrderService); private n = inject(NotificationService);
  readonly order = signal<Order|undefined>(undefined); readonly loading = signal(true);
  ngOnInit() { const id=this.route.snapshot.paramMap.get('id'); if(id)this.os.getOrder(id).subscribe({next:(o)=>{this.order.set(o);this.loading.set(false);},error:()=>this.loading.set(false)}); }
  protected sb(s: OrderStatus): 'success'|'warning'|'error'|'info'|'default' { const m: Record<OrderStatus,any> = {PENDING:'warning',CONFIRMED:'info',PROCESSING:'info',SHIPPED:'info',DELIVERED:'success',CANCELLED:'error',REFUNDED:'error'}; return m[s]||'default'; }
  protected cancel(id:string) { this.os.cancelOrder(id).subscribe({next:()=>{this.n.show('Order cancelled.','info');this.loading.set(true);this.ngOnInit();},error:()=>this.n.show('Failed to cancel.','error')}); }
}
```

- [ ] **Step 3: Commit**

```bash
npx ng build && git add src/app/features/orders/ && git commit -m "feat: add order list and detail pages"
```

---

### Task 16: Create Profile Page

- [ ] **Step 1: `features/profile/profile.component.ts`**

```typescript
import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { ApiService } from '../../core/services/api.service';
import { NotificationService } from '../../core/services/notification.service';
import { Breadcrumb } from '../../shared/components/breadcrumb/breadcrumb.component';
import { User } from '../../models';

@Component({ selector: 'app-profile', standalone: true, imports: [FormsModule, Breadcrumb],
  template: `
<div class="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
  <app-breadcrumb [items]="[{label:'Home',url:'/'},{label:'Profile'}]"/>
  <h1 class="text-2xl font-bold text-[var(--color-text)] font-heading mb-6">My Profile</h1>
  <div class="rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] p-6 max-w-lg">
    <div class="flex items-center gap-4 mb-6">
      @if(auth.currentUser()?.imageUrl){<img [src]="auth.currentUser()?.imageUrl" class="w-16 h-16 rounded-full object-cover border-2 border-[var(--color-border)]" alt="Profile"/>}@else{<div class="w-16 h-16 rounded-full bg-[var(--color-primary)]/10 flex items-center justify-center border-2 border-[var(--color-primary)]/20"><span class="material-icons text-2xl text-[var(--color-primary)]">person</span></div>}
      <div><h2 class="text-lg font-semibold text-[var(--color-text)] font-heading">{{auth.currentUser()?.displayName||'User'}}</h2><p class="text-sm text-[var(--color-text-secondary)]">{{auth.currentUser()?.email}}</p></div>
    </div>
    <form (ngSubmit)="onSubmit()" class="space-y-4">
      <div><label for="pn" class="block text-sm font-medium text-[var(--color-text)] mb-1.5">Display Name</label><input id="pn" type="text" [(ngModel)]="dn" name="dn" required class="w-full px-3 py-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]"/></div>
      <div><label for="pi" class="block text-sm font-medium text-[var(--color-text)] mb-1.5">Image URL</label><input id="pi" type="url" [(ngModel)]="iu" name="iu" class="w-full px-3 py-2.5 rounded-xl border border-[var(--color-border)] bg-[var(--color-surface)] text-sm focus:outline-none focus:ring-2 focus:ring-[var(--color-primary)]"/></div>
      <button type="submit" [disabled]="saving()" class="px-6 py-2.5 bg-[var(--color-primary)] text-white rounded-xl text-sm font-bold hover:bg-[var(--color-primary-hover)] disabled:opacity-50 transition-all cursor-pointer shadow-sm hover:shadow">{{saving()?'Saving...':'Save Changes'}}</button>
    </form>
  </div>
</div>`})
export class ProfileComponent {
  protected auth = inject(AuthService); private api = inject(ApiService); private n = inject(NotificationService);
  protected dn = ''; protected iu = ''; protected saving = signal(false);
  constructor() { const u=this.auth.currentUser(); if(u){this.dn=u.displayName;this.iu=u.imageUrl||'';} }
  protected onSubmit() {
    if(!this.dn)return; this.saving.set(true);
    this.api.put<User>('/api/user/profile',{displayName:this.dn,imageUrl:this.iu}).subscribe({
      next:(u)=>{this.auth.currentUser.set(u);this.n.show('Profile updated!','success');this.saving.set(false);},
      error:()=>{this.n.show('Failed to update.','error');this.saving.set(false);},
    });
  }
}
```

- [ ] **Step 2: Final build verification**

```bash
npx ng build
```

Fix any import/types issues until build succeeds cleanly.

- [ ] **Step 3: Create placeholder asset**

```bash
mkdir -p src/assets
cat > src/assets/placeholder.svg << 'SVGEOF'
<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400" viewBox="0 0 400 400"><rect fill="#f1f5f9" width="400" height="400"/><text fill="#94a3b8" font-family="sans-serif" font-size="16" text-anchor="middle" x="200" y="205">No Image</text></svg>
SVGEOF
```

- [ ] **Step 4: Final commit**

```bash
git add src/app/features/profile/ src/assets/ && git commit -m "feat: add profile page and placeholder asset"
```

---

### Final Verification

- [ ] **Serve and test**

```bash
npx ng serve --port 4200
```

Open browser at `http://localhost:4200` and verify all routes render.

Expected routes:
- `/` — Home with hero, categories, featured products
- `/products` — Product listing with category filter
- `/products/:id` — Product detail with add-to-cart
- `/cart` — Cart with quantity controls and shipping banner
- `/checkout` — Multi-step checkout (requires auth)
- `/orders` — Order list (requires auth)
- `/orders/:id` — Order detail with cancel (requires auth)
- `/auth/login` — Login form with Google OAuth
- `/auth/register` — Registration form
- `/profile` — Profile edit (requires auth)
