import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { ApiService, Product, Category } from '../../services/api.service';

@Component({
  selector: 'app-product-listing',
  imports: [RouterLink, CommonModule, FormsModule, ButtonModule, InputTextModule],
  templateUrl: './product-listing.html'
})
export class ProductListing implements OnInit {
  private apiService = inject(ApiService);

  allProducts: Product[] = [];
  filteredProducts: Product[] = [];
  categories: Category[] = [];

  selectedCategoryId: string = 'all';
  searchQuery: string = '';
  priceRange: number = 3000;
  cartCount = 0;

  ngOnInit() {
    this.apiService.getProducts().subscribe(products => {
      this.allProducts = products;
      this.applyFilters();
    });

    this.apiService.categories$.subscribe(categories => {
      this.categories = categories;
    });

    this.apiService.cart$.subscribe(cart => {
      this.cartCount = cart.reduce((acc, item) => acc + item.quantity, 0);
    });
  }

  selectCategory(categoryId: string) {
    this.selectedCategoryId = categoryId;
    this.applyFilters();
  }

  onSearchChange() {
    this.applyFilters();
  }

  onPriceChange() {
    this.applyFilters();
  }

  applyFilters() {
    this.filteredProducts = this.allProducts.filter(product => {
      const matchesCategory = this.selectedCategoryId === 'all' || product.categoryId === this.selectedCategoryId;
      const matchesSearch = product.name.toLowerCase().includes(this.searchQuery.toLowerCase()) || 
                            product.description.toLowerCase().includes(this.searchQuery.toLowerCase());
      const matchesPrice = product.price <= this.priceRange;
      return matchesCategory && matchesSearch && matchesPrice;
    });
  }

  addToCart(product: Product, event: Event) {
    event.stopPropagation();
    this.apiService.addToCart(product);
  }
}
