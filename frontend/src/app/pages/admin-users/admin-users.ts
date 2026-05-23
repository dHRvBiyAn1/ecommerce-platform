import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TableModule } from 'primeng/table';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { CardModule } from 'primeng/card';
import { BadgeModule } from 'primeng/badge';
import { SelectModule } from 'primeng/select';
import { ApiService, User } from '../../services/api.service';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [CommonModule, FormsModule, TableModule, ButtonModule, InputTextModule, CardModule, BadgeModule, SelectModule],
  templateUrl: './admin-users.html',
})
export class AdminUsers implements OnInit {
  private apiService = inject(ApiService);

  users: User[] = [];
  filteredUsers: User[] = [];
  searchQuery = '';
  selectedRoleFilter = 'ALL';

  totalUsersCount = 0;
  adminsCount = 0;
  activeCount = 0;
  suspendedCount = 0;

  roleOptions = [
    { label: 'All Roles', value: 'ALL' },
    { label: 'Buyers', value: 'BUYER' },
    { label: 'Sellers', value: 'SELLER' },
    { label: 'Admins', value: 'ADMIN' },
  ];

  ngOnInit() {
    this.apiService.users$.subscribe(allUsers => {
      this.users = allUsers;
      this.calculateStats();
      this.applyFilters();
    });
  }

  calculateStats() {
    this.totalUsersCount = this.users.length;
    this.adminsCount = this.users.filter(u => u.role === 'ADMIN').length;
    this.activeCount = this.users.filter(u => u.status === 'ACTIVE').length;
    this.suspendedCount = this.users.filter(u => u.status === 'SUSPENDED').length;
  }

  applyFilters() {
    let result = [...this.users];
    if (this.selectedRoleFilter !== 'ALL') {
      result = result.filter(u => u.role === this.selectedRoleFilter);
    }
    if (this.searchQuery.trim()) {
      const query = this.searchQuery.toLowerCase();
      result = result.filter(u =>
        u.name.toLowerCase().includes(query) || u.email.toLowerCase().includes(query),
      );
    }
    this.filteredUsers = result;
  }

  toggleUserStatus(user: User) {
    const nextStatus = user.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE';
    if (confirm(`Are you sure you want to transition ${user.name} to ${nextStatus}?`)) {
      this.apiService.updateUserStatus(user.id, nextStatus);
    }
  }

  changeUserRole(userId: string, event: Event) {
    const selectEl = event.target as HTMLSelectElement;
    const newRole = selectEl.value as 'BUYER' | 'SELLER' | 'ADMIN';
    this.apiService.updateUserRole(userId, newRole);
  }
}
