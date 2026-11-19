import React from 'react';
import { Database, Server, Monitor, ShieldCheck, Heart } from 'lucide-react';

export const Footer: React.FC = () => {
  return (
    <footer className="border-t border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 mt-auto py-10 transition-colors">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-8 mb-8">
          {/* Brand & Abstract */}
          <div className="md:col-span-2 space-y-3">
            <h4 className="font-black text-slate-900 dark:text-white text-base tracking-tight">
              Smart Queue and Token Management System
            </h4>
            <p className="text-xs text-slate-500 dark:text-slate-400 max-w-md leading-relaxed">
              Enterprise-grade dual-surface queue management platform featuring an active JavaFX desktop application, Spring Boot 3.3 reactive REST & WebSocket backend, and a modern responsive React web ecosystem sharing a unified MySQL 8 persistent database.
            </p>
            <div className="flex flex-wrap gap-2 pt-1">
              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-blue-50 text-blue-700 dark:bg-blue-950/60 dark:text-blue-300 border border-blue-200 dark:border-blue-900">
                <Server className="w-3 h-3" /> Spring Boot 3.3.5
              </span>
              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-emerald-50 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-900">
                <Database className="w-3 h-3" /> MySQL 8.4 Enterprise
              </span>
              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-50 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-900">
                <Monitor className="w-3 h-3" /> JavaFX 21 & React 18
              </span>
              <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[10px] font-semibold bg-purple-50 text-purple-700 dark:bg-purple-950/60 dark:text-purple-300 border border-purple-200 dark:border-purple-900">
                <ShieldCheck className="w-3 h-3" /> JWT + Refresh Rotation
              </span>
            </div>
          </div>

          {/* Quick Access */}
          <div>
            <h5 className="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-3">
              Portals & Views
            </h5>
            <ul className="space-y-2 text-xs text-slate-500 dark:text-slate-400">
              <li><a href="/customer" className="hover:text-blue-600 dark:hover:text-blue-400 transition-colors">Customer Kiosk & Booking</a></li>
              <li><a href="/display" className="hover:text-blue-600 dark:hover:text-blue-400 transition-colors">Live TV Waiting Board</a></li>
              <li><a href="/staff/login" className="hover:text-blue-600 dark:hover:text-blue-400 transition-colors">Staff Counter Desk</a></li>
              <li><a href="/admin/login" className="hover:text-blue-600 dark:hover:text-blue-400 transition-colors">Administrative Dashboard</a></li>
            </ul>
          </div>

          {/* College Project Submission Info */}
          <div>
            <h5 className="text-xs font-bold uppercase tracking-wider text-slate-700 dark:text-slate-300 mb-3">
              Academic Project
            </h5>
            <ul className="space-y-1.5 text-xs text-slate-500 dark:text-slate-400">
              <li><span className="font-semibold text-slate-700 dark:text-slate-300">Course:</span> Full-Stack Capstone Project</li>
              <li><span className="font-semibold text-slate-700 dark:text-slate-300">Architecture:</span> Clean Layered Architecture</li>
              <li><span className="font-semibold text-slate-700 dark:text-slate-300">Concurrency:</span> Row-Locking & STOMP</li>
              <li><span className="font-semibold text-slate-700 dark:text-slate-300">Status:</span> Production Ready</li>
            </ul>
          </div>
        </div>

        <div className="pt-6 border-t border-slate-200 dark:border-slate-800 flex flex-col sm:flex-row items-center justify-between text-xs text-slate-400">
          <p>© 2026 Smart Queue and Token Management System. All rights reserved.</p>
          <p className="flex items-center gap-1 mt-2 sm:mt-0">
            Engineered with <Heart className="w-3.5 h-3.5 text-rose-500 fill-rose-500" /> for Zero-Downtime Queue Operations
          </p>
        </div>
      </div>
    </footer>
  );
};
