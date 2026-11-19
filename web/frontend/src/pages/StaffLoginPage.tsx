import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Briefcase, Lock, User, AlertCircle, ArrowRight } from 'lucide-react';
import { ErrorState } from '../components/common/StateFeedback';

export const StaffLoginPage: React.FC = () => {
  const { login, user } = useAuth();
  const navigate = useNavigate();

  const [username, setUsername] = useState<string>('staff1');
  const [password, setPassword] = useState<string>('Staff@123');
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  // If already logged in as staff, redirect directly
  React.useEffect(() => {
    if (user?.role === 'STAFF') {
      navigate('/staff');
    }
  }, [user, navigate]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username.trim() || !password) {
      setError('Please provide both username and password.');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await login('STAFF', username.trim(), password);
      navigate('/staff');
    } catch (err: any) {
      setError(err.message || 'Staff authentication failed. Check credentials.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-[80vh] flex flex-col justify-center items-center px-4 py-12">
      <div className="w-full max-w-md bg-white dark:bg-slate-800 rounded-3xl p-8 border border-slate-200 dark:border-slate-700/80 shadow-xl space-y-6">
        <div className="text-center space-y-2">
          <div className="w-14 h-14 rounded-2xl bg-emerald-600 text-white flex items-center justify-center mx-auto shadow-lg shadow-emerald-500/25">
            <Briefcase className="w-7 h-7" />
          </div>
          <h2 className="text-2xl font-black text-slate-900 dark:text-white tracking-tight">Staff Desk Station</h2>
          <p className="text-xs text-slate-500 dark:text-slate-400">
            Sign in with your staff credentials to access counter controls and call tickets.
          </p>
        </div>

        {error && <ErrorState message={error} />}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="space-y-1">
            <label className="block text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300">
              Staff Username
            </label>
            <div className="relative">
              <User className="w-4 h-4 text-slate-400 absolute left-3.5 top-3.5" />
              <input
                type="text"
                required
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="staff1"
                className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900 text-sm font-medium focus:border-emerald-600 focus:bg-white dark:focus:bg-slate-900 transition-colors"
                data-testid="staff-input-username"
              />
            </div>
          </div>

          <div className="space-y-1">
            <label className="block text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300">
              Password
            </label>
            <div className="relative">
              <Lock className="w-4 h-4 text-slate-400 absolute left-3.5 top-3.5" />
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50 dark:bg-slate-900 text-sm font-medium focus:border-emerald-600 focus:bg-white dark:focus:bg-slate-900 transition-colors"
                data-testid="staff-input-password"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full py-3 rounded-2xl bg-emerald-600 hover:bg-emerald-700 text-white font-bold text-sm shadow-lg shadow-emerald-500/25 transition-all flex items-center justify-center gap-2 disabled:opacity-50"
            data-testid="staff-btn-login"
          >
            {loading ? 'Authenticating...' : 'Sign In to Desk'}
            <ArrowRight className="w-4 h-4" />
          </button>
        </form>

        <div className="pt-4 border-t border-slate-100 dark:border-slate-700/60 text-center">
          <p className="text-xs text-slate-400">
            Default seeded credentials: <code className="font-mono font-bold text-slate-700 dark:text-slate-300">staff1 / Staff@123</code>
          </p>
          <Link to="/admin/login" className="inline-block mt-2 text-xs font-semibold text-purple-600 hover:text-purple-700 dark:text-purple-400">
            Looking for Admin Portal? Click here &rarr;
          </Link>
        </div>
      </div>
    </div>
  );
};
