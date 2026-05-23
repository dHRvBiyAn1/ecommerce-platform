import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { ApiService, CartItem } from '../../services/api.service';

@Component({
  selector: 'app-checkout',
  imports: [
    CommonModule,
    FormsModule,
    ButtonModule,
    CardModule,
    InputTextModule,
    InputNumberModule,
  ],
  templateUrl: './checkout.html'
})
export class Checkout implements OnInit {
  private apiService = inject(ApiService);
  private router = inject(Router);

  cartItems: CartItem[] = [];
  subtotal = 0;
  shipping = 0;
  tax = 0;
  total = 0;

  firstName = '';
  lastName = '';
  email = '';
  address = '';
  city = '';
  zipCode = '';
  cardNumber = '';
  expiryDate = '';
  cvv = '';

  isSubmitting = false;

  ngOnInit() {
    this.apiService.cart$.subscribe(items => {
      this.cartItems = items;
      this.calculateSummary();
    });
  }

  submitOrder() {
    if (!this.firstName || !this.email || !this.address || !this.cardNumber) {
      alert('Please fill out all required shipping and billing details.');
      return;
    }

    this.isSubmitting = true;

    const items = this.cartItems.map(i => ({
      productId: i.product.id,
      quantity: i.quantity,
    }));

    const orderRequest = {
      items,
      shippingAddress: {
        fullName: `${this.firstName} ${this.lastName}`.trim() || this.firstName,
        street: this.address,
        city: this.city,
        zipCode: this.zipCode,
        country: 'US',
      },
      paymentMethod: 'CREDIT_CARD',
    };

    this.apiService.createOrder(orderRequest).subscribe({
      next: (order) => {
        this.isSubmitting = false;
        this.router.navigate(['/order-success']);
      },
      error: (err) => {
        console.error('Failed to create order through Microservice Gateway:', err);
        this.isSubmitting = false;
        this.router.navigate(['/order-success']);
      }
    });
  }

  private calculateSummary() {
    this.subtotal = this.cartItems.reduce((acc, item) => acc + (item.product.price * item.quantity), 0);
    this.shipping = this.subtotal > 500 || this.subtotal === 0 ? 0 : 25.00;
    this.tax = this.subtotal * 0.08;
    this.total = this.subtotal + this.shipping + this.tax;
  }
}
