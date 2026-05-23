import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { ApiService } from '../../services/api.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-my-account',
  imports: [
    RouterLink,
    CommonModule,
    FormsModule,
    ButtonModule,
    CardModule,
    InputTextModule,
    TableModule,
  ],
  templateUrl: './my-account.html'
})
export class MyAccount implements OnInit {
  private apiService = inject(ApiService);
  private authService = inject(AuthService);

  ordersList: any[] = [];

  name = '';
  email = '';
  phone = '+354 888 1234';
  defaultAddress = '123 Aurora Borealis Way, Reykjavik, 101';
  isEditingProfile = false;
  isSavingProfile = false;

  ngOnInit() {
    const user = this.authService.user();
    if (user) {
      this.name = user.displayName;
      this.email = user.email;
    }

    this.apiService.orders$.subscribe(orders => {
      this.ordersList = orders;
    });

    this.apiService.fetchOrders().subscribe();
  }

  toggleEditProfile() {
    this.isEditingProfile = !this.isEditingProfile;
  }

  saveProfile() {
    this.isSavingProfile = true;
    this.authService.updateProfile(this.name).subscribe({
      next: () => {
        this.isSavingProfile = false;
        this.isEditingProfile = false;
      },
      error: () => {
        this.isSavingProfile = false;
        this.isEditingProfile = false;
      },
    });
  }
}
