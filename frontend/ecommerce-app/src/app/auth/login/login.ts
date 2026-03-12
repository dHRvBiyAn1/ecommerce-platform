import { Component, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../auth.service';
import { InputComponent } from '../../shared/components/input.component';
import { ButtonComponent } from '../../shared/components/button.component';
import { ToastService } from '../../shared/toast/toast.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, InputComponent, ButtonComponent],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  loginForm: FormGroup;
  loading = false;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private cdr: ChangeDetectorRef,
    private toastService: ToastService
  ) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
    });
  }

  onSubmit() {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.loading = true;
    this.authService.login(this.loginForm.value).subscribe({
      next: () => {
        this.loading = false;
        this.cdr.detectChanges();
        
        const role = this.authService.getUserRole();
        console.log('User role:', role);
        if (role?.includes('ROLE_ADMIN')) {
          this.router.navigate(['/admin']);
        } else if (role?.includes('ROLE_SELLER')) {
          this.router.navigate(['/seller']);
        } else {
          this.router.navigate(['/']);
        }
        
        this.toastService.success('Logged in successfully!');
      },
      error: (err) => {
        this.loading = false;
        const msg = err?.error?.message || 'Invalid email or password. Please try again.';
        this.toastService.error(msg);
        this.cdr.detectChanges();
      },
    });
  }
}
