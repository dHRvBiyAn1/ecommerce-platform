import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { CardModule } from 'primeng/card';
import { SelectModule } from 'primeng/select';
import { ApiService, Category } from '../../services/api.service';

@Component({
  selector: 'app-edit-product',
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
  templateUrl: './edit-product.html',
})
export class EditProduct implements OnInit {
  private apiService = inject(ApiService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);

  categories: Category[] = [];
  selectedCategory: Category | null = null;

  productId = '';
  name = '';
  price = 0;
  description = '';
  stockQuantity = 0;
  brand = '';
  imageUrl = '';
  sku = '';

  successMessage = '';
  errorMessage = '';
  loading = true;
  isSubmitting = false;

  ngOnInit() {
    this.apiService.categories$.subscribe(cats => {
      this.categories = cats;
    });

    this.route.queryParams.subscribe(params => {
      const id = params['id'];
      if (!id) {
        this.router.navigate(['/seller/inventory']);
        return;
      }
      this.productId = id;
      this.apiService.getProductById(id).subscribe(prod => {
        if (!prod) {
          this.router.navigate(['/seller/inventory']);
          return;
        }
        this.name = prod.name;
        this.price = prod.price;
        this.description = prod.description || '';
        this.stockQuantity = prod.stockQuantity;
        this.brand = prod.brand || '';
        this.imageUrl = prod.imageUrls?.[0] || '';
        this.sku = prod.sku || '';

        // Match product category to a loaded category
        if (this.categories.length > 0) {
          this.selectedCategory = this.categories.find(c => c.id === prod.categoryId) ?? this.categories[0];
        }
        this.loading = false;
      });
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

    this.apiService.updateProduct(this.productId, {
      name: this.name,
      price: this.price,
      description: this.description,
      stockQuantity: this.stockQuantity,
      brand: this.brand,
      category: this.selectedCategory.name,
      categoryId: this.selectedCategory.id,
      imageUrl: this.imageUrl,
      sku: this.sku,
    }).subscribe({
      next: () => {
        this.successMessage = 'Product updated successfully!';
        this.isSubmitting = false;

        setTimeout(() => {
          this.router.navigate(['/seller/inventory']);
        }, 1500);
      },
      error: (err) => {
        this.errorMessage = `Failed to update product: ${err.error?.message || err.message || 'Server error'}`;
        this.isSubmitting = false;
      },
    });
  }
}
