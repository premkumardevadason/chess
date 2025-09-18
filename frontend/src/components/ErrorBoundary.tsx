import React, { Component, ErrorInfo, ReactNode } from 'react';

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error?: Error;
  errorInfo?: ErrorInfo;
  errorType: 'ai_system' | 'websocket' | 'game_logic' | 'unknown';
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, errorType: 'unknown' };
  }

  static getDerivedStateFromError(error: Error): State {
    const errorType = ErrorBoundary.categorizeError(error);
    return {
      hasError: true,
      error,
      errorType
    };
  }

  static categorizeError(error: Error): State['errorType'] {
    if (error.message.includes('AI') || error.message.includes('training')) {
      return 'ai_system';
    }
    if (error.message.includes('WebSocket') || error.message.includes('connection')) {
      return 'websocket';
    }
    if (error.message.includes('move') || error.message.includes('board')) {
      return 'game_logic';
    }
    return 'unknown';
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    this.setState({ errorInfo });
    
    // Log to monitoring service
    console.error('Chess Error Boundary caught an error:', error, errorInfo);
    
    // Send to error tracking service
    if ((window as any).Sentry) {
      (window as any).Sentry.captureException(error, {
        contexts: {
          react: {
            componentStack: errorInfo.componentStack
          }
        }
      });
    }
  }

  handleRetry = () => {
    this.setState({ hasError: false, error: undefined, errorInfo: undefined });
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex items-center justify-center bg-background">
          <div className="max-w-md mx-auto p-6 text-center">
            <div className="mb-6">
              <div className="text-6xl mb-4">♟️</div>
              <h2 className="text-2xl font-bold text-red-600 mb-4">
                {this.getErrorTitle()}
              </h2>
              <p className="text-gray-600 mb-6">
                {this.getErrorMessage()}
              </p>
            </div>
            
            <div className="space-y-4">
              <button
                onClick={this.handleRetry}
                className="w-full px-4 py-2 bg-primary text-primary-foreground rounded-md hover:bg-primary/90 transition-colors"
              >
                Try Again
              </button>
              <button
                onClick={() => window.location.reload()}
                className="w-full px-4 py-2 bg-secondary text-secondary-foreground rounded-md hover:bg-secondary/90 transition-colors"
              >
                Reload Application
              </button>
              <button
                onClick={() => window.location.href = '/'}
                className="w-full px-4 py-2 bg-muted text-muted-foreground rounded-md hover:bg-muted/90 transition-colors"
              >
                Go to Legacy UI
              </button>
            </div>
            
            {(process as any).env.NODE_ENV === 'development' && this.state.error && (
              <details className="mt-6 text-left">
                <summary className="cursor-pointer text-sm text-gray-500 hover:text-gray-700">
                  Error Details (Development)
                </summary>
                <pre className="mt-2 p-4 bg-gray-100 dark:bg-gray-800 rounded text-xs overflow-auto">
                  {this.state.error.toString()}
                  {this.state.errorInfo?.componentStack}
                </pre>
              </details>
            )}
          </div>
        </div>
      );
    }

    return this.props.children;
  }

  private getErrorTitle(): string {
    switch (this.state.errorType) {
      case 'ai_system':
        return 'AI System Error';
      case 'websocket':
        return 'Connection Error';
      case 'game_logic':
        return 'Game Logic Error';
      default:
        return 'Application Error';
    }
  }

  private getErrorMessage(): string {
    switch (this.state.errorType) {
      case 'ai_system':
        return 'An AI system encountered an error. The game can continue with other AI opponents.';
      case 'websocket':
        return 'Connection to the server was lost. Please check your internet connection.';
      case 'game_logic':
        return 'A game logic error occurred. Please try restarting the game.';
      default:
        return 'An unexpected error occurred. Please try refreshing the page.';
    }
  }
}
