import React from 'react';
import { BookTokenResponse } from '../../types';
import { Printer, CheckCircle2, QrCode, ArrowRight } from 'lucide-react';
import { PriorityBadge } from './PriorityBadge';
import { StatusBadge } from './StatusBadge';

interface TicketCardProps {
  ticket: BookTokenResponse;
  onTrackNow?: (trackingCode: string) => void;
}

export const TicketCard: React.FC<TicketCardProps> = ({ ticket, onTrackNow }) => {
  const handlePrint = () => {
    window.print();
  };

  return (
    <div className="flex flex-col items-center max-w-sm mx-auto">
      <div 
        id="printable-ticket" 
        className="w-full bg-white text-slate-900 rounded-2xl shadow-xl border border-slate-200 overflow-hidden relative"
      >
        {/* Ticket Header */}
        <div className="bg-slate-900 text-white p-5 text-center relative">
          <div className="text-xs uppercase tracking-widest font-bold text-blue-400">Smart Queue System</div>
          <h3 className="text-lg font-black tracking-tight mt-0.5">SMART SERVICE DESK</h3>
          <p className="text-xs text-slate-400 mt-1">Official Queue Token</p>
        </div>

        {/* Perforated Divider */}
        <div className="relative flex items-center justify-between px-2 py-1 bg-white">
          <div className="w-5 h-5 -ml-4 rounded-full bg-slate-100 dark:bg-slate-900 border-r border-slate-200"></div>
          <div className="flex-1 border-b-2 border-dashed border-slate-300 mx-2"></div>
          <div className="w-5 h-5 -mr-4 rounded-full bg-slate-100 dark:bg-slate-900 border-l border-slate-200"></div>
        </div>

        {/* Ticket Body */}
        <div className="p-6 text-center space-y-4">
          <div>
            <span className="text-xs font-semibold text-slate-500 uppercase tracking-wider">Your Token Number</span>
            <div className="text-5xl font-black text-blue-600 tracking-tight font-mono mt-1" data-testid="ticket-token-number">
              {ticket.tokenNo}
            </div>
          </div>

          <div className="inline-block bg-slate-50 border border-slate-100 rounded-xl px-4 py-2 w-full">
            <span className="text-xs text-slate-500 font-medium block">Department / Service</span>
            <span className="font-bold text-slate-800 text-base">{ticket.serviceName}</span>
          </div>

          <div className="grid grid-cols-2 gap-3 text-left bg-blue-50/60 rounded-xl p-3 border border-blue-100">
            <div>
              <span className="text-[11px] font-semibold text-blue-700/70 block uppercase">Queue Position</span>
              <span className="text-xl font-black text-blue-900 font-mono">#{ticket.queuePosition ?? ticket.positionInQueue ?? 1}</span>
            </div>
            <div>
              <span className="text-[11px] font-semibold text-blue-700/70 block uppercase">Est. Wait</span>
              <span className="text-xl font-black text-blue-900 font-mono">~{ticket.estimatedWaitMinutes}m</span>
            </div>
          </div>

          <div className="pt-2 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
            <div>
              <span className="block text-slate-400">Customer</span>
              <span className="font-medium text-slate-700">{ticket.phoneMasked || ticket.maskedPhone || 'Guest'}</span>
            </div>
            <div className="text-right">
              <span className="block text-slate-400">Date</span>
              <span className="font-mono text-slate-700">{ticket.queueDate || new Date().toISOString().split('T')[0]}</span>
            </div>
          </div>

          {/* Tracking QR representation */}
          <div className="bg-slate-50 p-3 rounded-xl border border-slate-100 flex items-center justify-center gap-3">
            <QrCode className="w-9 h-9 text-slate-700" />
            <div className="text-left">
              <span className="text-[10px] text-slate-500 font-semibold block uppercase">Tracking Passcode</span>
              <span className="text-xs font-mono font-bold text-slate-900 tracking-wider select-all">{ticket.trackingCode}</span>
            </div>
          </div>

          <p className="text-[11px] text-slate-400 italic">Please watch the TV display boards. Your number will be called shortly.</p>
        </div>
      </div>

      {/* Ticket Actions (Hidden in Print) */}
      <div className="flex gap-3 mt-5 w-full print:hidden">
        <button
          onClick={handlePrint}
          className="flex-1 inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl border border-slate-300 dark:border-slate-700 bg-white dark:bg-slate-800 text-slate-700 dark:text-slate-200 text-sm font-semibold hover:bg-slate-50 dark:hover:bg-slate-700 shadow-xs transition-colors"
        >
          <Printer className="w-4 h-4" />
          Print Ticket
        </button>

        {onTrackNow && (
          <button
            onClick={() => onTrackNow(ticket.trackingCode)}
            className="flex-1 inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold shadow-md shadow-blue-500/20 transition-all"
          >
            Track Live
            <ArrowRight className="w-4 h-4" />
          </button>
        )}
      </div>
    </div>
  );
};
