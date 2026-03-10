import { Routes } from '@angular/router';
import { Login } from './auth/login/login';
import { Register } from './auth/register/register';
import { Home } from './home/home';
import { AdminDashboardComponent } from './admin/admin-dashboard';
import { SellerDashboardComponent } from './seller/seller-dashboard';

export const routes: Routes = [
    {path: '', component: Home},
    {path: 'login', component: Login},
    {path: 'register', component: Register},
    {path: 'admin', component: AdminDashboardComponent},
    {path: 'seller', component: SellerDashboardComponent}
];
