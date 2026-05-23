import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { BadgeModule } from 'primeng/badge';
import { ApiService, Product } from '../../services/api.service';

@Component({
  selector: 'app-seller-dashboard',
  imports: [
    RouterLink,
    CommonModule,
    ButtonModule,
    CardModule,
    BadgeModule,
  ],
  templateUrl: './seller-dashboard.html',
})
export class SellerDashboard implements OnInit {
  private apiService = inject(ApiService);

  totalProducts = 0;
  aggregateStock = 0;
  lowStockItemsCount = 0;
  lowStockItems: Product[] = [];
  salesRevenue = 0;
  activeSellerId = 'seller-100';

  ngOnInit() {
    this.apiService.products$.subscribe(prods => {
      const sellerProds = prods.filter(p => p.sellerId === this.activeSellerId);
      this.totalProducts = sellerProds.length;
      this.aggregateStock = sellerProds.reduce((acc, p) => acc + p.stockQuantity, 0);

      this.lowStockItems = sellerProds.filter(p => p.stockQuantity < 15);
      this.lowStockItemsCount = this.lowStockItems.length;
    });

    this.apiService.orders$.subscribe(orders => {
      this.salesRevenue = orders.reduce((acc, ord) => acc + ord.totalAmount, 0);
    });
  }
}
