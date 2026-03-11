import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { ChatSession, ChatMessage, SenderType, ChatStatus, UserRole } from '../types';
import { agentApi, chatApi } from '../services/api';
import { websocketService } from '../services/websocket';

const AgentDashboard: React.FC = () => {
  const { user, logout, isAdmin } = useAuth();
  const navigate = useNavigate();
  const [waitingChats, setWaitingChats] = useState<ChatSession[]>([]);
  const [myChats, setMyChats] = useState<ChatSession[]>([]);
  const [selectedChat, setSelectedChat] = useState<ChatSession | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [isAvailable, setIsAvailable] = useState(true);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!user) {
      navigate('/login');
      return;
    }

    const initDashboard = async () => {
      await websocketService.connect();
      loadChats();

      websocketService.subscribe('/topic/agent/new-chat', (message) => {
        const chat = JSON.parse(message.body) as ChatSession;
        setWaitingChats(prev => {
          if (prev.find(c => c.sessionId === chat.sessionId)) return prev;
          return [...prev, chat];
        });
      });

      websocketService.subscribe('/topic/agent/chat-assigned', (message) => {
        const chat = JSON.parse(message.body) as ChatSession;
        setWaitingChats(prev => prev.filter(c => c.sessionId !== chat.sessionId));
        if (chat.agentId === user.userId) {
          setMyChats(prev => {
            if (prev.find(c => c.sessionId === chat.sessionId)) return prev;
            return [...prev, chat];
          });
        }
      });

      websocketService.subscribe('/topic/agent/chat-closed', (message) => {
        const chat = JSON.parse(message.body) as ChatSession;
        setMyChats(prev => prev.filter(c => c.sessionId !== chat.sessionId));
        if (selectedChat?.sessionId === chat.sessionId) {
          setSelectedChat(null);
          setMessages([]);
        }
      });
    };

    initDashboard();

    return () => {
      websocketService.unsubscribe('/topic/agent/new-chat');
      websocketService.unsubscribe('/topic/agent/chat-assigned');
      websocketService.unsubscribe('/topic/agent/chat-closed');
    };
  }, [user, navigate]);

  useEffect(() => {
    if (selectedChat) {
      websocketService.subscribe(`/topic/chat/${selectedChat.sessionId}`, (message) => {
        const chatMessage = JSON.parse(message.body) as ChatMessage;
        setMessages(prev => {
          if (prev.find(m => m.id === chatMessage.id)) return prev;
          return [...prev, chatMessage];
        });
      });

      return () => {
        websocketService.unsubscribe(`/topic/chat/${selectedChat.sessionId}`);
      };
    }
  }, [selectedChat]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const loadChats = async () => {
    if (!user) return;
    try {
      const [waiting, mine] = await Promise.all([
        agentApi.getWaitingChats(),
        agentApi.getMyChats(user.userId)
      ]);
      setWaitingChats(waiting);
      setMyChats(mine);
    } catch (error) {
      console.error('Failed to load chats:', error);
    }
  };

  const handleAcceptChat = async (sessionId: string) => {
    if (!user) return;
    try {
      const session = await agentApi.acceptChat(sessionId, user.userId);
      setSelectedChat(session);
      setMessages(session.messages || []);
    } catch (error) {
      console.error('Failed to accept chat:', error);
    }
  };

  const handleSelectChat = async (chat: ChatSession) => {
    try {
      const session = await chatApi.getSession(chat.sessionId);
      setSelectedChat(session);
      setMessages(session.messages || []);
    } catch (error) {
      console.error('Failed to load chat:', error);
    }
  };

  const handleCloseChat = async () => {
    if (!selectedChat) return;
    try {
      await agentApi.closeChat(selectedChat.sessionId);
      setSelectedChat(null);
      setMessages([]);
    } catch (error) {
      console.error('Failed to close chat:', error);
    }
  };

  const handleSendMessage = async () => {
    if (!inputValue.trim() || !selectedChat || !user) return;

    const content = inputValue.trim();
    setInputValue('');

    try {
      await chatApi.sendMessage(selectedChat.sessionId, SenderType.AGENT, content, user.userId);
    } catch (error) {
      console.error('Failed to send message:', error);
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const toggleAvailability = async () => {
    if (!user) return;
    try {
      await agentApi.setAvailability(user.userId, !isAvailable);
      setIsAvailable(!isAvailable);
    } catch (error) {
      console.error('Failed to toggle availability:', error);
    }
  };

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div style={styles.container}>
      <div style={styles.sidebar}>
        <div style={styles.sidebarHeader}>
          <h2 style={styles.sidebarTitle}>Agent Dashboard</h2>
          <div style={styles.userInfo}>
            <span style={styles.userName}>{user?.fullName}</span>
            <span style={{
              ...styles.statusDot,
              backgroundColor: isAvailable ? '#10b981' : '#ef4444'
            }} />
          </div>
        </div>

        <div style={styles.controls}>
          <button
            onClick={toggleAvailability}
            style={{
              ...styles.availabilityBtn,
              backgroundColor: isAvailable ? '#10b981' : '#ef4444'
            }}
          >
            {isAvailable ? '🟢 Available' : '🔴 Unavailable'}
          </button>
          {isAdmin && (
            <button
              onClick={() => navigate('/admin')}
              style={styles.adminBtn}
            >
              👤 Admin Panel
            </button>
          )}
          <button onClick={handleLogout} style={styles.logoutBtn}>
            Logout
          </button>
        </div>

        <div style={styles.chatLists}>
          <div style={styles.chatSection}>
            <h3 style={styles.sectionTitle}>
              Waiting Queue ({waitingChats.length})
            </h3>
            {waitingChats.map(chat => (
              <div
                key={chat.sessionId}
                style={styles.chatItem}
                onClick={() => handleAcceptChat(chat.sessionId)}
              >
                <div style={styles.chatItemHeader}>
                  <span style={styles.customerName}>{chat.customerName}</span>
                  <span style={styles.waitingBadge}>Waiting</span>
                </div>
                <p style={styles.chatProblem}>{chat.problem}</p>
                <span style={styles.chatEmail}>{chat.customerEmail}</span>
              </div>
            ))}
            {waitingChats.length === 0 && (
              <p style={styles.noChats}>No waiting chats</p>
            )}
          </div>

          <div style={styles.chatSection}>
            <h3 style={styles.sectionTitle}>
              My Active Chats ({myChats.length})
            </h3>
            {myChats.map(chat => (
              <div
                key={chat.sessionId}
                style={{
                  ...styles.chatItem,
                  backgroundColor: selectedChat?.sessionId === chat.sessionId ? '#dbeafe' : '#fff'
                }}
                onClick={() => handleSelectChat(chat)}
              >
                <div style={styles.chatItemHeader}>
                  <span style={styles.customerName}>{chat.customerName}</span>
                  <span style={styles.activeBadge}>Active</span>
                </div>
                <p style={styles.chatProblem}>{chat.problem}</p>
              </div>
            ))}
            {myChats.length === 0 && (
              <p style={styles.noChats}>No active chats</p>
            )}
          </div>
        </div>
      </div>

      <div style={styles.chatArea}>
        {selectedChat ? (
          <>
            <div style={styles.chatHeader}>
              <div>
                <h3 style={styles.chatCustomerName}>{selectedChat.customerName}</h3>
                <p style={styles.chatCustomerInfo}>
                  {selectedChat.customerEmail} • {selectedChat.customerPhone}
                </p>
                <p style={styles.chatProblemHeader}>Issue: {selectedChat.problem}</p>
              </div>
              <button onClick={handleCloseChat} style={styles.closeBtn}>
                Close Chat
              </button>
            </div>

            <div style={styles.messagesArea}>
              {messages.map((msg, index) => (
                <div
                  key={index}
                  style={{
                    ...styles.messageWrapper,
                    justifyContent: msg.senderType === SenderType.AGENT ? 'flex-end' : 'flex-start'
                  }}
                >
                  <div
                    style={{
                      ...styles.message,
                      ...(msg.senderType === SenderType.AGENT
                        ? styles.agentMessage
                        : msg.senderType === SenderType.BOT
                        ? styles.botMessage
                        : styles.customerMessage)
                    }}
                  >
                    <div style={styles.senderLabel}>
                      {msg.senderType === SenderType.BOT ? '🤖 Bot' :
                       msg.senderType === SenderType.CUSTOMER ? '👤 Customer' : 'You'}
                    </div>
                    {msg.content}
                  </div>
                </div>
              ))}
              <div ref={messagesEndRef} />
            </div>

            <div style={styles.inputArea}>
              <input
                type="text"
                value={inputValue}
                onChange={(e) => setInputValue(e.target.value)}
                onKeyPress={handleKeyPress}
                placeholder="Type your message..."
                style={styles.input}
              />
              <button
                onClick={handleSendMessage}
                disabled={!inputValue.trim()}
                style={{
                  ...styles.sendBtn,
                  opacity: inputValue.trim() ? 1 : 0.5
                }}
              >
                Send
              </button>
            </div>
          </>
        ) : (
          <div style={styles.noChat}>
            <div style={styles.noChatIcon}>💬</div>
            <h3>Select a chat to start</h3>
            <p>Choose a waiting customer or an active chat from the sidebar</p>
          </div>
        )}
      </div>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    display: 'flex',
    height: '100vh',
    backgroundColor: '#f3f4f6',
  },
  sidebar: {
    width: '350px',
    backgroundColor: '#1e3a8a',
    color: '#fff',
    display: 'flex',
    flexDirection: 'column',
  },
  sidebarHeader: {
    padding: '20px',
    borderBottom: '1px solid rgba(255,255,255,0.1)',
  },
  sidebarTitle: {
    margin: 0,
    fontSize: '1.25rem',
    fontWeight: 600,
  },
  userInfo: {
    display: 'flex',
    alignItems: 'center',
    marginTop: '12px',
    gap: '8px',
  },
  userName: {
    fontSize: '0.875rem',
    opacity: 0.9,
  },
  statusDot: {
    width: '8px',
    height: '8px',
    borderRadius: '50%',
  },
  controls: {
    padding: '16px',
    display: 'flex',
    flexDirection: 'column',
    gap: '8px',
  },
  availabilityBtn: {
    padding: '10px 16px',
    borderRadius: '8px',
    border: 'none',
    color: '#fff',
    fontWeight: 500,
    cursor: 'pointer',
  },
  adminBtn: {
    padding: '10px 16px',
    borderRadius: '8px',
    border: 'none',
    backgroundColor: '#7c3aed',
    color: '#fff',
    fontWeight: 500,
    cursor: 'pointer',
  },
  logoutBtn: {
    padding: '10px 16px',
    borderRadius: '8px',
    border: '1px solid rgba(255,255,255,0.3)',
    backgroundColor: 'transparent',
    color: '#fff',
    fontWeight: 500,
    cursor: 'pointer',
  },
  chatLists: {
    flex: 1,
    overflowY: 'auto',
    padding: '16px',
  },
  chatSection: {
    marginBottom: '24px',
  },
  sectionTitle: {
    fontSize: '0.75rem',
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
    opacity: 0.7,
    marginBottom: '12px',
  },
  chatItem: {
    backgroundColor: '#fff',
    borderRadius: '12px',
    padding: '14px',
    marginBottom: '8px',
    cursor: 'pointer',
    transition: 'transform 0.2s',
    color: '#1f2937',
  },
  chatItemHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '8px',
  },
  customerName: {
    fontWeight: 600,
    color: '#1f2937',
  },
  waitingBadge: {
    backgroundColor: '#f59e0b',
    color: '#fff',
    padding: '2px 8px',
    borderRadius: '12px',
    fontSize: '0.7rem',
    fontWeight: 500,
  },
  activeBadge: {
    backgroundColor: '#10b981',
    color: '#fff',
    padding: '2px 8px',
    borderRadius: '12px',
    fontSize: '0.7rem',
    fontWeight: 500,
  },
  chatProblem: {
    margin: 0,
    fontSize: '0.8rem',
    color: '#6b7280',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
  chatEmail: {
    fontSize: '0.7rem',
    color: '#9ca3af',
  },
  noChats: {
    textAlign: 'center',
    opacity: 0.5,
    fontSize: '0.875rem',
  },
  chatArea: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    backgroundColor: '#fff',
  },
  chatHeader: {
    padding: '20px',
    borderBottom: '1px solid #e5e7eb',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  chatCustomerName: {
    margin: 0,
    fontSize: '1.125rem',
    fontWeight: 600,
  },
  chatCustomerInfo: {
    margin: '4px 0 0',
    fontSize: '0.875rem',
    color: '#6b7280',
  },
  chatProblemHeader: {
    margin: '8px 0 0',
    fontSize: '0.875rem',
    color: '#3b82f6',
    fontWeight: 500,
  },
  closeBtn: {
    padding: '8px 16px',
    borderRadius: '8px',
    backgroundColor: '#ef4444',
    color: '#fff',
    border: 'none',
    fontWeight: 500,
    cursor: 'pointer',
  },
  messagesArea: {
    flex: 1,
    overflowY: 'auto',
    padding: '20px',
    backgroundColor: '#f9fafb',
  },
  messageWrapper: {
    display: 'flex',
    marginBottom: '12px',
  },
  message: {
    maxWidth: '70%',
    padding: '12px 16px',
    borderRadius: '16px',
    fontSize: '0.9rem',
  },
  agentMessage: {
    backgroundColor: '#3b82f6',
    color: '#fff',
    borderBottomRightRadius: '4px',
  },
  customerMessage: {
    backgroundColor: '#e5e7eb',
    color: '#1f2937',
    borderBottomLeftRadius: '4px',
  },
  botMessage: {
    backgroundColor: '#fef3c7',
    color: '#92400e',
    borderBottomLeftRadius: '4px',
  },
  senderLabel: {
    fontSize: '0.7rem',
    opacity: 0.8,
    marginBottom: '4px',
    fontWeight: 500,
  },
  inputArea: {
    padding: '16px',
    borderTop: '1px solid #e5e7eb',
    display: 'flex',
    gap: '12px',
  },
  input: {
    flex: 1,
    padding: '12px 16px',
    borderRadius: '24px',
    border: '2px solid #e5e7eb',
    fontSize: '0.9rem',
    outline: 'none',
  },
  sendBtn: {
    padding: '12px 24px',
    borderRadius: '24px',
    backgroundColor: '#3b82f6',
    color: '#fff',
    border: 'none',
    fontWeight: 600,
    cursor: 'pointer',
  },
  noChat: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    color: '#9ca3af',
  },
  noChatIcon: {
    fontSize: '4rem',
    marginBottom: '16px',
  },
};

export default AgentDashboard;
