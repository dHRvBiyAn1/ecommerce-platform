import { Component } from '@angular/core';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { ChartModule } from 'primeng/chart';
import { ButtonModule } from 'primeng/button';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CardModule, TableModule, ChartModule, ButtonModule],
  templateUrl: './admin-dashboard.html',
})
export class AdminDashboard {
  stats = [
    { label: 'Revenue', value: '$2,100', icon: 'pi pi-dollar', color: 'text-green-500', change: '+52%' },
    { label: 'Orders', value: '152', icon: 'pi pi-shopping-cart', color: 'text-blue-500', change: '+24 new' },
    { label: 'Customers', value: '28,441', icon: 'pi pi-users', color: 'text-purple-500', change: '+520' },
    { label: 'Products', value: '1,203', icon: 'pi pi-box', color: 'text-orange-500', change: '+12' },
  ];

  recentSales = [
    { name: 'Bamboo Watch', price: '$65.00', status: 'Shipped' },
    { name: 'Black Watch', price: '$72.00', status: 'Pending' },
    { name: 'Blue Band', price: '$79.00', status: 'Delivered' },
    { name: 'Blue T-Shirt', price: '$29.00', status: 'Shipped' },
    { name: 'Bracelet', price: '$15.00', status: 'Cancelled' },
  ];

  chartData = {
    labels: ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun'],
    datasets: [
      {
        label: 'Revenue',
        data: [1200, 1900, 1600, 2100, 1800, 2400],
        fill: false,
        borderColor: '#3B82F6',
        tension: 0.4,
      },
    ],
  };

  chartOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
  };
}
