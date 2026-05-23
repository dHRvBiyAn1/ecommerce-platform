import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-order-success',
  imports: [
    RouterLink,
    CommonModule,
    ButtonModule,
    CardModule,
  ],
  templateUrl: './order-success.html'
})
export class OrderSuccess implements OnInit {
  private apiService = inject(ApiService);

  latestOrder: any = null;

  ngOnInit() {
    this.apiService.orders$.subscribe(orders => {
      if (orders.length > 0) {
        this.latestOrder = orders[0];
      } else {
        this.latestOrder = {
          id: 'ORD-' + Math.floor(100000 + Math.random() * 900000),
          customerName: 'John Doe',
          customerEmail: 'john.doe@glacier.com',
          shippingAddress: '123 Aurora Borealis Way, Reykjavik, 101',
          totalAmount: 1897.67,
          paymentMethod: 'CREDIT_CARD',
          items: [
            { productName: 'Quantum Core Processor Unit X9', quantity: 1, price: 1299.00 },
            { productName: 'Neural Link Interface Module', quantity: 1, price: 450.00 }
          ]
        };
      }
    });
  }
}
