# Seller Access Restriction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restrict sellers to inventory management only — no cart/checkout/browsing. Sellers can only view and edit their own products.

**Architecture:** A `customerGuard` blocks `ROLE_SELLER` from customer routes. Topbar and sidebar hide customer nav for sellers. Product details shows read-only for sellers with an "Edit" link. A new edit-product page pre-fills from existing product data.

**Tech Stack:** Angular 21, PrimeNG 21, route guards, standalone components

---

### Task 1: Customer Route Guard

**Files:**
- Create: `src/app/guards/customer.guard.ts`

- [ ] **Create `customer.guard.ts`**

```typescript
import { inject } from '@angular/core';
import { Router, type ActivatedRouteSnapshot } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const customerGuard = (_route: ActivatedRouteSnapshot) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const user = authService.user();
  if (user?.roles.includes('ROLE_SELLER')) {
    return router.parseUrl('/seller');
  }

  return true;
};
```

- [ ] **Build check**: `cd frontend && npx tsc --noEmit` — passes

---

### Task 2: Update Routes

**Files:**
- Modify: `src/app/app.routes.ts`

- [ ] **Add import and apply `customerGuard` to customer routes**

```typescript
// Add with other imports at top:
import { customerGuard } from './guards/customer.guard';

// Updated routes - add customerGuard to these:
{ path: 'products', component: ProductListing, canActivate: [customerGuard] },
{ path: 'product', component: ProductDetails, canActivate: [customerGuard] },
{ path: 'cart', component: ShoppingCart, canActivate: [customerGuard] },
{ path: 'checkout', component: Checkout, canActivate: [authGuard, customerGuard] },
{ path: 'order-success', component: OrderSuccess, canActivate: [authGuard, customerGuard] },

// Add edit-product route after add-product:
{ path: 'seller/edit-product', component: EditProduct, canActivate: [authGuard, roleGuard], data: { roles: ['ROLE_SELLER'] } },
```

- [ ] **Add `EditProduct` import**:
```typescript
import { EditProduct } from './pages/edit-product/edit-product';
```

- [ ] **Build check**: `cd frontend && npx tsc --noEmit` — passes

---

### Task 3: Hide Customer Nav in Topbar

**Files:**
- Modify: `src/app/layout/component/app-topbar.ts`

- [ ] **Add `computed` import and `isSeller` signal**

In the class body, add:
```typescript
protected isSeller = computed(() => {
  const user = this.authService.user();
  return user?.roles.includes('ROLE_SELLER') ?? false;
});
```

Update import to add `computed`:
```typescript
import { Component, inject, computed } from '@angular/core';
```

- [ ] **Hide "Products" nav link and cart for seller**

In template, wrap the Products link with `@if (!isSeller())`:
```html
@if (!isSeller()) {
  <a routerLink="/products" ...>...</a>
}
```

Wrap cart button with `@if (!isSeller())`:
```html
@if (!isSeller()) {
  <button pButton class="p-button-text p-relative" icon="pi pi-shopping-cart" routerLink="/cart">
    @if (cartCount() > 0) {
      <p-badge [value]="cartCount()" severity="danger" class="absolute -top-1 -right-1"></p-badge>
    }
  </button>
}
```

- [ ] **Build check**: `cd frontend && npx tsc --noEmit` — passes

---

### Task 4: Filter Sidebar Menu for Seller

**Files:**
- Modify: `src/app/layout/component/app-sidebar.ts`

- [ ] **Remove "Products" from public menu items for seller**

Update the `visibleItems` computed to filter out customer-only menu items when the user is a seller:

```typescript
protected visibleItems = computed(() => {
    const user = this.authService.user();
    const roles: string[] = user?.roles ?? [];
    const isAuth = this.authService.isAuthenticated();

    let items = [...publicMenuItems];

    // Remove customer-only menu items for sellers
    if (roles.includes('ROLE_SELLER')) {
      items = items.filter(m => m.routerLink[0] !== 'products');
    }

    if (isAuth) items.push(...authMenuItems);

    if (roles.includes('ROLE_SELLER') || roles.includes('ROLE_ADMIN')) {
      items.push(...sellerMenuItems);
    }
    if (roles.includes('ROLE_ADMIN')) {
      items.push(...adminMenuItems);
    }

    return items;
  });
```

- [ ] **Build check**: `cd frontend && npx tsc --noEmit` — passes

---

### Task 5: Product Details — Hide Purchase Actions for Seller

**Files:**
- Modify: `src/app/pages/product-details/product-details.ts`
- Modify: `src/app/pages/product-details/product-details.html`

- [ ] **Add `AuthService` injection and `isSeller` signal**

In `product-details.ts`:
```typescript
import { AuthService } from '../../services/auth.service';

// In class:
protected authService = inject(AuthService);

protected isSeller = computed(() => {
  const user = this.authService.user();
  return user?.roles.includes('ROLE_SELLER') ?? false;
});
```

Also add `computed` to Angular imports if not already there.

- [ ] **In template, wrap purchase actions with `@if (!isSeller())`**

In `product-details.html`, wrap lines 65-86 (quantity selector, add-to-cart, buy-now, shield text) with:
```html
@if (!isSeller()) {
  <!-- existing quantity selector, cart buttons, shield text -->
} @else {
  <div class="rounded-xl border border-surface-border bg-surface-card p-6 flex flex-col gap-4">
    <p class="text-sm text-surface-500 text-center">This is your product listing. <a routerLink="/seller/edit-product?id={{product.id}}" class="text-primary hover:underline font-medium">Edit product</a></p>
  </div>
}
```

- [ ] **Build check**: `cd frontend && npx tsc --noEmit` — passes

---

### Task 6: Edit Product Page

**Files:**
- Create: `src/app/pages/edit-product/edit-product.ts`
- Create: `src/app/pages/edit-product/edit-product.html`

- [ ] **Create `edit-product.ts`**

```typescript
import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { CardModule } from 'primeng/card';
import { ApiService, Product } from '../../services/api.service';

@Component({
  selector: 'app-edit-product',
  imports: [
    RouterLink,
    CommonModule,
    FormsModule,
    ButtonModule,
    InputTextModule,
    TextareaModule,
    CardModule,
  ],
  templateUrl: './edit-product.html',
})
export class EditProduct implements OnInit {
  private apiService = inject(ApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  productId = '';
  name = '';
  price = 0;
  description = '';
  stockQuantity = 0;
  brand = '';
  category = 'Electronics';
  imageUrl = '';
  sku = '';

  successMessage = '';
  errorMessage = '';
  loading = true;

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      const id = params['id'];
      if (!id) {
        this.router.navigate(['/seller/inventory']);
        return;
      }
      this.productId = id;
      this.apiService.getProductById(id).subscribe(prod => {
        if (!prod) {
          this.router.navigate(['/seller/inventory']);
          return;
        }
        this.name = prod.name;
        this.price = prod.price;
        this.description = prod.description || '';
        this.stockQuantity = prod.stockQuantity;
        this.brand = prod.brand || '';
        this.category = prod.category || 'Electronics';
        this.imageUrl = prod.imageUrls?.[0] || '';
        this.sku = prod.sku || '';
        this.loading = false;
      });
    });
  }

  onSubmit() {
    if (!this.name.trim() || !this.price || !this.stockQuantity) {
      this.errorMessage = 'Please complete all required fields (Name, Price, Stock Quantity).';
      this.successMessage = '';
      return;
    }

    this.apiService.updateProduct(this.productId, {
      name: this.name,
      price: this.price,
      description: this.description,
      stockQuantity: this.stockQuantity,
      brand: this.brand,
      category: this.category,
      imageUrl: this.imageUrl,
      sku: this.sku,
    });

    this.successMessage = 'Product updated successfully!';
    this.errorMessage = '';

    setTimeout(() => {
      this.router.navigate(['/seller/inventory']);
    }, 1500);
  }
}
```

- [ ] **Create `edit-product.html`** (reuse add-product template with adjusted title/button)

```html
<div class="max-w-7xl mx-auto flex flex-col gap-8 pb-16">
  @if (loading) {
    <div class="py-24 text-center">
      <i class="pi pi-spin pi-spinner text-3xl text-primary mb-4 block mx-auto"></i>
      <p class="text-surface-500">Loading product...</p>
    </div>
  } @else {
    <div class="flex flex-col md:flex-row md:items-end justify-between gap-4">
      <div>
        <div class="flex items-center gap-2 text-surface-500 text-xs mb-2">
          <a class="hover:text-primary transition-colors" routerLink="/seller">Seller Dashboard</a>
          <i class="pi pi-chevron-right" style="font-size: 0.75rem"></i>
          <a class="hover:text-primary transition-colors" routerLink="/seller/inventory">Inventory</a>
          <i class="pi pi-chevron-right" style="font-size: 0.75rem"></i>
          <span class="text-surface-900 dark:text-surface-0 font-semibold">Edit Product</span>
        </div>
        <h1 class="text-3xl md:text-4xl font-bold text-surface-900 dark:text-surface-0 tracking-tight">Edit Product</h1>
        <p class="text-surface-500 mt-2 text-sm">Update product details in the Glacier catalog.</p>
      </div>
      <div class="flex gap-3">
        <button pButton class="p-button-outlined" routerLink="/seller/inventory" label="Cancel"></button>
        <button pButton (click)="onSubmit()" label="Save Changes"></button>
      </div>
    </div>

    @if (successMessage) {
      <div class="p-4 rounded-lg bg-emerald-500/10 border border-emerald-500/20 text-emerald-500 text-sm font-medium flex items-center gap-3">
        <i class="pi pi-check-circle" style="font-size: 1.25rem"></i>
        {{ successMessage }}
      </div>
    }
    @if (errorMessage) {
      <div class="p-4 rounded-lg bg-red-500/10 border border-red-500/20 text-red-500 text-sm font-medium flex items-center gap-3">
        <i class="pi pi-exclamation-circle" style="font-size: 1.25rem"></i>
        {{ errorMessage }}
      </div>
    }

    <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
      <div class="lg:col-span-2 space-y-8">
        <p-card header="Basic Information">
          <div class="space-y-6">
            <div>
              <label class="block text-sm font-medium text-surface-900 dark:text-surface-0 mb-1">Product Title <span class="text-red-500">*</span></label>
              <input pInputText type="text" placeholder="e.g., Titanium Pro Smartwatch" [(ngModel)]="name" class="w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium text-surface-900 dark:text-surface-0 mb-1">Description</label>
              <textarea pInputTextarea placeholder="Provide product features, sizing, specifications..." rows="4" [(ngModel)]="description" class="w-full"></textarea>
            </div>
            <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
              <div>
                <label class="block text-sm font-medium text-surface-900 dark:text-surface-0 mb-1">Category <span class="text-red-500">*</span></label>
                <select pInputText [(ngModel)]="category" class="w-full">
                  <option value="Electronics">Electronics</option>
                  <option value="Apparel">Apparel</option>
                  <option value="Wearables">Wearables</option>
                  <option value="Audio">Audio</option>
                  <option value="Home & Kitchen">Home & Kitchen</option>
                </select>
              </div>
              <div>
                <label class="block text-sm font-medium text-surface-900 dark:text-surface-0 mb-1">Brand</label>
                <input pInputText type="text" placeholder="Brand Name" [(ngModel)]="brand" class="w-full" />
              </div>
            </div>
          </div>
        </p-card>

        <p-card header="Product Image Links">
          <div>
            <label class="block text-sm font-medium text-surface-900 dark:text-surface-0 mb-1">Image Web URL</label>
            <input pInputText type="text" placeholder="e.g. https://images.unsplash.com/photo-..." [(ngModel)]="imageUrl" class="w-full" />
            <p class="text-xs text-surface-500 mt-2">Provide a working image URL for the product.</p>
          </div>
        </p-card>
      </div>

      <div class="space-y-8">
        <p-card header="Pricing Details">
          <div>
            <label class="block text-sm font-medium text-surface-900 dark:text-surface-0 mb-1">Base Price ($) <span class="text-red-500">*</span></label>
            <div class="relative">
              <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <span class="text-surface-500 text-sm">$</span>
              </div>
              <input pInputText class="w-full pl-8" type="number" placeholder="0.00" [(ngModel)]="price" />
            </div>
          </div>
        </p-card>

        <p-card header="Inventory Settings">
          <div class="space-y-5">
            <div>
              <label class="block text-sm font-medium text-surface-900 dark:text-surface-0 mb-1">SKU (Stock Keeping Unit)</label>
              <input pInputText type="text" placeholder="e.g. GL-PRO-100" [(ngModel)]="sku" class="w-full" />
            </div>
            <div>
              <label class="block text-sm font-medium text-surface-900 dark:text-surface-0 mb-1">Available Units <span class="text-red-500">*</span></label>
              <input pInputText type="number" placeholder="e.g. 50" [(ngModel)]="stockQuantity" class="w-full" />
            </div>
          </div>
        </p-card>
      </div>
    </div>
  }
</div>
```

- [ ] **Build check**: `cd frontend && npx tsc --noEmit` — passes

---

### Task 7: Add `updateProduct` Method to API Service

**Files:**
- Modify: `src/app/services/api.service.ts`

- [ ] **Add `updateProduct` method after `updateProductStockAndPrice`**

```typescript
  updateProduct(id: string, data: {
    name: string;
    price: number;
    description: string;
    stockQuantity: number;
    brand: string;
    category: string;
    imageUrl: string;
    sku: string;
  }) {
    const request = {
      sku: data.sku,
      name: data.name,
      description: data.description,
      categoryId: data.category.toLowerCase().replace(/\s+/g, '-'),
      price: data.price,
      stockQuantity: data.stockQuantity,
      imageUrls: [data.imageUrl],
    };
    this.http.put<ApiResponse<Product>>(`${this.apiUrl}/api/v1/products/${id}`, request).pipe(
      map(response => this.mapProduct(response.data)),
      catchError(() => of(null)),
    ).subscribe(updated => {
      if (updated) {
        const list = this.productsSubject.value.map(p => p.id === id ? updated : p);
        this.productsSubject.next(list);
      }
    });
  }
```

- [ ] **Build check**: `cd frontend && npx tsc --noEmit` — passes

---

### Task 8: Full Build Verification

- [ ] **Run full build**:
```bash
cd frontend && ng build
```
Expected: Build succeeds with no errors.

- [ ] **Verify output**: Check that no new warnings appear. Budget should be within limits.
