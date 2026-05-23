import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ApiService, Product } from '../../services/api.service';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink, CommonModule, ButtonModule, InputTextModule],
  templateUrl: './home.html',
})
export class Home implements OnInit {
  private apiService = inject(ApiService);
  products: Product[] = [];
  featuredProduct: Product | null = null;
  trendingProducts: Product[] = [];

  ngOnInit() {
    this.apiService.getProducts().subscribe(products => {
      this.products = products;
      if (products.length > 0) {
        this.featuredProduct = products[0];
        this.trendingProducts = products.slice(1);
      }
    });
  }

  addToCart(product: Product, event: Event) {
    event.stopPropagation();
    this.apiService.addToCart(product);
  }
}
