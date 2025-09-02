import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Toaster } from '@/components/ui/toaster';
import { ChessGame } from '@/components/ChessGame';
import { ErrorBoundary } from '@/components/ErrorBoundary';
import { PerformanceMonitor } from '@/components/PerformanceMonitor/PerformanceMonitor';
import { useWebSocket } from '@/hooks/useWebSocket';
import { usePerformanceMonitoring } from '@/hooks/usePerformanceMonitoring';
import './index.css';

// Create a client
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5, // 5 minutes
      retry: 3,
    },
  },
});

function AppContent() {
  const { isConnected, error } = useWebSocket();
  
  // Initialize performance monitoring
  usePerformanceMonitoring();

  return (
    <div className="min-h-screen bg-background text-foreground">
      {/* Connection Status Indicator */}
      <div className="fixed top-4 right-4 z-50">
        <div className={`px-3 py-1 rounded-full text-sm font-medium ${
          isConnected 
            ? 'bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200' 
            : 'bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200'
        }`}>
          {isConnected ? '🟢 Connected' : '🔴 Disconnected'}
        </div>
        {error && (
          <div className="mt-2 px-3 py-1 bg-yellow-100 text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200 rounded text-sm">
            {error}
          </div>
        )}
      </div>

      {/* Main Application */}
      <Router>
        <Routes>
          <Route path="/" element={<ChessGame />} />
          <Route path="*" element={<ChessGame />} />
        </Routes>
      </Router>

      {/* Toast Notifications */}
      <Toaster />
      
      {/* Performance Monitor */}
      <PerformanceMonitor />
    </div>
  );
}

function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <AppContent />
      </QueryClientProvider>
    </ErrorBoundary>
  );
}

export default App;
