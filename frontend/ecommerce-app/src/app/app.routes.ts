import { Routes } from '@angular/router';
import { Login } from './auth/login/login';
import { Register } from './auth/register/register';
import { Home } from './home/home';
import { AdminDashboardComponent } from './admin/admin-dashboard';
import { SellerDashboardComponent } from './seller/seller-dashboard';
import { AdminProductsComponent } from './admin/products/products.component';
import { AdminAddProductComponent } from './admin/products/add-product/add-product.component';
import { AdminLayoutComponent } from './shared/layout/admin-layout/admin-layout.component';
import { ShopComponent } from './shop/shop.component';
import { ProductDetailComponent } from './shop/product-detail/product-detail.component';

export const routes: Routes = [
    {path: '', component: Home},
    {path: 'login', component: Login},
    {path: 'register', component: Register},
    {path: 'shop', component: ShopComponent},
    {path: 'shop/:category', component: ShopComponent},
    {path: 'product/:id', component: ProductDetailComponent},
    {
      path: 'admin',
      component: AdminLayoutComponent,
      children: [
        {path: '', component: AdminDashboardComponent},
        {path: 'products', component: AdminProductsComponent},
        {path: 'add-product', component: AdminAddProductComponent}
      ]
    },
    {path: 'seller', component: SellerDashboardComponent}
];
