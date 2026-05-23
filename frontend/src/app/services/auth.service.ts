import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, tap, of, catchError, map, switchMap } from 'rxjs';
import { environment } from '../../environments/environment';

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
  userType?: 'CUSTOMER' | 'SELLER';
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
}

export interface UserProfile {
  id: string;
  email: string;
  displayName: string;
  imageUrl?: string;
  active: boolean;
  createdAt: string;
  roles: string[];
  permissions: string[];
}

export interface ApiResponse<T> {
  status: number;
  message: string;
  data: T;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  private readonly TOKEN_KEY = 'auth_token';
  private readonly USER_KEY = 'auth_user';

  private userSignal = signal<UserProfile | null>(null);
  private tokenSignal = signal<string | null>(null);

  user = this.userSignal.asReadonly();
  token = this.tokenSignal.asReadonly();
  isAuthenticated = computed(() => this.tokenSignal() !== null);

  constructor() {
    this.restoreSession();
  }

  private restoreSession() {
    const token = localStorage.getItem(this.TOKEN_KEY);
    if (token) {
      this.tokenSignal.set(token);
      const userJson = localStorage.getItem(this.USER_KEY);
      if (userJson) {
        try {
          this.userSignal.set(JSON.parse(userJson));
        } catch {
          this.clearSession();
        }
      }
    }
  }

  login(request: LoginRequest): Observable<UserProfile> {
    const params = new HttpParams()
      .set('grant_type', 'password')
      .set('email', request.email)
      .set('password', request.password);

    return this.http.post<ApiResponse<TokenResponse>>(`${this.apiUrl}/api/auth/token`, null, { params }).pipe(
      tap(response => {
        const token = response.data.accessToken;
        this.tokenSignal.set(token);
        localStorage.setItem(this.TOKEN_KEY, token);
      }),
      switchMap(() => this.fetchProfile()),
    );
  }

  register(request: RegisterRequest): Observable<UserProfile> {
    return this.http.post<ApiResponse<UserProfile>>(`${this.apiUrl}/api/auth/register`, request).pipe(
      map(response => response.data),
    );
  }

  fetchProfile(): Observable<UserProfile> {
    return this.http.get<ApiResponse<UserProfile>>(`${this.apiUrl}/api/user/profile`).pipe(
      map(response => response.data),
      tap(user => {
        this.userSignal.set(user);
        localStorage.setItem(this.USER_KEY, JSON.stringify(user));
      }),
      catchError(() => {
        this.logout();
        return of(null as unknown as UserProfile);
      }),
    );
  }

  logout(): void {
    this.http.post<ApiResponse<void>>(`${this.apiUrl}/api/auth/logout`, null).subscribe({
      error: () => {},
    });
    this.clearSession();
  }

  updateProfile(displayName: string, imageUrl?: string): Observable<UserProfile> {
    return this.http.put<ApiResponse<UserProfile>>(`${this.apiUrl}/api/user/profile`, { displayName, imageUrl }).pipe(
      map(response => response.data),
      tap(user => {
        this.userSignal.set(user);
        localStorage.setItem(this.USER_KEY, JSON.stringify(user));
      }),
    );
  }

  getToken(): string | null {
    return this.tokenSignal();
  }

  private clearSession() {
    this.tokenSignal.set(null);
    this.userSignal.set(null);
    localStorage.removeItem(this.TOKEN_KEY);
    localStorage.removeItem(this.USER_KEY);
  }
}
