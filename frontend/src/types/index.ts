export enum UserRole {
  ADMIN = 'ADMIN',
  AGENT = 'AGENT'
}

export enum ChatStatus {
  BOT_INTERACTION = 'BOT_INTERACTION',
  WAITING_FOR_AGENT = 'WAITING_FOR_AGENT',
  ASSIGNED_TO_AGENT = 'ASSIGNED_TO_AGENT',
  CLOSED = 'CLOSED'
}

export enum SenderType {
  BOT = 'BOT',
  CUSTOMER = 'CUSTOMER',
  AGENT = 'AGENT'
}

export interface User {
  id: number;
  username: string;
  fullName: string;
  role: UserRole;
  active: boolean;
  available: boolean;
  createdAt: string;
  lastLogin: string;
}

export interface LoginResponse {
  token: string;
  userId: number;
  username: string;
  fullName: string;
  role: UserRole;
}

export interface ChatMessage {
  id?: number;
  sessionId: string;
  senderType: SenderType;
  senderId?: number;
  content: string;
  timestamp?: string;
}

export interface ChatSession {
  id: number;
  sessionId: string;
  customerName: string;
  customerEmail: string;
  customerPhone: string;
  problem: string;
  status: ChatStatus;
  agentId?: number;
  agentName?: string;
  createdAt: string;
  assignedAt?: string;
  messages: ChatMessage[];
}

export interface CustomerInfo {
  name: string;
  email: string;
  phone: string;
  problem: string;
  sessionId: string;
}
