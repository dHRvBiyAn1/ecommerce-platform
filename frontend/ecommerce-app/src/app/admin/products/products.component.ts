import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { AdminLayoutComponent } from '../../shared/layout/admin-layout/admin-layout.component';
import { CardComponent } from '../../shared/components/card/card.component';
import { BadgeComponent } from '../../shared/components/badge/badge.component';

@Component({
  selector: 'app-admin-products',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    AdminLayoutComponent,
    CardComponent,
    BadgeComponent
  ],
  templateUrl: './products.component.html',
  styleUrl: './products.component.css'
})
export class AdminProductsComponent {
  products = [
    { id: 1, name: 'ASUS ROG Gaming Laptop', category: 'Laptop', brand: 'ASUS', price: 2199, inStock: false, createdAt: '01 Dec, 2027' },
    { id: 2, name: 'Airpods Pro 2nd Gen', category: 'Accessories', brand: 'Apple', price: 839, inStock: true, createdAt: '29 Jun, 2027' },
    { id: 3, name: 'Apple Watch Ultra', category: 'Watch', brand: 'Apple', price: 1579, inStock: false, createdAt: '13 Mar, 2027' },
    { id: 4, name: 'Bose QuietComfort Earbuds', category: 'Audio', brand: 'Bose', price: 279, inStock: true, createdAt: '18 Nov, 2027' },
    { id: 5, name: 'Canon EOS R5 Camera', category: 'Camera', brand: 'Canon', price: 3899, inStock: true, createdAt: '28 Sep, 2027' },
    { id: 6, name: 'Dell XPS 13 Laptop', category: 'Laptop', brand: 'Dell', price: 1299, inStock: true, createdAt: '18 Aug, 2027' },
    { id: 7, name: 'Google Pixel 8 Pro', category: 'Phone', brand: 'Google', price: 899, inStock: false, createdAt: '02 Sep, 2027' }
  ];
}
