import React, { useState, useEffect, useRef } from 'react';
import { ChatStatus, SenderType, ChatMessage, CustomerInfo } from '../types';
import { chatApi } from '../services/api';
import { websocketService } from '../services/websocket';
import { v4 as uuidv4 } from 'uuid';

enum BotStep {
  GREETING = 'GREETING',
  ASK_NAME = 'ASK_NAME',
  ASK_EMAIL = 'ASK_EMAIL',
  ASK_PHONE = 'ASK_PHONE',
  ASK_PROBLEM = 'ASK_PROBLEM',
  COMPLETED = 'COMPLETED'
}

const CustomerChat: React.FC = () => {
  const [sessionId] = useState(() => localStorage.getItem('chatSessionId') || uuidv4());
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [botStep, setBotStep] = useState<BotStep>(BotStep.GREETING);
  const [chatStatus, setChatStatus] = useState<ChatStatus>(ChatStatus.BOT_INTERACTION);
  const [customerInfo, setCustomerInfo] = useState<Partial<CustomerInfo>>({});
  const [isConnected, setIsConnected] = useState(false);
  const [agentName, setAgentName] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    localStorage.setItem('chatSessionId', sessionId);
  }, [sessionId]);

  useEffect(() => {
    const initChat = async () => {
      try {
        await websocketService.connect();
        setIsConnected(true);
        
        websocketService.subscribe(`/topic/chat/${sessionId}`, (message) => {
          const chatMessage = JSON.parse(message.body) as ChatMessage;
          setMessages(prev => [...prev, chatMessage]);
        });

        websocketService.subscribe(`/topic/customer/${sessionId}`, (message) => {
          const session = JSON.parse(message.body);
          if (session.status === ChatStatus.ASSIGNED_TO_AGENT) {
            setChatStatus(ChatStatus.ASSIGNED_TO_AGENT);
            setAgentName(session.agentName);
            addBotMessage(`You are now connected with ${session.agentName}. How can they help you today?`);
          }
        });

        websocketService.subscribe(`/topic/chat/${sessionId}/closed`, () => {
          setChatStatus(ChatStatus.CLOSED);
          addBotMessage('This chat has been closed. Thank you for contacting us!');
        });

        addBotMessage('Welcome to Enterprise Support! I\'m here to help connect you with one of our agents.');
        setTimeout(() => {
          addBotMessage('May I know your name please?');
          setBotStep(BotStep.ASK_NAME);
        }, 1000);
      } catch (error) {
        console.error('Failed to connect:', error);
      }
    };

    initChat();

    return () => {
      websocketService.unsubscribe(`/topic/chat/${sessionId}`);
      websocketService.unsubscribe(`/topic/customer/${sessionId}`);
      websocketService.unsubscribe(`/topic/chat/${sessionId}/closed`);
    };
  }, [sessionId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const addBotMessage = (content: string) => {
    const message: ChatMessage = {
      sessionId,
      senderType: SenderType.BOT,
      content,
      timestamp: new Date().toISOString()
    };
    setMessages(prev => [...prev, message]);
  };

  const addCustomerMessage = (content: string) => {
    const message: ChatMessage = {
      sessionId,
      senderType: SenderType.CUSTOMER,
      content,
      timestamp: new Date().toISOString()
    };
    setMessages(prev => [...prev, message]);
  };

  const validateEmail = (email: string): boolean => {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  };

  const validatePhone = (phone: string): boolean => {
    return /^[\d\s\-+()]{8,}$/.test(phone);
  };

  const handleBotFlow = (userInput: string) => {
    addCustomerMessage(userInput);

    setTimeout(() => {
      switch (botStep) {
        case BotStep.ASK_NAME:
          if (userInput.trim().length < 2) {
            addBotMessage('Please enter a valid name (at least 2 characters).');
            return;
          }
          setCustomerInfo(prev => ({ ...prev, name: userInput.trim() }));
          addBotMessage(`Nice to meet you, ${userInput.trim()}! What's your email address?`);
          setBotStep(BotStep.ASK_EMAIL);
          break;

        case BotStep.ASK_EMAIL:
          if (!validateEmail(userInput.trim())) {
            addBotMessage('Please enter a valid email address (e.g., name@example.com).');
            return;
          }
          setCustomerInfo(prev => ({ ...prev, email: userInput.trim() }));
          addBotMessage('Great! And your phone number?');
          setBotStep(BotStep.ASK_PHONE);
          break;

        case BotStep.ASK_PHONE:
          if (!validatePhone(userInput.trim())) {
            addBotMessage('Please enter a valid phone number.');
            return;
          }
          setCustomerInfo(prev => ({ ...prev, phone: userInput.trim() }));
          addBotMessage('Perfect! Now, please describe your issue or question in detail.');
          setBotStep(BotStep.ASK_PROBLEM);
          break;

        case BotStep.ASK_PROBLEM:
          if (userInput.trim().length < 10) {
            addBotMessage('Please provide more details about your issue (at least 10 characters).');
            return;
          }
          const finalInfo: CustomerInfo = {
            name: customerInfo.name!,
            email: customerInfo.email!,
            phone: customerInfo.phone!,
            problem: userInput.trim(),
            sessionId
          };
          setCustomerInfo(finalInfo);
          setBotStep(BotStep.COMPLETED);
          submitCustomerInfo(finalInfo);
          break;

        default:
          break;
      }
    }, 500);
  };

  const submitCustomerInfo = async (info: CustomerInfo) => {
    try {
      addBotMessage('Thank you for providing your information. Connecting you with an available agent...');
      const session = await chatApi.submitCustomerInfo(info);
      setChatStatus(session.status);
      
      if (session.status === ChatStatus.WAITING_FOR_AGENT) {
        addBotMessage('Please wait while we connect you with an agent. This usually takes less than a minute.');
      }
    } catch (error) {
      console.error('Failed to submit customer info:', error);
      addBotMessage('Sorry, there was an error. Please try again.');
    }
  };

  const handleSendMessage = async () => {
    if (!inputValue.trim()) return;

    const messageContent = inputValue.trim();
    setInputValue('');

    if (chatStatus === ChatStatus.BOT_INTERACTION && botStep !== BotStep.COMPLETED) {
      handleBotFlow(messageContent);
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

  const getStatusBadge = () => {
    switch (chatStatus) {
      case ChatStatus.BOT_INTERACTION:
        return <span style={styles.statusBadge}>🤖 Bot</span>;
      case ChatStatus.WAITING_FOR_AGENT:
        return <span style={{ ...styles.statusBadge, backgroundColor: '#f59e0b' }}>⏳ Waiting for Agent</span>;
      case ChatStatus.ASSIGNED_TO_AGENT:
        return <span style={{ ...styles.statusBadge, backgroundColor: '#10b981' }}>👤 {agentName || 'Agent'}</span>;
      case ChatStatus.CLOSED:
        return <span style={{ ...styles.statusBadge, backgroundColor: '#6b7280' }}>✓ Closed</span>;
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
              key={index}
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
                  {msg.senderType === SenderType.BOT ? '🤖 Bot' : 
                   msg.senderType === SenderType.AGENT ? '👤 Agent' : 'You'}
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
              disabled={chatStatus === ChatStatus.WAITING_FOR_AGENT}
              style={styles.input}
            />
            <button
              onClick={handleSendMessage}
              disabled={!inputValue.trim() || chatStatus === ChatStatus.WAITING_FOR_AGENT}
              style={{
                ...styles.sendButton,
                opacity: !inputValue.trim() || chatStatus === ChatStatus.WAITING_FOR_AGENT ? 0.5 : 1
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
