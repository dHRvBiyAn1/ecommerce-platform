import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, BehaviorSubject, of } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { environment } from '../../environments/environment';

export interface Product {
  id: string;
  sku: string;
  name: string;
  description: string;
  categoryId: string;
  categoryName: string;
  price: number;
  stockQuantity: number;
  imageUrls: string[];
  sellerId: string;
  active: boolean;
  brand?: string;
  category?: string;
  ratings?: { average: number; count: number };
}

export interface Category {
  id: string;
  name: string;
  description: string;
  imageUrl?: string;
  parentCategoryId?: string;
}

export interface CartItem {
  product: Product;
  quantity: number;
}

export interface OrderItem {
  productId: string;
  sku: string;
  productName: string;
  imageUrl: string;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
  totalPrice: number;
}

export interface ShippingAddress {
  fullName: string;
  phone: string;
  street: string;
  city: string;
  state?: string;
  zipCode: string;
  country: string;
}

export interface Order {
  id: string;
  orderNumber: string;
  userId: string;
  userEmail: string;
  status: string;
  items: OrderItem[];
  subtotal: number;
  taxAmount: number;
  shippingCost: number;
  discountAmount: number;
  totalAmount: number;
  currency: string;
  shippingAddress: ShippingAddress;
  paymentMethod: string;
  paymentStatus: string;
  couponCode?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
}

export interface User {
  id: string;
  name: string;
  email: string;
  role: 'BUYER' | 'SELLER' | 'ADMIN';
  status: 'ACTIVE' | 'SUSPENDED';
  joinedDate: string;
  avatarUrl?: string;
}

export interface ApiResponse<T> {
  status: number;
  message: string;
  data: T;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);
  private apiUrl = environment.apiUrl;

  private readonly CART_KEY = 'glacier_cart';
  private readonly PRODUCTS_KEY = 'glacier_products';

  private productsSubject = new BehaviorSubject<Product[]>([]);
  products$ = this.productsSubject.asObservable();

  private cartSubject = new BehaviorSubject<CartItem[]>([]);
  cart$ = this.cartSubject.asObservable();

  private categoriesSubject = new BehaviorSubject<Category[]>([]);
  categories$ = this.categoriesSubject.asObservable();

  private ordersSubject = new BehaviorSubject<Order[]>([]);
  orders$ = this.ordersSubject.asObservable();

  private usersSubject = new BehaviorSubject<User[]>([]);
  users$ = this.usersSubject.asObservable();

  constructor() {
    this.restoreCart();
    this.fetchCategories().subscribe();
    this.refreshProducts().subscribe();
  }

  private restoreCart() {
    const savedCart = localStorage.getItem(this.CART_KEY);
    if (savedCart) {
      try {
        this.cartSubject.next(JSON.parse(savedCart));
      } catch {
        this.cartSubject.next([]);
      }
    }
  }

  private saveCart() {
    localStorage.setItem(this.CART_KEY, JSON.stringify(this.cartSubject.value));
  }

  private mapProduct(p: any): Product {
    return {
      id: p.id,
      sku: p.sku || '',
      name: p.name,
      description: p.description || '',
      categoryId: p.categoryId,
      categoryName: p.categoryName || '',
      price: typeof p.price === 'number' ? p.price : parseFloat(p.price) || 0,
      stockQuantity: p.stockQuantity ?? 0,
      imageUrls: p.imageUrls?.length ? p.imageUrls : ['https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&q=80&w=800'],
      sellerId: p.sellerId || '',
      active: p.active ?? true,
      brand: p.brand || '',
      category: p.categoryName || '',
      ratings: p.ratings || { average: 0, count: 0 },
    };
  }

  getProducts(): Observable<Product[]> {
    return this.products$;
  }

  refreshProducts(): Observable<Product[]> {
    return this.http.get<PageResponse<Product> | Product[]>(`${this.apiUrl}/api/v1/products`).pipe(
      map(res => {
        const raw = res && 'content' in res ? res.content : Array.isArray(res) ? res : [];
        return raw.map(p => this.mapProduct(p));
      }),
      tap(products => {
        this.productsSubject.next(products);
        localStorage.setItem(this.PRODUCTS_KEY, JSON.stringify(products));
      }),
      catchError(() => {
        const cached = localStorage.getItem(this.PRODUCTS_KEY);
        if (cached) {
          try {
            this.productsSubject.next(JSON.parse(cached));
          } catch {}
        }
        return of(this.productsSubject.value);
      }),
    );
  }

  getProductById(id: string): Observable<Product | undefined> {
    return this.http.get<Product>(`${this.apiUrl}/api/v1/products/${id}`).pipe(
      map(p => this.mapProduct(p)),
      catchError(() => {
        const prod = this.productsSubject.value.find(p => p.id === id);
        return of(prod);
      }),
    );
  }

  private fetchCategories(): Observable<Category[]> {
    return this.http.get<Category[]>(`${this.apiUrl}/api/v1/categories`).pipe(
      tap(categories => this.categoriesSubject.next(categories)),
      catchError(() => of(this.categoriesSubject.value)),
    );
  }

  createOrder(orderRequest: {
    items: { productId: string; quantity: number }[];
    shippingAddress: { fullName: string; street: string; city: string; zipCode: string; country: string };
    paymentMethod: string;
  }): Observable<Order> {
    return this.http.post<ApiResponse<Order>>(`${this.apiUrl}/api/v1/orders`, orderRequest).pipe(
      map(response => {
        const order = response.data;
        this.ordersSubject.next([order, ...this.ordersSubject.value]);
        this.clearCart();
        return order;
      }),
      catchError(() => {
        const orderNumber = 'GLC-' + Math.floor(100000 + Math.random() * 900000);
        const items = this.cartSubject.value.map(i => ({
          productId: i.product.id,
          sku: i.product.sku,
          productName: i.product.name,
          imageUrl: i.product.imageUrls[0] || '',
          quantity: i.quantity,
          unitPrice: i.product.price,
          discountAmount: 0,
          totalPrice: i.product.price * i.quantity,
        }));
        const total = items.reduce((acc, i) => acc + i.totalPrice, 0);
        const fallbackOrder: Order = {
          id: 'order-' + Date.now(),
          orderNumber,
          userId: '',
          userEmail: '',
          status: 'PROCESSING',
          items,
          subtotal: total,
          taxAmount: total * 0.08,
          shippingCost: total > 50 ? 0 : 5.99,
          discountAmount: 0,
          totalAmount: total + (total * 0.08) + (total > 50 ? 0 : 5.99),
          currency: 'USD',
          shippingAddress: {
            fullName: '',
            phone: '',
            street: orderRequest.shippingAddress.street,
            city: orderRequest.shippingAddress.city,
            zipCode: orderRequest.shippingAddress.zipCode,
            country: orderRequest.shippingAddress.country,
          },
          paymentMethod: orderRequest.paymentMethod,
          paymentStatus: 'PENDING',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        };
        this.ordersSubject.next([fallbackOrder, ...this.ordersSubject.value]);
        this.clearCart();
        return of(fallbackOrder);
      }),
    );
  }

  fetchOrders(): Observable<Order[]> {
    return this.http.get<ApiResponse<PageResponse<Order>>>(`${this.apiUrl}/api/v1/orders`).pipe(
      map(response => response.data.content),
      tap(orders => this.ordersSubject.next(orders)),
      catchError(() => of(this.ordersSubject.value)),
    );
  }

  // --- CART ---
  addToCart(product: Product, quantity = 1) {
    const current = [...this.cartSubject.value];
    const index = current.findIndex(item => item.product.id === product.id);
    if (index > -1) {
      current[index] = { ...current[index], quantity: current[index].quantity + quantity };
    } else {
      current.push({ product, quantity });
    }
    this.cartSubject.next(current);
    this.saveCart();
  }

  removeFromCart(productId: string) {
    const updated = this.cartSubject.value.filter(item => item.product.id !== productId);
    this.cartSubject.next(updated);
    this.saveCart();
  }

  updateCartQuantity(productId: string, quantity: number) {
    if (quantity <= 0) {
      this.removeFromCart(productId);
      return;
    }
    const updated = this.cartSubject.value.map(item =>
      item.product.id === productId ? { ...item, quantity } : item,
    );
    this.cartSubject.next(updated);
    this.saveCart();
  }

  clearCart() {
    this.cartSubject.next([]);
    localStorage.removeItem(this.CART_KEY);
  }

  getCartTotal(): number {
    return this.cartSubject.value.reduce((acc, item) => acc + item.product.price * item.quantity, 0);
  }

  // --- SELLER PRODUCT CRUD ---
  addProduct(product: Product) {
    const request = {
      sku: product.sku,
      name: product.name,
      description: product.description,
      categoryId: product.categoryId,
      price: product.price,
      stockQuantity: product.stockQuantity,
      imageUrls: product.imageUrls,
    };
    return this.http.post<ApiResponse<Product>>(`${this.apiUrl}/api/v1/products`, request).pipe(
      map(response => this.mapProduct(response.data)),
      tap(newProd => {
        const updated = [newProd, ...this.productsSubject.value];
        this.productsSubject.next(updated);
      }),
    );
  }

  updateProductStockAndPrice(id: string, stock: number, price: number) {
    this.http.patch<Product>(`${this.apiUrl}/api/v1/products/${id}/stock`, { stockQuantity: stock }).pipe(
      catchError(() => of(null)),
    ).subscribe(() => {
      const updated = this.productsSubject.value.map(p =>
        p.id === id ? { ...p, stockQuantity: stock, price } : p,
      );
      this.productsSubject.next(updated);
    });
  }

  updateProduct(id: string, data: {
    name: string;
    price: number;
    description: string;
    stockQuantity: number;
    brand: string;
    category: string;
    categoryId: string;
    imageUrl: string;
    sku: string;
  }) {
    const request = {
      sku: data.sku,
      name: data.name,
      description: data.description,
      categoryId: data.categoryId,
      price: data.price,
      stockQuantity: data.stockQuantity,
      imageUrls: [data.imageUrl],
    };
    return this.http.put<ApiResponse<Product>>(`${this.apiUrl}/api/v1/products/${id}`, request).pipe(
      map(response => this.mapProduct(response.data)),
      tap(updated => {
        const list = this.productsSubject.value.map(p => p.id === id ? updated : p);
        this.productsSubject.next(list);
      }),
    );
  }

  deleteProduct(id: string): Observable<boolean> {
    return this.http.delete(`${this.apiUrl}/api/v1/products/${id}`, { responseType: 'text' }).pipe(
      map(() => true),
      tap(() => {
        const updated = this.productsSubject.value.filter(p => p.id !== id);
        this.productsSubject.next(updated);
      }),
      catchError(() => {
        const updated = this.productsSubject.value.filter(p => p.id !== id);
        this.productsSubject.next(updated);
        return of(true);
      }),
    );
  }

  // --- USER MANAGEMENT (Admin) ---
  updateUserStatus(id: string, status: 'ACTIVE' | 'SUSPENDED') {
    const active = status === 'ACTIVE';
    this.http.put(`${this.apiUrl}/api/admin/users/${id}/activate?active=${active}`, null).pipe(
      catchError(() => of(null)),
    ).subscribe(() => {
      const updated = this.usersSubject.value.map(u => (u.id === id ? { ...u, status } : u));
      this.usersSubject.next(updated);
    });
  }

  updateUserRole(id: string, role: 'BUYER' | 'SELLER' | 'ADMIN') {
    const roleName = 'ROLE_' + role;
    this.http.put(`${this.apiUrl}/api/admin/users/${id}/assign-role/${roleName}`, null).pipe(
      catchError(() => of(null)),
    ).subscribe(() => {
      const updated = this.usersSubject.value.map(u => (u.id === id ? { ...u, role } : u));
      this.usersSubject.next(updated);
    });
  }
}
