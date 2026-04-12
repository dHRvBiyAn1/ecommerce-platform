import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CardComponent } from '../../shared/components/card/card.component';
import { BadgeComponent } from '../../shared/components/badge/badge.component';
import { PageHeaderComponent, BreadcrumbItem } from '../../shared/components/page-header/page-header.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { PaginationComponent } from '../../shared/components/pagination/pagination.component';

@Component({
  selector: 'app-admin-products',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    FormsModule,
    CardComponent,
    BadgeComponent,
    PageHeaderComponent,
    IconComponent,
    PaginationComponent
  ],
  templateUrl: './products.component.html',
  styleUrl: './products.component.css'
})
export class AdminProductsComponent {
  breadcrumbs: BreadcrumbItem[] = [
    { label: 'Dashboard', link: '/admin' },
    { label: 'Products' }
  ];

  allSelected = false;
  sortColumn = 'name';
  sortDirection: 'asc' | 'desc' = 'asc';
  currentPage = 1;
  itemsPerPage = 10;

  products = [
    { id: 1, name: 'ASUS ROG Gaming Laptop', category: 'Laptop', brand: 'ASUS', price: 2199, inStock: false, createdAt: '01 Dec, 2027', selected: false },
    { id: 2, name: 'Airpods Pro 2nd Gen', category: 'Accessories', brand: 'Apple', price: 839, inStock: true, createdAt: '29 Jun, 2027', selected: false },
    { id: 3, name: 'Apple Watch Ultra', category: 'Watch', brand: 'Apple', price: 1579, inStock: false, createdAt: '13 Mar, 2027', selected: false },
    { id: 4, name: 'Bose QuietComfort Earbuds', category: 'Audio', brand: 'Bose', price: 279, inStock: true, createdAt: '18 Nov, 2027', selected: false },
    { id: 5, name: 'Canon EOS R5 Camera', category: 'Camera', brand: 'Canon', price: 3899, inStock: true, createdAt: '28 Sep, 2027', selected: false },
    { id: 6, name: 'Dell XPS 13 Laptop', category: 'Laptop', brand: 'Dell', price: 1299, inStock: true, createdAt: '18 Aug, 2027', selected: false },
    { id: 7, name: 'Google Pixel 8 Pro', category: 'Phone', brand: 'Google', price: 899, inStock: false, createdAt: '02 Sep, 2027', selected: false },
    { id: 8, name: 'Canon EOS R5 Camera', category: 'Camera', brand: 'Canon', price: 3899, inStock: true, createdAt: '28 Sep, 2027', selected: false },
    { id: 9, name: 'Dell XPS 13 Laptop', category: 'Laptop', brand: 'Dell', price: 1299, inStock: true, createdAt: '18 Aug, 2027', selected: false },
    { id: 10, name: 'Google Pixel 8 Pro', category: 'Phone', brand: 'Google', price: 899, inStock: false, createdAt: '02 Sep, 2027', selected: false }
  ];

  sort(column: string) {
    if (this.sortColumn === column) {
      this.sortDirection = this.sortDirection === 'asc' ? 'desc' : 'asc';
    } else {
      this.sortColumn = column;
      this.sortDirection = 'asc';
    }

    this.products.sort((a: any, b: any) => {
      const valA = a[column];
      const valB = b[column];

      if (valA < valB) {
        return this.sortDirection === 'asc' ? -1 : 1;
      }
      if (valA > valB) {
        return this.sortDirection === 'asc' ? 1 : -1;
      }
      return 0;
    });
  }

  toggleAll(event: any) {
    this.allSelected = event.target.checked;
    this.products.forEach(p => p.selected = this.allSelected);
  }

  onProductSelect() {
    this.allSelected = this.products.every(p => p.selected);
  }

  exportProducts() {
    console.log('Exporting products...');
    // In a real app, this would trigger a CSV/PDF download
    alert('Exporting product list to CSV...');
  }

  onPageChange(page: number) {
    this.currentPage = page;
  }
}
