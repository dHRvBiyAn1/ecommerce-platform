import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { AuthService } from '../services/auth.service';
import { type ActivatedRouteSnapshot } from '@angular/router';

export const roleGuard = (route: ActivatedRouteSnapshot) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const messageService = inject(MessageService);

  const user = authService.user();
  const requiredRoles: string[] = route.data?.['roles'] ?? [];

  if (!user || !requiredRoles.some(r => user.roles?.includes(r))) {
    messageService.add({ severity: 'error', summary: 'Unauthorized', detail: 'You do not have the required permissions' });
    return router.parseUrl('/');
  }

  return true;
};
