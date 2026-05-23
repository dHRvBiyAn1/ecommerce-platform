import { inject } from '@angular/core';
import { Router, type ActivatedRouteSnapshot } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const customerGuard = (_route: ActivatedRouteSnapshot) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const user = authService.user();
  if (user?.roles.includes('ROLE_SELLER')) {
    return router.parseUrl('/seller');
  }

  return true;
};
