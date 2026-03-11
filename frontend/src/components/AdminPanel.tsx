import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { User } from '../types';
import { adminApi } from '../services/api';

const AdminPanel: React.FC = () => {
  const { user, logout, isAdmin } = useAuth();
  const navigate = useNavigate();
  const [agents, setAgents] = useState<User[]>([]);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newAgent, setNewAgent] = useState({ username: '', password: '', fullName: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!user || !isAdmin) {
      navigate('/login');
      return;
    }
    loadAgents();
  }, [user, isAdmin, navigate]);

  const loadAgents = async () => {
    try {
      const data = await adminApi.getAllAgents();
      setAgents(data);
    } catch (error) {
      console.error('Failed to load agents:', error);
    }
  };

  const handleCreateAgent = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      await adminApi.createAgent(newAgent.username, newAgent.password, newAgent.fullName);
      setShowCreateModal(false);
      setNewAgent({ username: '', password: '', fullName: '' });
      loadAgents();
    } catch (err: any) {
      setError(err.response?.data?.error || 'Failed to create agent');
    } finally {
      setLoading(false);
    }
  };

  const handleToggleStatus = async (agentId: number) => {
    try {
      await adminApi.toggleAgentStatus(agentId);
      loadAgents();
    } catch (error) {
      console.error('Failed to toggle agent status:', error);
    }
  };

  const handleDeleteAgent = async (agentId: number) => {
    if (!window.confirm('Are you sure you want to delete this agent?')) return;
    try {
      await adminApi.deleteAgent(agentId);
      loadAgents();
    } catch (error) {
      console.error('Failed to delete agent:', error);
    }
  };

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <div>
          <h1 style={styles.title}>Admin Panel</h1>
          <p style={styles.subtitle}>Manage agents and system settings</p>
        </div>
        <div style={styles.headerButtons}>
          <button onClick={() => navigate('/agent')} style={styles.dashboardBtn}>
            📊 Agent Dashboard
          </button>
          <button onClick={handleLogout} style={styles.logoutBtn}>
            Logout
          </button>
        </div>
      </div>

      <div style={styles.content}>
        <div style={styles.card}>
          <div style={styles.cardHeader}>
            <h2 style={styles.cardTitle}>Agents</h2>
            <button
              onClick={() => setShowCreateModal(true)}
              style={styles.createBtn}
            >
              + Create Agent
            </button>
          </div>

          <div style={styles.tableContainer}>
            <table style={styles.table}>
              <thead>
                <tr>
                  <th style={styles.th}>Username</th>
                  <th style={styles.th}>Full Name</th>
                  <th style={styles.th}>Status</th>
                  <th style={styles.th}>Availability</th>
                  <th style={styles.th}>Last Login</th>
                  <th style={styles.th}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {agents.map(agent => (
                  <tr key={agent.id} style={styles.tr}>
                    <td style={styles.td}>{agent.username}</td>
                    <td style={styles.td}>{agent.fullName}</td>
                    <td style={styles.td}>
                      <span style={{
                        ...styles.badge,
                        backgroundColor: agent.active ? '#dcfce7' : '#fee2e2',
                        color: agent.active ? '#166534' : '#991b1b',
                      }}>
                        {agent.active ? 'Active' : 'Inactive'}
                      </span>
                    </td>
                    <td style={styles.td}>
                      <span style={{
                        ...styles.badge,
                        backgroundColor: agent.available ? '#dbeafe' : '#f3f4f6',
                        color: agent.available ? '#1e40af' : '#6b7280',
                      }}>
                        {agent.available ? 'Online' : 'Offline'}
                      </span>
                    </td>
                    <td style={styles.td}>
                      {agent.lastLogin 
                        ? new Date(agent.lastLogin).toLocaleString() 
                        : 'Never'}
                    </td>
                    <td style={styles.td}>
                      <div style={styles.actions}>
                        <button
                          onClick={() => handleToggleStatus(agent.id)}
                          style={{
                            ...styles.actionBtn,
                            backgroundColor: agent.active ? '#fef3c7' : '#dcfce7',
                            color: agent.active ? '#92400e' : '#166534',
                          }}
                        >
                          {agent.active ? 'Deactivate' : 'Activate'}
                        </button>
                        <button
                          onClick={() => handleDeleteAgent(agent.id)}
                          style={styles.deleteBtn}
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {agents.length === 0 && (
              <div style={styles.noData}>
                No agents found. Create your first agent to get started.
              </div>
            )}
          </div>
        </div>

        <div style={styles.statsRow}>
          <div style={styles.statCard}>
            <div style={styles.statNumber}>{agents.length}</div>
            <div style={styles.statLabel}>Total Agents</div>
          </div>
          <div style={styles.statCard}>
            <div style={styles.statNumber}>
              {agents.filter(a => a.active).length}
            </div>
            <div style={styles.statLabel}>Active Agents</div>
          </div>
          <div style={styles.statCard}>
            <div style={styles.statNumber}>
              {agents.filter(a => a.available).length}
            </div>
            <div style={styles.statLabel}>Online Now</div>
          </div>
        </div>
      </div>

      {showCreateModal && (
        <div style={styles.modalOverlay}>
          <div style={styles.modal}>
            <h3 style={styles.modalTitle}>Create New Agent</h3>
            <form onSubmit={handleCreateAgent}>
              {error && <div style={styles.error}>{error}</div>}
              
              <div style={styles.formGroup}>
                <label style={styles.label}>Username</label>
                <input
                  type="text"
                  value={newAgent.username}
                  onChange={(e) => setNewAgent({ ...newAgent, username: e.target.value })}
                  style={styles.input}
                  required
                  minLength={3}
                />
              </div>

              <div style={styles.formGroup}>
                <label style={styles.label}>Full Name</label>
                <input
                  type="text"
                  value={newAgent.fullName}
                  onChange={(e) => setNewAgent({ ...newAgent, fullName: e.target.value })}
                  style={styles.input}
                  required
                />
              </div>

              <div style={styles.formGroup}>
                <label style={styles.label}>Password</label>
                <input
                  type="password"
                  value={newAgent.password}
                  onChange={(e) => setNewAgent({ ...newAgent, password: e.target.value })}
                  style={styles.input}
                  required
                  minLength={6}
                />
              </div>

              <div style={styles.modalButtons}>
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  style={styles.cancelBtn}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={loading}
                  style={{
                    ...styles.submitBtn,
                    opacity: loading ? 0.7 : 1,
                  }}
                >
                  {loading ? 'Creating...' : 'Create Agent'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    minHeight: '100vh',
    backgroundColor: '#f3f4f6',
  },
  header: {
    backgroundColor: '#1e3a8a',
    color: '#fff',
    padding: '24px 32px',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  title: {
    margin: 0,
    fontSize: '1.5rem',
    fontWeight: 600,
  },
  subtitle: {
    margin: '4px 0 0',
    opacity: 0.8,
    fontSize: '0.875rem',
  },
  headerButtons: {
    display: 'flex',
    gap: '12px',
  },
  dashboardBtn: {
    padding: '10px 20px',
    borderRadius: '8px',
    backgroundColor: '#3b82f6',
    color: '#fff',
    border: 'none',
    fontWeight: 500,
    cursor: 'pointer',
  },
  logoutBtn: {
    padding: '10px 20px',
    borderRadius: '8px',
    backgroundColor: 'transparent',
    color: '#fff',
    border: '1px solid rgba(255,255,255,0.3)',
    fontWeight: 500,
    cursor: 'pointer',
  },
  content: {
    padding: '32px',
    maxWidth: '1200px',
    margin: '0 auto',
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: '16px',
    boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
    overflow: 'hidden',
  },
  cardHeader: {
    padding: '20px 24px',
    borderBottom: '1px solid #e5e7eb',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  cardTitle: {
    margin: 0,
    fontSize: '1.125rem',
    fontWeight: 600,
    color: '#1f2937',
  },
  createBtn: {
    padding: '10px 20px',
    borderRadius: '8px',
    backgroundColor: '#10b981',
    color: '#fff',
    border: 'none',
    fontWeight: 500,
    cursor: 'pointer',
  },
  tableContainer: {
    overflowX: 'auto',
  },
  table: {
    width: '100%',
    borderCollapse: 'collapse',
  },
  th: {
    textAlign: 'left',
    padding: '14px 24px',
    backgroundColor: '#f9fafb',
    fontSize: '0.75rem',
    fontWeight: 600,
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
    color: '#6b7280',
  },
  tr: {
    borderBottom: '1px solid #e5e7eb',
  },
  td: {
    padding: '16px 24px',
    fontSize: '0.875rem',
    color: '#1f2937',
  },
  badge: {
    padding: '4px 10px',
    borderRadius: '12px',
    fontSize: '0.75rem',
    fontWeight: 500,
  },
  actions: {
    display: 'flex',
    gap: '8px',
  },
  actionBtn: {
    padding: '6px 12px',
    borderRadius: '6px',
    border: 'none',
    fontSize: '0.75rem',
    fontWeight: 500,
    cursor: 'pointer',
  },
  deleteBtn: {
    padding: '6px 12px',
    borderRadius: '6px',
    backgroundColor: '#fee2e2',
    color: '#991b1b',
    border: 'none',
    fontSize: '0.75rem',
    fontWeight: 500,
    cursor: 'pointer',
  },
  noData: {
    padding: '48px',
    textAlign: 'center',
    color: '#9ca3af',
  },
  statsRow: {
    display: 'grid',
    gridTemplateColumns: 'repeat(3, 1fr)',
    gap: '24px',
    marginTop: '24px',
  },
  statCard: {
    backgroundColor: '#fff',
    borderRadius: '16px',
    padding: '24px',
    textAlign: 'center',
    boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
  },
  statNumber: {
    fontSize: '2.5rem',
    fontWeight: 700,
    color: '#1e3a8a',
  },
  statLabel: {
    fontSize: '0.875rem',
    color: '#6b7280',
    marginTop: '8px',
  },
  modalOverlay: {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
  },
  modal: {
    backgroundColor: '#fff',
    borderRadius: '16px',
    padding: '32px',
    width: '100%',
    maxWidth: '400px',
    boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
  },
  modalTitle: {
    margin: '0 0 24px',
    fontSize: '1.25rem',
    fontWeight: 600,
    color: '#1f2937',
  },
  formGroup: {
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
    boxSizing: 'border-box',
  },
  modalButtons: {
    display: 'flex',
    gap: '12px',
    marginTop: '24px',
  },
  cancelBtn: {
    flex: 1,
    padding: '12px',
    borderRadius: '8px',
    backgroundColor: '#f3f4f6',
    color: '#374151',
    border: 'none',
    fontWeight: 500,
    cursor: 'pointer',
  },
  submitBtn: {
    flex: 1,
    padding: '12px',
    borderRadius: '8px',
    backgroundColor: '#10b981',
    color: '#fff',
    border: 'none',
    fontWeight: 500,
    cursor: 'pointer',
  },
  error: {
    backgroundColor: '#fef2f2',
    color: '#dc2626',
    padding: '12px',
    borderRadius: '8px',
    marginBottom: '16px',
    fontSize: '0.875rem',
  },
};

export default AdminPanel;
