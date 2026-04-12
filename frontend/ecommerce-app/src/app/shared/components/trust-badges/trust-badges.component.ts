import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-trust-badges',
  standalone: true,
  imports: [CommonModule, IconComponent],
  templateUrl: './trust-badges.component.html',
  styleUrl: './trust-badges.component.css'
})
export class TrustBadgesComponent {
  badges = [
    { icon: 'truck', title: 'Free Shipping', subtitle: 'On orders over $50' },
    { icon: 'refresh', title: '30-Day Returns', subtitle: 'Hassle-free refunds' },
    { icon: 'shield', title: 'Secure Payment', subtitle: 'SSL encrypted checkout' },
    { icon: 'headphones', title: '24/7 Support', subtitle: 'Dedicated help center' }
  ];
}
