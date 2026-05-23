import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { AuthService } from '../services/auth.service';
import { tap } from 'rxjs/operators';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const messageService = inject(MessageService);

  const token = authService.getToken();
  if (token) {
    req = req.clone({
      setHeaders: { Authorization: `Bearer ${token}` },
    });
  }

  return next(req).pipe(
    tap({
      error: (err) => {
        if (err.status === 401) {
          messageService.add({ severity: 'error', summary: 'Session Expired', detail: 'Please login again' });
          authService.logout();
          router.navigateByUrl('/login');
        } else if (err.status === 403) {
          messageService.add({ severity: 'error', summary: 'Unauthorized', detail: 'You do not have access' });
        }
      },
    })
  );
};
