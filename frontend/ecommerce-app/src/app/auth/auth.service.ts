import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, BehaviorSubject } from 'rxjs';
import { tap } from 'rxjs/operators';

export interface RegisterRequest {
  email: string;
  password: string;
  displayName: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface JwtResponse {
  accessToken: string;
  tokenType: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = 'http://localhost:8081/api/auth';
  private accessToken: string | null = null;
  private loggedIn = new BehaviorSubject<boolean>(false);

  constructor(private http: HttpClient) {
    this.checkTokenOnInit();
  }

  register(request: RegisterRequest): Observable<any> {
    return this.http.post(`${this.apiUrl}/register`, request);
  }

  login(request: LoginRequest): Observable<JwtResponse> {
    const params = new HttpParams()
      .set('grant_type', 'password')
      .set('email', request.email)
      .set('password', request.password);
    return this.http.post<JwtResponse>(`${this.apiUrl}/token`, null, { params, withCredentials: true })
      .pipe(tap(response => {
        this.accessToken = response.accessToken;
        this.loggedIn.next(true);
      }));
  }

  refreshToken(): Observable<JwtResponse> {
    const params = new HttpParams().set('grant_type', 'refresh_token');
    return this.http.post<JwtResponse>(`${this.apiUrl}/token`, null, { params, withCredentials: true })
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

  getUserRole(): string | null {
    if (!this.accessToken) return null;
    try {
      const payload = this.accessToken.split('.')[1];
      const decoded = JSON.parse(atob(payload));
      return decoded.roles || decoded.role || null;
    } catch (e) {
      return null;
    }
  }

  private checkTokenOnInit() {
    this.refreshToken().subscribe({
      error: () => this.loggedIn.next(false)
    });
  }
}