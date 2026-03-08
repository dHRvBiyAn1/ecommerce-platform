import { Routes } from '@angular/router';

export const routes: Routes = [
    {path: 'login', loadComponent: () => import('./auth/login/login').then(m => m.Login)},
    {path: 'register', loadComponent: () => import('./auth/register/register').then(m => m.Register)},
];
