import { Component, OnInit, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { BadgeModule } from 'primeng/badge';
import { AuthService } from '../../services/auth.service';
import { ApiService, Product } from '../../services/api.service';

@Component({
  selector: 'app-product-details',
  imports: [RouterLink, CommonModule, ButtonModule, BadgeModule],
  templateUrl: './product-details.html'
})
export class ProductDetails implements OnInit {
  private apiService = inject(ApiService);
  private route = inject(ActivatedRoute);
  protected authService = inject(AuthService);

  protected isSeller = computed(() => {
    const user = this.authService.user();
    return user?.roles.includes('ROLE_SELLER') ?? false;
  });
  
  product: Product | null = null;
  selectedImageIndex = 0;
  quantity = 1;
  cartCount = 0;

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      const prodId = params['id'] || 'prod-1';
      this.apiService.getProductById(prodId).subscribe(prod => {
        this.product = prod || null;
      });
    });

    this.apiService.cart$.subscribe(cart => {
      this.cartCount = cart.reduce((acc, item) => acc + item.quantity, 0);
    });
  }

  selectImage(index: number) {
    this.selectedImageIndex = index;
  }

  incrementQuantity() {
    this.quantity++;
  }

  decrementQuantity() {
    if (this.quantity > 1) {
      this.quantity--;
    }
  }

  addToCart() {
    if (this.product) {
      this.apiService.addToCart(this.product, this.quantity);
    }
  }
}
