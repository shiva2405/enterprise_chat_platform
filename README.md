# Enterprise Chat Platform

A real-time enterprise chat application with bot-assisted customer intake and live agent support.

## Features

- **Customer Chat Widget**: Bot-assisted intake collecting name, email, phone, and problem description
- **Real-time Messaging**: WebSocket-based chat between customers and agents
- **Agent Dashboard**: View waiting customers, accept chats, and manage conversations
- **Admin Panel**: Create and manage agent accounts
- **Authentication**: JWT-based authentication for agents and admins

## Tech Stack

- **Backend**: Java 21, Spring Boot 3.2, WebSocket (STOMP), H2 Database
- **Frontend**: React 18, TypeScript, Vite, SockJS/STOMP
- **Security**: Spring Security, JWT Authentication

## Quick Start

### Prerequisites

- Java 21
- Node.js 18+
- Maven 3.8+

### Start Backend

```bash
cd backend
./mvnw spring-boot:run
```

Backend will start on http://localhost:8080

### Start Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend will start on http://localhost:3000

## Usage

### Customer Flow

1. Open http://localhost:3000
2. The bot will ask for your name, email, phone, and problem description
3. After providing information, you'll be placed in a queue
4. Wait for an agent to accept your chat
5. Once connected, you can chat with the agent in real-time

### Agent/Admin Login

1. Navigate to http://localhost:3000/login
2. Use demo credentials:
   - **Admin**: username: `admin`, password: `admin123`
   - **Agent**: username: `agent1`, password: `agent123`

### Agent Dashboard

- View waiting customers in the queue
- Accept chats to start helping customers
- Send messages in real-time
- Close chats when issues are resolved
- Toggle availability status

### Admin Panel

- Create new agent accounts
- View all agents with status
- Activate/deactivate agents
- Delete agent accounts

## API Endpoints

### Authentication
- `POST /api/auth/login` - Login
- `POST /api/auth/logout` - Logout

### Chat (Public)
- `POST /api/chat/init` - Initialize chat session
- `POST /api/chat/submit-info` - Submit customer information
- `GET /api/chat/session/{sessionId}` - Get session details
- `POST /api/chat/message` - Send message

### Agent (Requires Authentication)
- `GET /api/agent/waiting-chats` - Get waiting chats
- `GET /api/agent/my-chats/{agentId}` - Get agent's active chats
- `POST /api/agent/accept-chat` - Accept a waiting chat
- `POST /api/agent/close-chat/{sessionId}` - Close a chat
- `PUT /api/agent/availability/{agentId}` - Set availability

### Admin (Requires Admin Role)
- `POST /api/admin/agents` - Create new agent
- `GET /api/admin/agents` - List all agents
- `PUT /api/admin/agents/{id}/toggle-status` - Toggle agent status
- `DELETE /api/admin/agents/{id}` - Delete agent

## WebSocket Topics

- `/topic/chat/{sessionId}` - Chat messages
- `/topic/customer/{sessionId}` - Customer notifications
- `/topic/agent/new-chat` - New chat notifications
- `/topic/agent/chat-assigned` - Chat assignment notifications
- `/topic/agent/chat-closed` - Chat closed notifications

## Development

### H2 Console

Access the H2 database console at http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:chatdb`
- Username: `sa`
- Password: (empty)

## License

MIT
