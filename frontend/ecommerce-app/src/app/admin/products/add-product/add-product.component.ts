import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CardComponent } from '../../../shared/components/card/card.component';
import { InputComponent } from '../../../shared/components/input/input.component';
import { ButtonComponent } from '../../../shared/components/button/button.component';
import { SelectComponent, SelectOption } from '../../../shared/components/select/select.component';
import { TextareaComponent } from '../../../shared/components/textarea/textarea.component';
import { PageHeaderComponent, BreadcrumbItem } from '../../../shared/components/page-header/page-header.component';
import { ToastService } from '../../../shared/toast/toast.service';
import { Router } from '@angular/router';
import { IconComponent } from '../../../shared/components/icon/icon.component';

@Component({
  selector: 'app-admin-add-product',
  standalone: true,
  imports: [
    CommonModule, 
    FormsModule,
    CardComponent, 
    InputComponent, 
    ButtonComponent, 
    SelectComponent, 
    TextareaComponent,
    PageHeaderComponent,
    IconComponent
  ],
  templateUrl: './add-product.component.html',
  styleUrl: './add-product.component.css'
})
export class AdminAddProductComponent {
  breadcrumbs: BreadcrumbItem[] = [
    { label: 'Dashboard', link: '/admin' },
    { label: 'Add Product' }
  ];

  product = {
    name: '',
    category: '',
    brand: '',
    color: '',
    weight: null,
    length: null,
    width: null,
    description: '',
    price: null,
    discount: null,
    stock: 1,
    availability: ''
  };

  categories: SelectOption[] = [
    { label: 'Laptop', value: 'laptop' },
    { label: 'Smartphone', value: 'smartphone' },
    { label: 'Watch', value: 'watch' },
    { label: 'Audio', value: 'audio' },
    { label: 'Camera', value: 'camera' },
    { label: 'Accessories', value: 'accessories' }
  ];

  brands: SelectOption[] = [
    { label: 'Apple', value: 'apple' },
    { label: 'Samsung', value: 'samsung' },
    { label: 'ASUS', value: 'asus' },
    { label: 'Dell', value: 'dell' },
    { label: 'Sony', value: 'sony' },
    { label: 'Bose', value: 'bose' }
  ];

  colors: SelectOption[] = [
    { label: 'Space Gray', value: 'space_gray' },
    { label: 'Silver', value: 'silver' },
    { label: 'Midnight Black', value: 'black' },
    { label: 'Pearl White', value: 'white' },
    { label: 'Ocean Blue', value: 'blue' }
  ];

  availabilities: SelectOption[] = [
    { label: 'In Stock', value: 'in_stock' },
    { label: 'Out of Stock', value: 'out_of_stock' },
    { label: 'Pre-order', value: 'pre_order' }
  ];

  constructor(private toastService: ToastService, private router: Router) {}

  incStock() {
    this.product.stock++;
  }

  decStock() {
    if (this.product.stock > 0) {
      this.product.stock--;
    }
  }

  onSubmit() {
    console.log('Saving product:', this.product);
    this.toastService.info('Saving product...');
    setTimeout(() => {
      this.toastService.success('Product added successfully!');
      this.router.navigate(['/admin/products']);
    }, 800);
  }
}
