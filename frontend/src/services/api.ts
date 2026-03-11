import axios from 'axios';
import { LoginResponse, ChatSession, ChatMessage, User, CustomerInfo } from '../types';

const API_BASE_URL = 'http://localhost:8080/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
    }
    return Promise.reject(error);
  }
);

export const authApi = {
  login: async (username: string, password: string): Promise<LoginResponse> => {
    const response = await api.post('/auth/login', { username, password });
    return response.data;
  },
  logout: async (): Promise<void> => {
    await api.post('/auth/logout');
  },
};

export const chatApi = {
  initializeChat: async (sessionId: string): Promise<ChatSession> => {
    const response = await api.post('/chat/init', { sessionId });
    return response.data;
  },
  submitCustomerInfo: async (info: CustomerInfo): Promise<ChatSession> => {
    const response = await api.post('/chat/submit-info', info);
    return response.data;
  },
  getSession: async (sessionId: string): Promise<ChatSession> => {
    const response = await api.get(`/chat/session/${sessionId}`);
    return response.data;
  },
  sendMessage: async (
    sessionId: string,
    senderType: string,
    content: string,
    senderId?: number
  ): Promise<ChatMessage> => {
    const response = await api.post('/chat/message', {
      sessionId,
      senderType,
      senderId,
      content,
    });
    return response.data;
  },
};

export const agentApi = {
  getWaitingChats: async (): Promise<ChatSession[]> => {
    const response = await api.get('/agent/waiting-chats');
    return response.data;
  },
  getMyChats: async (agentId: number): Promise<ChatSession[]> => {
    const response = await api.get(`/agent/my-chats/${agentId}`);
    return response.data;
  },
  acceptChat: async (sessionId: string, agentId: number): Promise<ChatSession> => {
    const response = await api.post('/agent/accept-chat', { sessionId, agentId });
    return response.data;
  },
  closeChat: async (sessionId: string): Promise<ChatSession> => {
    const response = await api.post(`/agent/close-chat/${sessionId}`);
    return response.data;
  },
  setAvailability: async (agentId: number, available: boolean): Promise<User> => {
    const response = await api.put(`/agent/availability/${agentId}`, { available });
    return response.data;
  },
};

export const adminApi = {
  createAgent: async (username: string, password: string, fullName: string): Promise<User> => {
    const response = await api.post('/admin/agents', { username, password, fullName });
    return response.data;
  },
  getAllAgents: async (): Promise<User[]> => {
    const response = await api.get('/admin/agents');
    return response.data;
  },
  toggleAgentStatus: async (agentId: number): Promise<User> => {
    const response = await api.put(`/admin/agents/${agentId}/toggle-status`);
    return response.data;
  },
  deleteAgent: async (agentId: number): Promise<void> => {
    await api.delete(`/admin/agents/${agentId}`);
  },
};

export default api;
