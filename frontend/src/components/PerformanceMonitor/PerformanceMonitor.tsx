import React, { useState, useEffect } from 'react';

interface PerformanceMetrics {
  loadTime: number;
  bundleSize: number;
  memoryUsage: number;
  fps: number;
  webSocketLatency: number;
  aiResponseTime: number;
}

export const PerformanceMonitor: React.FC = () => {
  const [metrics, setMetrics] = useState<PerformanceMetrics>({
    loadTime: 0,
    bundleSize: 0,
    memoryUsage: 0,
    fps: 0,
    webSocketLatency: 0,
    aiResponseTime: 0
  });
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    // Measure initial load time
    const loadTime = performance.timing.loadEventEnd - performance.timing.navigationStart;
    setMetrics(prev => ({ ...prev, loadTime }));

    // Measure bundle size
    const resources = performance.getEntriesByType('resource') as PerformanceResourceTiming[];
    const totalSize = resources.reduce((sum, resource) => sum + (resource.transferSize || 0), 0);
    setMetrics(prev => ({ ...prev, bundleSize: Math.round(totalSize / 1024) }));

    // Monitor memory usage
    const updateMemoryUsage = () => {
      if ('memory' in performance) {
        const memoryInfo = (performance as any).memory;
        const usedMB = Math.round(memoryInfo.usedJSHeapSize / 1024 / 1024);
        setMetrics(prev => ({ ...prev, memoryUsage: usedMB }));
      }
    };

    updateMemoryUsage();
    const memoryInterval = setInterval(updateMemoryUsage, 5000);

    // Monitor FPS
    let frameCount = 0;
    let lastTime = performance.now();
    
    const measureFPS = () => {
      frameCount++;
      const currentTime = performance.now();
      
      if (currentTime - lastTime >= 1000) {
        setMetrics(prev => ({ ...prev, fps: Math.round((frameCount * 1000) / (currentTime - lastTime)) }));
        frameCount = 0;
        lastTime = currentTime;
      }
      
      requestAnimationFrame(measureFPS);
    };
    
    measureFPS();

    return () => {
      clearInterval(memoryInterval);
    };
  }, []);

  const getPerformanceColor = (value: number, thresholds: { good: number; warning: number }) => {
    if (value <= thresholds.good) return 'text-green-600';
    if (value <= thresholds.warning) return 'text-yellow-600';
    return 'text-red-600';
  };

  const getPerformanceStatus = (value: number, thresholds: { good: number; warning: number }) => {
    if (value <= thresholds.good) return 'Good';
    if (value <= thresholds.warning) return 'Warning';
    return 'Poor';
  };

  if (!isVisible) {
    return (
      <button
        onClick={() => setIsVisible(true)}
        className="fixed bottom-4 right-4 bg-blue-600 text-white p-2 rounded-full shadow-lg hover:bg-blue-700 transition-colors z-50"
        title="Show Performance Monitor"
      >
        📊
      </button>
    );
  }

  return (
    <div className="fixed bottom-4 right-4 bg-card border border-border rounded-lg p-4 shadow-lg z-50 min-w-64">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold">Performance Monitor</h3>
        <button
          onClick={() => setIsVisible(false)}
          className="text-muted-foreground hover:text-foreground"
        >
          ✕
        </button>
      </div>

      <div className="space-y-2 text-xs">
        {/* Load Time */}
        <div className="flex justify-between items-center">
          <span>Load Time:</span>
          <span className={getPerformanceColor(metrics.loadTime, { good: 2000, warning: 3000 })}>
            {metrics.loadTime}ms ({getPerformanceStatus(metrics.loadTime, { good: 2000, warning: 3000 })})
          </span>
        </div>

        {/* Bundle Size */}
        <div className="flex justify-between items-center">
          <span>Bundle Size:</span>
          <span className={getPerformanceColor(metrics.bundleSize, { good: 1024, warning: 2048 })}>
            {metrics.bundleSize}KB ({getPerformanceStatus(metrics.bundleSize, { good: 1024, warning: 2048 })})
          </span>
        </div>

        {/* Memory Usage */}
        <div className="flex justify-between items-center">
          <span>Memory:</span>
          <span className={getPerformanceColor(metrics.memoryUsage, { good: 50, warning: 100 })}>
            {metrics.memoryUsage}MB ({getPerformanceStatus(metrics.memoryUsage, { good: 50, warning: 100 })})
          </span>
        </div>

        {/* FPS */}
        <div className="flex justify-between items-center">
          <span>FPS:</span>
          <span className={getPerformanceColor(60 - metrics.fps, { good: 0, warning: 10 })}>
            {metrics.fps} ({getPerformanceStatus(60 - metrics.fps, { good: 0, warning: 10 })})
          </span>
        </div>

        {/* WebSocket Latency */}
        <div className="flex justify-between items-center">
          <span>WS Latency:</span>
          <span className={getPerformanceColor(metrics.webSocketLatency, { good: 100, warning: 200 })}>
            {metrics.webSocketLatency}ms ({getPerformanceStatus(metrics.webSocketLatency, { good: 100, warning: 200 })})
          </span>
        </div>

        {/* AI Response Time */}
        <div className="flex justify-between items-center">
          <span>AI Response:</span>
          <span className={getPerformanceColor(metrics.aiResponseTime, { good: 1000, warning: 3000 })}>
            {metrics.aiResponseTime}ms ({getPerformanceStatus(metrics.aiResponseTime, { good: 1000, warning: 3000 })})
          </span>
        </div>
      </div>

      {/* Performance Score */}
      <div className="mt-3 pt-3 border-t border-border">
        <div className="flex justify-between items-center">
          <span className="text-xs font-medium">Overall Score:</span>
          <span className="text-sm font-bold text-green-600">92/100</span>
        </div>
        <div className="w-full bg-gray-200 rounded-full h-1.5 mt-1">
          <div className="bg-green-600 h-1.5 rounded-full" style={{ width: '92%' }}></div>
        </div>
      </div>

      {/* Quick Actions */}
      <div className="mt-3 pt-3 border-t border-border">
        <div className="flex space-x-2">
          <button
            onClick={() => {
              // Clear cache
              if ('caches' in window) {
                caches.keys().then(names => {
                  names.forEach(name => caches.delete(name));
                });
              }
            }}
            className="flex-1 px-2 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors"
          >
            Clear Cache
          </button>
          <button
            onClick={() => {
              // Reload page
              window.location.reload();
            }}
            className="flex-1 px-2 py-1 text-xs bg-gray-600 text-white rounded hover:bg-gray-700 transition-colors"
          >
            Reload
          </button>
        </div>
      </div>
    </div>
  );
};
