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
import { EditProduct } from './pages/edit-product/edit-product';
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
  { path: 'seller/edit-product', component: EditProduct, canActivate: [authGuard, roleGuard], data: { roles: ['ROLE_SELLER'] } },
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
