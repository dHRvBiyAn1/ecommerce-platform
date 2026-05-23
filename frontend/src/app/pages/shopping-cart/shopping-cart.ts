import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { ApiService, CartItem } from '../../services/api.service';

@Component({
  selector: 'app-shopping-cart',
  imports: [RouterLink, CommonModule, ButtonModule, CardModule],
  templateUrl: './shopping-cart.html'
})
export class ShoppingCart implements OnInit {
  private apiService = inject(ApiService);

  cartItems: CartItem[] = [];
  subtotal = 0;
  shipping = 0;
  tax = 0;
  total = 0;

  ngOnInit() {
    this.apiService.cart$.subscribe(items => {
      this.cartItems = items;
      this.calculateSummary();
    });
  }

  updateQuantity(productId: string, quantity: number) {
    if (quantity < 1) {
      this.apiService.removeFromCart(productId);
    } else {
      this.apiService.updateCartQuantity(productId, quantity);
    }
  }

  removeItem(productId: string) {
    this.apiService.removeFromCart(productId);
  }

  clearCart() {
    this.apiService.clearCart();
  }

  private calculateSummary() {
    this.subtotal = this.cartItems.reduce((acc, item) => acc + (item.product.price * item.quantity), 0);
    this.shipping = this.subtotal > 500 || this.subtotal === 0 ? 0 : 25.00;
    this.tax = this.subtotal * 0.08;
    this.total = this.subtotal + this.shipping + this.tax;
  }
}
