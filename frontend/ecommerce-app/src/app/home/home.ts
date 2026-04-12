import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { StoreNavbarComponent } from '../shared/layout/store-navbar/store-navbar.component';
import { ProductCardComponent, Product } from '../shared/components/product-card/product-card.component';
import { TrustBadgesComponent } from '../shared/components/trust-badges/trust-badges.component';
import { IconComponent } from '../shared/components/icon/icon.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    StoreNavbarComponent,
    ProductCardComponent,
    TrustBadgesComponent,
    IconComponent
  ],
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {
  categories = [
    { name: 'Laptops', icon: 'laptop', count: 24 },
    { name: 'Phones', icon: 'smartphone', count: 38 },
    { name: 'Audio', icon: 'audio', count: 19 },
    { name: 'Watches', icon: 'watch', count: 12 },
    { name: 'Cameras', icon: 'camera', count: 8 },
    { name: 'Accessories', icon: 'package', count: 45 }
  ];

  featuredProducts: Product[] = [
    {
      id: 1, name: 'MacBook Pro 16" M3 Max', category: 'Laptop', brand: 'Apple',
      price: 2499, originalPrice: 2799, rating: 4.8, reviewCount: 342,
      gradient: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)',
      badge: 'hot', inStock: true
    },
    {
      id: 2, name: 'Sony WH-1000XM5 Headphones', category: 'Audio', brand: 'Sony',
      price: 279, originalPrice: 399, rating: 4.7, reviewCount: 1204,
      gradient: 'linear-gradient(135deg, #2d1b69 0%, #11998e 100%)',
      badge: 'sale', inStock: true
    },
    {
      id: 3, name: 'Apple Watch Ultra 2', category: 'Watch', brand: 'Apple',
      price: 799, rating: 4.6, reviewCount: 567,
      gradient: 'linear-gradient(135deg, #f12711 0%, #f5af19 100%)',
      badge: 'new', inStock: true
    },
    {
      id: 4, name: 'Canon EOS R5 Mirrorless', category: 'Camera', brand: 'Canon',
      price: 3899, originalPrice: 4299, rating: 4.9, reviewCount: 89,
      gradient: 'linear-gradient(135deg, #0c0c0c 0%, #1a1a2e 50%, #2d2d44 100%)',
      inStock: true
    },
    {
      id: 5, name: 'Samsung Galaxy S24 Ultra', category: 'Phone', brand: 'Samsung',
      price: 1199, originalPrice: 1419, rating: 4.5, reviewCount: 2103,
      gradient: 'linear-gradient(135deg, #2c3e50 0%, #4ca1af 100%)',
      badge: 'sale', inStock: true
    },
    {
      id: 6, name: 'AirPods Pro 2nd Gen', category: 'Accessories', brand: 'Apple',
      price: 249, rating: 4.7, reviewCount: 4521,
      gradient: 'linear-gradient(135deg, #e0e0e0 0%, #f5f5f5 50%, #ffffff 100%)',
      inStock: true
    },
    {
      id: 7, name: 'Dell XPS 15 OLED', category: 'Laptop', brand: 'Dell',
      price: 1799, originalPrice: 2099, rating: 4.4, reviewCount: 312,
      gradient: 'linear-gradient(135deg, #141e30 0%, #243b55 100%)',
      inStock: true
    },
    {
      id: 8, name: 'Bose QuietComfort Ultra', category: 'Audio', brand: 'Bose',
      price: 329, rating: 4.6, reviewCount: 678,
      gradient: 'linear-gradient(135deg, #0f0c29 0%, #302b63 50%, #24243e 100%)',
      badge: 'new', inStock: false
    }
  ];

  trendingProducts: Product[] = [
    {
      id: 9, name: 'Google Pixel 8 Pro', category: 'Phone', brand: 'Google',
      price: 899, originalPrice: 999, rating: 4.5, reviewCount: 1567,
      gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      badge: 'sale', inStock: true
    },
    {
      id: 10, name: 'ASUS ROG Zephyrus G16', category: 'Laptop', brand: 'ASUS',
      price: 2199, rating: 4.7, reviewCount: 234,
      gradient: 'linear-gradient(135deg, #0f0f1a 0%, #1a0a2e 50%, #2d0040 100%)',
      badge: 'hot', inStock: true
    },
    {
      id: 11, name: 'Sony A7 IV Camera', category: 'Camera', brand: 'Sony',
      price: 2498, originalPrice: 2798, rating: 4.8, reviewCount: 456,
      gradient: 'linear-gradient(135deg, #232526 0%, #414345 100%)',
      inStock: true
    },
    {
      id: 12, name: 'Apple iPad Pro M4', category: 'Accessories', brand: 'Apple',
      price: 1099, rating: 4.9, reviewCount: 890,
      gradient: 'linear-gradient(135deg, #c9d6ff 0%, #e2e2e2 100%)',
      badge: 'new', inStock: true
    }
  ];

  onAddToCart(product: Product) {
    console.log('Added to cart:', product.name);
  }
}
