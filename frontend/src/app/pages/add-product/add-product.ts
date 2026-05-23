import { Component, inject, OnInit } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { CardModule } from 'primeng/card';
import { SelectModule } from 'primeng/select';
import { ApiService, Category, Product } from '../../services/api.service';

@Component({
  selector: 'app-add-product',
  imports: [
    RouterLink,
    CommonModule,
    FormsModule,
    ButtonModule,
    InputTextModule,
    TextareaModule,
    CardModule,
    SelectModule,
  ],
  templateUrl: './add-product.html',
})
export class AddProduct implements OnInit {
  private apiService = inject(ApiService);
  private router = inject(Router);

  categories: Category[] = [];
  selectedCategory: Category | null = null;

  name = '';
  price: number | null = null;
  description = '';
  stockQuantity: number | null = null;
  brand = '';
  imageUrl = '';
  sku = '';

  successMessage = '';
  errorMessage = '';
  isSubmitting = false;

  ngOnInit() {
    this.apiService.categories$.subscribe(cats => {
      this.categories = cats;
      if (cats.length > 0) {
        this.selectedCategory = cats[0];
      }
    });
  }

  onSubmit() {
    if (!this.name.trim() || !this.price || !this.stockQuantity || !this.selectedCategory) {
      this.errorMessage = 'Please complete all required fields (Name, Price, Stock Quantity, Category).';
      this.successMessage = '';
      return;
    }

    this.isSubmitting = true;
    this.errorMessage = '';
    this.successMessage = '';

    const fallbackImage = this.imageUrl.trim() || 'https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&q=80&w=800';

    const newProd: Product = {
      id: 'prod-' + Date.now(),
      name: this.name,
      description: this.description || `${this.name} premium listing description.`,
      price: this.price,
      stockQuantity: this.stockQuantity,
      brand: this.brand || 'Glacier',
      category: this.selectedCategory.name,
      categoryId: this.selectedCategory.id,
      categoryName: this.selectedCategory.name,
      active: true,
      imageUrls: [fallbackImage],
      sku: this.sku.trim() || 'SKU-' + Math.floor(100000 + Math.random() * 900000),
      sellerId: '',
      ratings: { average: 5.0, count: 1 },
    };

    this.apiService.addProduct(newProd).subscribe({
      next: () => {
        this.successMessage = 'Product successfully added to Glacier Enterprise Catalog!';
        this.isSubmitting = false;

        this.name = '';
        this.price = null;
        this.description = '';
        this.stockQuantity = null;
        this.brand = '';
        this.imageUrl = '';
        this.sku = '';
        this.selectedCategory = this.categories[0] ?? null;

        setTimeout(() => {
          this.router.navigate(['/seller/inventory']);
        }, 1500);
      },
      error: (err) => {
        this.errorMessage = `Failed to add product: ${err.error?.message || err.message || 'Server error'}`;
        this.isSubmitting = false;
      },
    });
  }
}
