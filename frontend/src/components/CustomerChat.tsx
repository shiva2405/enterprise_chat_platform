import React, { useState, useEffect, useRef } from 'react';
import { ChatStatus, SenderType, ChatMessage } from '../types';
import { chatApi } from '../services/api';
import { websocketService } from '../services/websocket';
import { v4 as uuidv4 } from 'uuid';

const CustomerChat: React.FC = () => {
  const [sessionId] = useState(() => localStorage.getItem('chatSessionId') || uuidv4());
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [chatStatus, setChatStatus] = useState<ChatStatus>(ChatStatus.BOT_INTERACTION);
  const [isConnected, setIsConnected] = useState(false);
  const [agentName, setAgentName] = useState<string | null>(null);
  const [isSending, setIsSending] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    localStorage.setItem('chatSessionId', sessionId);
  }, [sessionId]);

  useEffect(() => {
    const initChat = async () => {
      try {
        await websocketService.connect();
        setIsConnected(true);

        websocketService.subscribe(`/topic/customer/${sessionId}`, (message) => {
          const session = JSON.parse(message.body);
          if (session.status === ChatStatus.ASSIGNED_TO_AGENT) {
            setChatStatus(ChatStatus.ASSIGNED_TO_AGENT);
            setAgentName(session.agentName);
          }
        });

        websocketService.subscribe(`/topic/chat/${sessionId}/closed`, () => {
          setChatStatus(ChatStatus.CLOSED);
        });

        const botMessages = await chatApi.startChat(sessionId);
        setMessages(botMessages);
      } catch (error) {
        console.error('Failed to initialize chat:', error);
      }
    };

    initChat();

    return () => {
      websocketService.unsubscribe(`/topic/customer/${sessionId}`);
      websocketService.unsubscribe(`/topic/chat/${sessionId}/closed`);
    };
  }, [sessionId]);

  useEffect(() => {
    if (chatStatus === ChatStatus.ASSIGNED_TO_AGENT) {
      websocketService.subscribe(`/topic/chat/${sessionId}`, (message) => {
        const chatMessage = JSON.parse(message.body) as ChatMessage;
        setMessages(prev => {
          if (prev.find(m => m.id === chatMessage.id)) return prev;
          return [...prev, chatMessage];
        });
      });

      return () => {
        websocketService.unsubscribe(`/topic/chat/${sessionId}`);
      };
    }
  }, [chatStatus, sessionId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async () => {
    if (!inputValue.trim() || isSending) return;

    const messageContent = inputValue.trim();
    setInputValue('');

    if (chatStatus === ChatStatus.BOT_INTERACTION) {
      setIsSending(true);
      try {
        const result = await chatApi.botReply(sessionId, messageContent);

        setMessages(prev => [...prev, result.customerMessage, ...result.botReplies]);

        if (result.status === 'WAITING_FOR_AGENT') {
          setChatStatus(ChatStatus.WAITING_FOR_AGENT);
        }
      } catch (error) {
        console.error('Failed to send bot message:', error);
      } finally {
        setIsSending(false);
      }
    } else if (chatStatus === ChatStatus.ASSIGNED_TO_AGENT) {
      try {
        await chatApi.sendMessage(sessionId, SenderType.CUSTOMER, messageContent);
      } catch (error) {
        console.error('Failed to send message:', error);
      }
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const isInputDisabled = chatStatus === ChatStatus.WAITING_FOR_AGENT || isSending;

  const getStatusBadge = () => {
    switch (chatStatus) {
      case ChatStatus.BOT_INTERACTION:
        return <span style={styles.statusBadge}>Bot</span>;
      case ChatStatus.WAITING_FOR_AGENT:
        return <span style={{ ...styles.statusBadge, backgroundColor: '#f59e0b' }}>Waiting for Agent</span>;
      case ChatStatus.ASSIGNED_TO_AGENT:
        return <span style={{ ...styles.statusBadge, backgroundColor: '#10b981' }}>{agentName || 'Agent'}</span>;
      case ChatStatus.CLOSED:
        return <span style={{ ...styles.statusBadge, backgroundColor: '#6b7280' }}>Closed</span>;
    }
  };

  return (
    <div style={styles.container}>
      <div style={styles.chatBox}>
        <div style={styles.header}>
          <h2 style={styles.title}>Enterprise Support</h2>
          {getStatusBadge()}
        </div>

        <div style={styles.messagesContainer}>
          {messages.map((msg, index) => (
            <div
              key={msg.id || index}
              style={{
                ...styles.messageWrapper,
                justifyContent: msg.senderType === SenderType.CUSTOMER ? 'flex-end' : 'flex-start'
              }}
            >
              <div
                style={{
                  ...styles.message,
                  ...(msg.senderType === SenderType.CUSTOMER
                    ? styles.customerMessage
                    : msg.senderType === SenderType.BOT
                    ? styles.botMessage
                    : styles.agentMessage)
                }}
              >
                <div style={styles.senderLabel}>
                  {msg.senderType === SenderType.BOT ? 'Bot' :
                   msg.senderType === SenderType.AGENT ? 'Agent' : 'You'}
                </div>
                <div>{msg.content}</div>
              </div>
            </div>
          ))}
          <div ref={messagesEndRef} />
        </div>

        {chatStatus !== ChatStatus.CLOSED && (
          <div style={styles.inputContainer}>
            <input
              type="text"
              value={inputValue}
              onChange={(e) => setInputValue(e.target.value)}
              onKeyPress={handleKeyPress}
              placeholder={
                chatStatus === ChatStatus.WAITING_FOR_AGENT
                  ? 'Waiting for an agent...'
                  : 'Type your message...'
              }
              disabled={isInputDisabled}
              style={styles.input}
            />
            <button
              onClick={handleSendMessage}
              disabled={!inputValue.trim() || isInputDisabled}
              style={{
                ...styles.sendButton,
                opacity: !inputValue.trim() || isInputDisabled ? 0.5 : 1
              }}
            >
              Send
            </button>
          </div>
        )}

        {!isConnected && (
          <div style={styles.connectionStatus}>
            Connecting...
          </div>
        )}
      </div>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    padding: '20px',
  },
  chatBox: {
    width: '100%',
    maxWidth: '500px',
    height: '700px',
    backgroundColor: '#fff',
    borderRadius: '20px',
    boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
  },
  header: {
    padding: '20px',
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    color: '#fff',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  title: {
    margin: 0,
    fontSize: '1.25rem',
    fontWeight: 600,
  },
  statusBadge: {
    backgroundColor: '#4f46e5',
    padding: '6px 12px',
    borderRadius: '20px',
    fontSize: '0.75rem',
    fontWeight: 500,
  },
  messagesContainer: {
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
    maxWidth: '80%',
    padding: '12px 16px',
    borderRadius: '16px',
    fontSize: '0.9rem',
    lineHeight: 1.4,
  },
  customerMessage: {
    backgroundColor: '#4f46e5',
    color: '#fff',
    borderBottomRightRadius: '4px',
  },
  botMessage: {
    backgroundColor: '#e5e7eb',
    color: '#1f2937',
    borderBottomLeftRadius: '4px',
  },
  agentMessage: {
    backgroundColor: '#10b981',
    color: '#fff',
    borderBottomLeftRadius: '4px',
  },
  senderLabel: {
    fontSize: '0.7rem',
    opacity: 0.8,
    marginBottom: '4px',
    fontWeight: 500,
  },
  inputContainer: {
    padding: '16px',
    backgroundColor: '#fff',
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
    transition: 'border-color 0.2s',
  },
  sendButton: {
    padding: '12px 24px',
    borderRadius: '24px',
    backgroundColor: '#4f46e5',
    color: '#fff',
    border: 'none',
    fontWeight: 600,
    cursor: 'pointer',
    transition: 'background-color 0.2s',
  },
  connectionStatus: {
    padding: '8px',
    backgroundColor: '#fef3c7',
    textAlign: 'center',
    fontSize: '0.8rem',
    color: '#92400e',
  },
};

export default CustomerChat;
