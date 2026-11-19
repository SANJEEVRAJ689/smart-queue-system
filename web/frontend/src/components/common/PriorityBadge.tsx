import React from 'react';
import { Priority } from '../../types';
import { ShieldAlert, UserCheck, Users } from 'lucide-react';

interface PriorityBadgeProps {
  priority: Priority;
  isStarving?: boolean;
}

export const PriorityBadge: React.FC<PriorityBadgeProps> = ({ priority, isStarving = false }) => {
  if (priority === 'EMERGENCY') {
    return (
      <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-red-100 text-red-800 border border-red-300 dark:bg-red-950/50 dark:text-red-300 dark:border-red-800">
        <ShieldAlert className="w-3.5 h-3.5 text-red-600 dark:text-red-400" />
        EMERGENCY
      </span>
    );
  }

  if (priority === 'SENIOR') {
    return (
      <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-100 text-amber-800 border border-amber-300 dark:bg-amber-950/50 dark:text-amber-300 dark:border-amber-800">
        <UserCheck className="w-3.5 h-3.5 text-amber-600 dark:text-amber-400" />
        SENIOR
      </span>
    );
  }

  return (
    <div className="inline-flex items-center gap-1.5">
      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-slate-100 text-slate-700 border border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-slate-700">
        <Users className="w-3 h-3 text-slate-500" />
        NORMAL
      </span>
      {isStarving && (
        <span className="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold bg-purple-100 text-purple-700 border border-purple-200 dark:bg-purple-900/50 dark:text-purple-300" title="Promoted by starvation rule (>30 min waiting)">
          PROMOTED &gt;30m
        </span>
      )}
    </div>
  );
};
