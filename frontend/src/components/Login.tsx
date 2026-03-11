import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { UserRole } from '../types';

const Login: React.FC = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login, user } = useAuth();
  const navigate = useNavigate();

  React.useEffect(() => {
    if (user) {
      if (user.role === UserRole.ADMIN) {
        navigate('/admin');
      } else {
        navigate('/agent');
      }
    }
  }, [user, navigate]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await login(username, password);
    } catch (err: any) {
      setError(err.response?.data?.error || 'Login failed. Please check your credentials.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.container}>
      <div style={styles.loginBox}>
        <div style={styles.header}>
          <h1 style={styles.title}>Enterprise Chat</h1>
          <p style={styles.subtitle}>Agent & Admin Portal</p>
        </div>

        <form onSubmit={handleSubmit} style={styles.form}>
          {error && <div style={styles.error}>{error}</div>}

          <div style={styles.inputGroup}>
            <label style={styles.label}>Username</label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              style={styles.input}
              placeholder="Enter username"
              required
            />
          </div>

          <div style={styles.inputGroup}>
            <label style={styles.label}>Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              style={styles.input}
              placeholder="Enter password"
              required
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            style={{
              ...styles.button,
              opacity: loading ? 0.7 : 1,
            }}
          >
            {loading ? 'Signing in...' : 'Sign In'}
          </button>
        </form>

        <div style={styles.footer}>
          <p style={styles.hint}>Demo Credentials:</p>
          <p style={styles.credentials}>Admin: admin / admin123</p>
          <p style={styles.credentials}>Agent: agent1 / agent123</p>
        </div>
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
    background: 'linear-gradient(135deg, #1e3a8a 0%, #3b82f6 100%)',
    padding: '20px',
  },
  loginBox: {
    width: '100%',
    maxWidth: '400px',
    backgroundColor: '#fff',
    borderRadius: '16px',
    boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
    overflow: 'hidden',
  },
  header: {
    padding: '32px',
    background: 'linear-gradient(135deg, #1e3a8a 0%, #3b82f6 100%)',
    textAlign: 'center',
  },
  title: {
    margin: 0,
    color: '#fff',
    fontSize: '1.75rem',
    fontWeight: 700,
  },
  subtitle: {
    margin: '8px 0 0',
    color: 'rgba(255, 255, 255, 0.8)',
    fontSize: '0.9rem',
  },
  form: {
    padding: '32px',
  },
  inputGroup: {
    marginBottom: '20px',
  },
  label: {
    display: 'block',
    marginBottom: '8px',
    fontSize: '0.875rem',
    fontWeight: 500,
    color: '#374151',
  },
  input: {
    width: '100%',
    padding: '12px 16px',
    borderRadius: '8px',
    border: '2px solid #e5e7eb',
    fontSize: '1rem',
    outline: 'none',
    transition: 'border-color 0.2s',
    boxSizing: 'border-box',
  },
  button: {
    width: '100%',
    padding: '14px',
    borderRadius: '8px',
    backgroundColor: '#3b82f6',
    color: '#fff',
    border: 'none',
    fontSize: '1rem',
    fontWeight: 600,
    cursor: 'pointer',
    transition: 'background-color 0.2s',
    marginTop: '8px',
  },
  error: {
    backgroundColor: '#fef2f2',
    color: '#dc2626',
    padding: '12px',
    borderRadius: '8px',
    marginBottom: '16px',
    fontSize: '0.875rem',
  },
  footer: {
    padding: '16px 32px 24px',
    backgroundColor: '#f9fafb',
    textAlign: 'center',
    borderTop: '1px solid #e5e7eb',
  },
  hint: {
    margin: '0 0 8px',
    color: '#6b7280',
    fontSize: '0.75rem',
    fontWeight: 600,
  },
  credentials: {
    margin: '4px 0',
    color: '#9ca3af',
    fontSize: '0.75rem',
  },
};

export default Login;
