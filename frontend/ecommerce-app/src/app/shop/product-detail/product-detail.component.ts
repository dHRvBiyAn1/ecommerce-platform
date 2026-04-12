import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { StoreNavbarComponent } from '../../shared/layout/store-navbar/store-navbar.component';
import { ProductCardComponent, Product } from '../../shared/components/product-card/product-card.component';
import { StarRatingComponent } from '../../shared/components/star-rating/star-rating.component';
import { IconComponent } from '../../shared/components/icon/icon.component';

interface Review {
  id: number;
  author: string;
  initials: string;
  date: string;
  rating: number;
  comment: string;
  helpful: number;
}

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [
    CommonModule, RouterModule, StoreNavbarComponent,
    ProductCardComponent, StarRatingComponent, IconComponent
  ],
  templateUrl: './product-detail.component.html',
  styleUrl: './product-detail.component.css'
})
export class ProductDetailComponent implements OnInit {
  product: Product & { description: string; specs: { label: string; value: string }[]; images: string[] } = {
    id: 1,
    name: 'MacBook Pro 16" M3 Max',
    category: 'Laptop',
    brand: 'Apple',
    price: 2499,
    originalPrice: 2799,
    rating: 4.8,
    reviewCount: 342,
    gradient: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)',
    badge: 'hot',
    inStock: true,
    description: 'The most powerful MacBook Pro ever. With the M3 Max chip, up to 128GB of unified memory, and a stunning 16-inch Liquid Retina XDR display, this is the ultimate pro notebook for the most demanding workflows.',
    specs: [
      { label: 'Processor', value: 'Apple M3 Max' },
      { label: 'Memory', value: '48GB Unified' },
      { label: 'Storage', value: '1TB SSD' },
      { label: 'Display', value: '16.2" Liquid Retina XDR' },
      { label: 'Battery', value: 'Up to 22 hours' },
      { label: 'Weight', value: '2.14 kg' }
    ],
    images: [
      'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)',
      'linear-gradient(135deg, #0f3460 0%, #16213e 50%, #1a1a2e 100%)',
      'linear-gradient(135deg, #16213e 0%, #1a1a2e 100%)',
      'linear-gradient(135deg, #0f3460 0%, #1a1a2e 100%)'
    ]
  };

  selectedImageIndex = 0;
  quantity = 1;
  activeTab: 'description' | 'specs' | 'reviews' = 'description';
  addedToCart = false;

  reviews: Review[] = [
    { id: 1, author: 'Alex Chen', initials: 'AC', date: 'Mar 15, 2027', rating: 5, comment: 'Absolutely incredible machine. The M3 Max handles everything I throw at it — 4K video editing, 3D rendering, running multiple VMs. Battery life is unreal. Best laptop I\'ve ever owned.', helpful: 47 },
    { id: 2, author: 'Sarah Kim', initials: 'SK', date: 'Mar 8, 2027', rating: 5, comment: 'Worth every penny. The display is stunning and the speakers are the best I\'ve heard on any laptop. Compiles my entire React project in seconds. Couldn\'t be happier.', helpful: 32 },
    { id: 3, author: 'James Rodriguez', initials: 'JR', date: 'Feb 28, 2027', rating: 4, comment: 'Great performance and build quality. Only complaint is the notch on the display, but you get used to it. The keyboard is a joy to type on.', helpful: 18 },
    { id: 4, author: 'Priya Patel', initials: 'PP', date: 'Feb 14, 2027', rating: 5, comment: 'Switching from Windows was the best decision. Everything is buttery smooth. The trackpad is massive and incredibly responsive. Runs Figma + VS Code + Docker with zero lag.', helpful: 25 }
  ];

  relatedProducts: Product[] = [
    { id: 7, name: 'Dell XPS 15 OLED', category: 'Laptop', brand: 'Dell', price: 1799, originalPrice: 2099, rating: 4.4, reviewCount: 312, gradient: 'linear-gradient(135deg, #141e30 0%, #243b55 100%)', inStock: true },
    { id: 10, name: 'ASUS ROG Zephyrus G16', category: 'Laptop', brand: 'ASUS', price: 2199, rating: 4.7, reviewCount: 234, gradient: 'linear-gradient(135deg, #0f0f1a 0%, #1a0a2e 50%, #2d0040 100%)', badge: 'hot', inStock: true },
    { id: 2, name: 'Sony WH-1000XM5', category: 'Audio', brand: 'Sony', price: 279, originalPrice: 399, rating: 4.7, reviewCount: 1204, gradient: 'linear-gradient(135deg, #2d1b69 0%, #11998e 100%)', badge: 'sale', inStock: true },
    { id: 6, name: 'AirPods Pro 2nd Gen', category: 'Accessories', brand: 'Apple', price: 249, rating: 4.7, reviewCount: 4521, gradient: 'linear-gradient(135deg, #e0e0e0 0%, #f5f5f5 50%, #ffffff 100%)', inStock: true }
  ];

  ratingBreakdown = [
    { stars: 5, count: 245, percent: 72 },
    { stars: 4, count: 68, percent: 20 },
    { stars: 3, count: 17, percent: 5 },
    { stars: 2, count: 8, percent: 2 },
    { stars: 1, count: 4, percent: 1 }
  ];

  get discountPercent(): number | null {
    if (this.product.originalPrice && this.product.originalPrice > this.product.price) {
      return Math.round((1 - this.product.price / this.product.originalPrice) * 100);
    }
    return null;
  }

  constructor(private route: ActivatedRoute) {}

  ngOnInit() {
    // In a real app, fetch product by route param
    this.route.params.subscribe(params => {
      console.log('Product ID:', params['id']);
    });
  }

  selectImage(index: number) {
    this.selectedImageIndex = index;
  }

  incrementQty() { this.quantity++; }
  decrementQty() { if (this.quantity > 1) this.quantity--; }

  addToCart() {
    this.addedToCart = true;
    console.log(`Added ${this.quantity}x ${this.product.name} to cart`);
    setTimeout(() => this.addedToCart = false, 2000);
  }

  onRelatedAddToCart(product: Product) {
    console.log('Added related product:', product.name);
  }
}
