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
