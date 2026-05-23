import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { BadgeModule } from 'primeng/badge';
import { TableModule } from 'primeng/table';
import { ApiService, Product } from '../../services/api.service';

@Component({
  selector: 'app-inventory-management',
  imports: [
    RouterLink,
    CommonModule,
    FormsModule,
    ButtonModule,
    CardModule,
    InputTextModule,
    BadgeModule,
    TableModule,
  ],
  templateUrl: './inventory-management.html',
})
export class InventoryManagement implements OnInit {
  private apiService = inject(ApiService);

  products: Product[] = [];
  filteredProducts: Product[] = [];
  searchQuery = '';
  activeSellerId = 'seller-100';

  editingProductId: string | null = null;
  editPrice = 0;
  editStock = 0;

  ngOnInit() {
    this.apiService.products$.subscribe(prods => {
      this.products = prods.filter(p => p.sellerId === this.activeSellerId);
      this.applyFilter();
    });
  }

  applyFilter() {
    if (!this.searchQuery.trim()) {
      this.filteredProducts = [...this.products];
    } else {
      const query = this.searchQuery.toLowerCase();
      this.filteredProducts = this.products.filter(p =>
        p.name.toLowerCase().includes(query) ||
        (p.sku && p.sku.toLowerCase().includes(query)) ||
        (p.category && p.category.toLowerCase().includes(query)),
      );
    }
  }

  startEdit(product: Product) {
    this.editingProductId = product.id;
    this.editPrice = product.price;
    this.editStock = product.stockQuantity;
  }

  cancelEdit() {
    this.editingProductId = null;
  }

  saveProduct(productId: string) {
    this.apiService.updateProductStockAndPrice(productId, this.editStock, this.editPrice);
    this.editingProductId = null;
  }

  deleteProduct(productId: string) {
    if (confirm('Are you sure you want to delete this product catalog listing?')) {
      this.apiService.deleteProduct(productId);
    }
  }
}
