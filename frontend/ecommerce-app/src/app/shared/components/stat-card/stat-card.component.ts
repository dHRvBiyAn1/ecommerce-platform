import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-stat-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './stat-card.component.html',
  styleUrl: './stat-card.component.css'
})
export class StatCardComponent {
  @Input() title = '';
  @Input() value = '';
  @Input() trend = '';
  @Input() trendType: 'up' | 'down' = 'up';
  @Input() iconBgClass = 'bg-primary'; 
}
