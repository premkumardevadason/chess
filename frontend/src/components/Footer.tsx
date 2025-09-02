import React from 'react';

export const Footer: React.FC = () => {
  return (
    <footer className="bg-card border-t border-border">
      <div className="container mx-auto px-4 py-3">
        <div className="flex items-center justify-between text-sm text-muted-foreground">
          <div>
            © 2025 Chess AI Game - React UI v2.0.0
          </div>
          <div className="flex items-center space-x-4">
            <span>12 AI Systems</span>
            <span>•</span>
            <span>PWA Ready</span>
          </div>
        </div>
      </div>
    </footer>
  );
};
