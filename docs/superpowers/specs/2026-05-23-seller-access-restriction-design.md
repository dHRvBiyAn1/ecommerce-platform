# Seller Access Restriction Design

## Goal
Restrict sellers to inventory management only — no purchasing, no browsing all products, no cart/checkout. Sellers can only view and edit their own products.

## Approach
A `customerGuard` blocks seller role from customer-facing routes. Navigation hides customer links for sellers. Product details renders as read-only (no cart/buy actions) for sellers. A dedicated edit-product page allows full editing of existing products.

## Changes

### 1. Customer Route Guard (`src/app/guards/customer.guard.ts`)
- Blocks `ROLE_SELLER` from routes: `/products`, `/product`, `/cart`, `/checkout`, `/order-success`
- Redirects seller to `/seller`
- Allows all other users (anonymous, `ROLE_CUSTOMER`) to proceed

### 2. Topbar (`src/app/layout/component/app-topbar.ts`)
- Hide "Products" nav link if user is a seller
- Hide cart icon if user is a seller
- Seller menu still shows Profile/Logout dropdown

### 3. Sidebar Menu (`src/app/layout/component/app-sidebar.ts`)
- Filter out `publicMenuItems` entries that are customer-only (like "Products") when user is a seller
- Keep "Home" visible for sellers

### 4. Product Details Page (`src/app/pages/product-details/`)
- Hide "Add to Cart" and "Buy Now" buttons if user is a seller
- Show seller-facing "Edit Product" link instead (redirects to edit page)
- Product info, images, description still visible as read-only preview

### 5. Routes (`src/app/app.routes.ts`)
- Add `customerGuard` to customer-facing routes
- Add edit-product route: `/seller/edit-product?id={id}`

### 6. Edit Product Page (`src/app/pages/edit-product/`)
- Reuses add-product template but pre-fills from existing product via `ApiService.getProductById()`
- Fields: name, price, description, stock, brand, category, imageUrl, sku
- On save, calls `ApiService.updateProductStockAndPrice()` + additional update logic
- Redirects to `/seller/inventory` on success
- Accessible only to `ROLE_SELLER` (via `authGuard` + `roleGuard`)

### 7. API Service (`src/app/services/api.service.ts`)
- Add `updateProduct()` method (or extend existing update for all editable fields)
- Add `getProductById()` (may already exist)

## Routes Protected by `customerGuard`
| Route | Guard |
|-------|-------|
| `/products` | `customerGuard` |
| `/product` | `customerGuard` |
| `/cart` | `customerGuard` |
| `/checkout` | `customerGuard` |
| `/order-success` | `customerGuard` |

## Routes Only for Sellers
| Route | Guard |
|-------|-------|
| `/seller` | `authGuard` + `roleGuard(['ROLE_SELLER'])` |
| `/seller/inventory` | `authGuard` + `roleGuard(['ROLE_SELLER'])` |
| `/seller/add-product` | `authGuard` + `roleGuard(['ROLE_SELLER'])` |
| `/seller/edit-product` | `authGuard` + `roleGuard(['ROLE_SELLER'])` |

## Testing
- Sign in as admin@example.com → admin dashboard, no customer restrictions
- Sign in as seller → no Products link, no cart, cannot reach customer routes, redirected to /seller
- Sign in as regular user → full customer experience unchanged
