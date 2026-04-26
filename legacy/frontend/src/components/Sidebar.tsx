import React from 'react';

export const Sidebar: React.FC = () => {
  return (
    <aside className="w-full lg:w-64 bg-card border-r border-border p-4">
      <div className="space-y-4">
        <h2 className="text-lg font-semibold">Game Info</h2>
        <div className="space-y-2 text-sm">
          <div>Status: Active</div>
          <div>Current Player: White</div>
          <div>Moves: 0</div>
        </div>
      </div>
    </aside>
  );
};
