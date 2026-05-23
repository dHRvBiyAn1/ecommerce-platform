import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { AppFloatingConfigurator } from '../../layout/component/app.floating-configurator';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [RouterLink, CommonModule, FormsModule, ButtonModule, InputTextModule, CardModule, AppFloatingConfigurator],
  templateUrl: './login.html',
})
export class Login {
  private authService = inject(AuthService);
  private router = inject(Router);

  email = '';
  password = '';
  errorMessage = '';
  isSubmitting = false;

  onLogin() {
    if (!this.email.trim() || !this.password.trim()) {
      this.errorMessage = 'Please enter your email and password.';
      return;
    }

    this.isSubmitting = true;
    this.errorMessage = '';

    this.authService.login({ email: this.email, password: this.password }).subscribe({
      next: (user) => {
        this.isSubmitting = false;
        if (user.roles.includes('ROLE_ADMIN')) {
          this.router.navigateByUrl('/admin/dashboard');
        } else if (user.roles.includes('ROLE_SELLER')) {
          this.router.navigateByUrl('/seller');
        } else {
          this.router.navigateByUrl('/');
        }
      },
      error: (err) => {
        this.isSubmitting = false;
        if (err.status === 401) {
          this.errorMessage = 'Invalid email or password.';
        } else if (err.status === 0) {
          this.errorMessage = 'Cannot reach the server. Please ensure the backend is running.';
        } else {
          this.errorMessage = err.error?.message || err.message || 'An unexpected error occurred.';
        }
      },
    });
  }
}
