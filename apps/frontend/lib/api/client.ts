const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || '';

export interface ApiError {
  status: number;
  message: string;
  detail?: string;
  details?: string[];
  data?: any;
}

export class ApiClient {
  static async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const url = `${API_BASE_URL}${endpoint}`;
    const defaultHeaders: HeadersInit = {
      'Content-Type': 'application/json',
    };

    let response: Response;
    try {
      response = await fetch(url, {
        ...options,
        headers: {
          ...defaultHeaders,
          ...(options.headers || {}),
        },
        credentials: 'include', // Include cookies for session
      });
    } catch (networkErr: any) {
      const error: ApiError = {
        status: 0,
        message: 'Falha na comunicação com o servidor. Tente novamente.',
        data: networkErr,
      };
      throw error;
    }

    // Handle non-JSON responses
    const contentType = response.headers.get('content-type');
    const isJson = contentType?.includes('application/json');

    if (!response.ok) {
      let errorData;
      try {
        errorData = isJson ? await response.json() : await response.text();
      } catch {
        errorData = null;
      }

      const detail = errorData?.detail;
      const details = errorData?.details;

      const error: ApiError = {
        status: response.status,
        message: detail || errorData?.error || errorData?.message || response.statusText,
        detail,
        details,
        data: errorData,
      };
      throw error;
    }

    // Return null for 204 No Content
    if (response.status === 204) {
      return null as T;
    }

    if (!isJson) {
      return response.text() as T;
    }

    return response.json() as Promise<T>;
  }

  static get<T>(endpoint: string) {
    return this.request<T>(endpoint, { method: 'GET' });
  }

  static post<T>(endpoint: string, body?: any) {
    return this.request<T>(endpoint, {
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  static put<T>(endpoint: string, body?: any) {
    return this.request<T>(endpoint, {
      method: 'PUT',
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  static delete<T>(endpoint: string) {
    return this.request<T>(endpoint, { method: 'DELETE' });
  }
}
