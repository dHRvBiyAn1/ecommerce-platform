import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent } from '../icon/icon.component';

@Component({
  selector: 'app-star-rating',
  standalone: true,
  imports: [CommonModule, IconComponent],
  templateUrl: './star-rating.component.html',
  styleUrl: './star-rating.component.css'
})
export class StarRatingComponent {
  @Input() rating: number = 0;
  @Input() maxStars: number = 5;
  @Input() reviewCount?: number;
  @Input() size: 'sm' | 'md' = 'sm';
  @Input() showCount: boolean = true;

  get stars(): ('full' | 'half' | 'empty')[] {
    const stars: ('full' | 'half' | 'empty')[] = [];
    const fullStars = Math.floor(this.rating);
    const hasHalf = this.rating % 1 >= 0.25 && this.rating % 1 < 0.75;
    const roundUp = this.rating % 1 >= 0.75;

    for (let i = 0; i < this.maxStars; i++) {
      if (i < fullStars + (roundUp ? 1 : 0)) {
        stars.push('full');
      } else if (i === fullStars && hasHalf) {
        stars.push('half');
      } else {
        stars.push('empty');
      }
    }
    return stars;
  }

  get starSize(): number {
    return this.size === 'sm' ? 14 : 18;
  }
}
