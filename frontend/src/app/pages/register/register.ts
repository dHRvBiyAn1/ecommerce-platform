import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { SelectButtonModule } from 'primeng/selectbutton';
import { AppFloatingConfigurator } from '../../layout/component/app.floating-configurator';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [RouterLink, CommonModule, FormsModule, ButtonModule, InputTextModule, CardModule, SelectButtonModule, AppFloatingConfigurator],
  templateUrl: './register.html',
})
export class Register {
  private authService = inject(AuthService);
  private router = inject(Router);

  fullName = '';
  email = '';
  password = '';
  errorMessage = '';
  isSubmitting = false;
  roleOptions = [
    { label: 'Buy', icon: 'pi pi-shopping-cart', value: 'CUSTOMER' },
    { label: 'Sell', icon: 'pi pi-briefcase', value: 'SELLER' },
  ];
  selectedRole: 'CUSTOMER' | 'SELLER' = 'CUSTOMER';

  onRegister() {
    if (!this.fullName.trim() || !this.email.trim() || !this.password.trim()) {
      this.errorMessage = 'Please fill out all required fields.';
      return;
    }

    if (this.password.length < 8) {
      this.errorMessage = 'Password must be at least 8 characters.';
      return;
    }

    this.isSubmitting = true;
    this.errorMessage = '';

    this.authService.register({
      email: this.email,
      password: this.password,
      displayName: this.fullName,
      userType: this.selectedRole,
    }).subscribe({
      next: () => {
        this.isSubmitting = false;
        setTimeout(() => this.router.navigate(['/login']), 1500);
      },
      error: (err) => {
        this.isSubmitting = false;
        if (err.status === 409) {
          this.errorMessage = 'An account with this email already exists.';
        } else if (err.status === 400) {
          this.errorMessage = 'Invalid registration details.';
        } else if (err.status === 0) {
          this.errorMessage = 'Cannot reach the server. Please ensure the backend is running.';
        } else {
          this.errorMessage = err.error?.message || err.message || 'An unexpected error occurred.';
        }
      },
    });
  }
}
