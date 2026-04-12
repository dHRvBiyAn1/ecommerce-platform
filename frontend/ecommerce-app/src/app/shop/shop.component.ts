import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StoreNavbarComponent } from '../shared/layout/store-navbar/store-navbar.component';
import { ProductCardComponent, Product } from '../shared/components/product-card/product-card.component';
import { IconComponent } from '../shared/components/icon/icon.component';

@Component({
  selector: 'app-shop',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, StoreNavbarComponent, ProductCardComponent, IconComponent],
  templateUrl: './shop.component.html',
  styleUrl: './shop.component.css'
})
export class ShopComponent {
  viewMode: 'grid' | 'list' = 'grid';
  sortBy = 'popular';
  filterOpen = false;

  categories = [
    { name: 'All', active: true },
    { name: 'Laptops', active: false },
    { name: 'Phones', active: false },
    { name: 'Audio', active: false },
    { name: 'Watches', active: false },
    { name: 'Cameras', active: false },
    { name: 'Accessories', active: false }
  ];

  brands = [
    { name: 'Apple', checked: false },
    { name: 'Samsung', checked: false },
    { name: 'Sony', checked: false },
    { name: 'Dell', checked: false },
    { name: 'ASUS', checked: false },
    { name: 'Bose', checked: false },
    { name: 'Canon', checked: false },
    { name: 'Google', checked: false }
  ];

  priceRange = { min: 0, max: 5000 };
  selectedRating = 0;

  allProducts: Product[] = [
    { id: 1, name: 'MacBook Pro 16" M3 Max', category: 'Laptop', brand: 'Apple', price: 2499, originalPrice: 2799, rating: 4.8, reviewCount: 342, gradient: 'linear-gradient(135deg, #1a1a2e, #16213e, #0f3460)', badge: 'hot', inStock: true },
    { id: 2, name: 'Sony WH-1000XM5 Headphones', category: 'Audio', brand: 'Sony', price: 279, originalPrice: 399, rating: 4.7, reviewCount: 1204, gradient: 'linear-gradient(135deg, #2d1b69, #11998e)', badge: 'sale', inStock: true },
    { id: 3, name: 'Apple Watch Ultra 2', category: 'Watch', brand: 'Apple', price: 799, rating: 4.6, reviewCount: 567, gradient: 'linear-gradient(135deg, #f12711, #f5af19)', badge: 'new', inStock: true },
    { id: 4, name: 'Canon EOS R5 Mirrorless', category: 'Camera', brand: 'Canon', price: 3899, originalPrice: 4299, rating: 4.9, reviewCount: 89, gradient: 'linear-gradient(135deg, #0c0c0c, #1a1a2e, #2d2d44)', inStock: true },
    { id: 5, name: 'Samsung Galaxy S24 Ultra', category: 'Phone', brand: 'Samsung', price: 1199, originalPrice: 1419, rating: 4.5, reviewCount: 2103, gradient: 'linear-gradient(135deg, #2c3e50, #4ca1af)', badge: 'sale', inStock: true },
    { id: 6, name: 'AirPods Pro 2nd Gen', category: 'Accessories', brand: 'Apple', price: 249, rating: 4.7, reviewCount: 4521, gradient: 'linear-gradient(135deg, #e0e0e0, #f5f5f5, #ffffff)', inStock: true },
    { id: 7, name: 'Dell XPS 15 OLED', category: 'Laptop', brand: 'Dell', price: 1799, originalPrice: 2099, rating: 4.4, reviewCount: 312, gradient: 'linear-gradient(135deg, #141e30, #243b55)', inStock: true },
    { id: 8, name: 'Bose QuietComfort Ultra', category: 'Audio', brand: 'Bose', price: 329, rating: 4.6, reviewCount: 678, gradient: 'linear-gradient(135deg, #0f0c29, #302b63, #24243e)', badge: 'new', inStock: false },
    { id: 9, name: 'Google Pixel 8 Pro', category: 'Phone', brand: 'Google', price: 899, originalPrice: 999, rating: 4.5, reviewCount: 1567, gradient: 'linear-gradient(135deg, #667eea, #764ba2)', badge: 'sale', inStock: true },
    { id: 10, name: 'ASUS ROG Zephyrus G16', category: 'Laptop', brand: 'ASUS', price: 2199, rating: 4.7, reviewCount: 234, gradient: 'linear-gradient(135deg, #0f0f1a, #1a0a2e, #2d0040)', badge: 'hot', inStock: true },
    { id: 11, name: 'Sony A7 IV Camera', category: 'Camera', brand: 'Sony', price: 2498, originalPrice: 2798, rating: 4.8, reviewCount: 456, gradient: 'linear-gradient(135deg, #232526, #414345)', inStock: true },
    { id: 12, name: 'Apple iPad Pro M4', category: 'Accessories', brand: 'Apple', price: 1099, rating: 4.9, reviewCount: 890, gradient: 'linear-gradient(135deg, #c9d6ff, #e2e2e2)', badge: 'new', inStock: true }
  ];

  get activeCategory(): string {
    return this.categories.find(c => c.active)?.name || 'All';
  }

  get filteredProducts(): Product[] {
    let products = [...this.allProducts];

    // Category filter
    if (this.activeCategory !== 'All') {
      const catMap: Record<string, string> = {
        'Laptops': 'Laptop', 'Phones': 'Phone', 'Watches': 'Watch',
        'Cameras': 'Camera'
      };
      const mapped = catMap[this.activeCategory] || this.activeCategory;
      products = products.filter(p => p.category === mapped);
    }

    // Brand filter
    const selectedBrands = this.brands.filter(b => b.checked).map(b => b.name);
    if (selectedBrands.length > 0) {
      products = products.filter(p => selectedBrands.includes(p.brand));
    }

    // Rating filter
    if (this.selectedRating > 0) {
      products = products.filter(p => p.rating >= this.selectedRating);
    }

    // Sort
    switch (this.sortBy) {
      case 'price-low': products.sort((a, b) => a.price - b.price); break;
      case 'price-high': products.sort((a, b) => b.price - a.price); break;
      case 'rating': products.sort((a, b) => b.rating - a.rating); break;
      case 'newest': products.sort((a, b) => b.id - a.id); break;
    }

    return products;
  }

  get activeFiltersCount(): number {
    let count = 0;
    if (this.activeCategory !== 'All') count++;
    count += this.brands.filter(b => b.checked).length;
    if (this.selectedRating > 0) count++;
    return count;
  }

  selectCategory(name: string) {
    this.categories.forEach(c => c.active = c.name === name);
  }

  setRating(rating: number) {
    this.selectedRating = this.selectedRating === rating ? 0 : rating;
  }

  clearFilters() {
    this.categories.forEach(c => c.active = c.name === 'All');
    this.brands.forEach(b => b.checked = false);
    this.selectedRating = 0;
  }

  onAddToCart(product: Product) {
    console.log('Added to cart:', product.name);
  }
}
