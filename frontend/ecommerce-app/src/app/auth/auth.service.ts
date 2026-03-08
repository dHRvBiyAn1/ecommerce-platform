import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject } from 'rxjs';
import { tap } from 'rxjs/operators';

export interface RegisterRequest {
  email: string;
  password: string;
  firstName: string;
  lastName?: string;
  role?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface JwtResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  // refreshToken not included because it's in HTTP-only cookie
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = 'http://localhost:8081/api/v1/users';
  private accessToken: string | null = null;
  private loggedIn = new BehaviorSubject<boolean>(false);

  constructor(private http: HttpClient) {
    this.checkTokenOnInit();
  }

  register(request: RegisterRequest): Observable<any> {
    return this.http.post(`${this.apiUrl}/register`, request);
  }

  login(request: LoginRequest): Observable<JwtResponse> {
    return this.http.post<JwtResponse>(`${this.apiUrl}/login`, request, { withCredentials: true })
      .pipe(tap(response => {
        this.accessToken = response.accessToken;
        this.loggedIn.next(true);
        // Store token in memory only
      }));
  }

  refreshToken(): Observable<JwtResponse> {
    return this.http.post<JwtResponse>(`${this.apiUrl}/refresh`, {}, { withCredentials: true })
      .pipe(tap(response => {
        this.accessToken = response.accessToken;
        this.loggedIn.next(true);
      }));
  }

  logout(): Observable<any> {
    return this.http.post(`${this.apiUrl}/logout`, {}, { withCredentials: true })
      .pipe(tap(() => {
        this.accessToken = null;
        this.loggedIn.next(false);
      }));
  }

  getAccessToken(): string | null {
    return this.accessToken;
  }

  isLoggedIn(): Observable<boolean> {
    return this.loggedIn.asObservable();
  }

  private checkTokenOnInit() {
    // Optionally try to refresh token on app start
    this.refreshToken().subscribe({
      error: () => this.loggedIn.next(false)
    });
  }
}