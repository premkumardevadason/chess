import React from 'react';

export const Header: React.FC = () => {
  return (
    <header className="bg-card border-b border-border">
      <div className="container mx-auto px-4 py-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-4">
            <h1 className="text-2xl font-bold text-foreground">
              ♟️ Chess AI Game
            </h1>
            <span className="text-sm text-muted-foreground">
              New Age Interface
            </span>
          </div>
          
          <div className="flex items-center space-x-4">
            <a
              href="/"
              className="text-sm text-muted-foreground hover:text-foreground transition-colors"
            >
              ← Legacy UI
            </a>
            <div className="text-sm text-muted-foreground">
              v2.0.0
            </div>
          </div>
        </div>
      </div>
    </header>
  );
};
