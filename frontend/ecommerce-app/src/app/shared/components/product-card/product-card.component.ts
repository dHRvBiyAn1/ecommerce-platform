import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { IconComponent } from '../icon/icon.component';
import { StarRatingComponent } from '../star-rating/star-rating.component';

export interface Product {
  id: number;
  name: string;
  category: string;
  brand: string;
  price: number;
  originalPrice?: number;
  rating: number;
  reviewCount: number;
  image?: string;
  gradient: string;
  badge?: 'sale' | 'new' | 'hot';
  inStock: boolean;
}

@Component({
  selector: 'app-product-card',
  standalone: true,
  imports: [CommonModule, RouterModule, IconComponent, StarRatingComponent],
  templateUrl: './product-card.component.html',
  styleUrl: './product-card.component.css'
})
export class ProductCardComponent {
  @Input() product!: Product;
  @Output() addToCart = new EventEmitter<Product>();
  @Output() toggleWishlist = new EventEmitter<Product>();

  wishlisted = false;
  addedToCart = false;

  get discountPercent(): number | null {
    if (this.product.originalPrice && this.product.originalPrice > this.product.price) {
      return Math.round((1 - this.product.price / this.product.originalPrice) * 100);
    }
    return null;
  }

  onAddToCart(event: Event) {
    event.stopPropagation();
    event.preventDefault();
    if (!this.product.inStock) return;
    this.addedToCart = true;
    this.addToCart.emit(this.product);
    setTimeout(() => this.addedToCart = false, 1500);
  }

  onToggleWishlist(event: Event) {
    event.stopPropagation();
    event.preventDefault();
    this.wishlisted = !this.wishlisted;
    this.toggleWishlist.emit(this.product);
  }
}
