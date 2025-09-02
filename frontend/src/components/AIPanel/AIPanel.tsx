import React, { useState } from 'react';
import useChessStore from '@/stores/chessStore';

export const AIPanel: React.FC = () => {
  const { aiSystems, trainingStatus, mcpStatus, actions } = useChessStore();
  const [selectedAIs, setSelectedAIs] = useState<string[]>([]);
  const [showQualityReport, setShowQualityReport] = useState(false);

  const handleStartTraining = () => {
    const aisToTrain = selectedAIs.length > 0 ? selectedAIs : Object.keys(aiSystems).filter(name => aiSystems[name].enabled);
    actions.startTraining(aisToTrain);
  };

  const handleStopTraining = () => {
    actions.stopTraining();
  };

  const handleDeleteTraining = (aiName: string) => {
    // TODO: Implement delete training data
    console.log('Delete training for:', aiName);
  };

  const handleEvaluateTraining = (aiName: string) => {
    // TODO: Implement training evaluation
    console.log('Evaluate training for:', aiName);
  };

  const toggleAISelection = (aiName: string) => {
    setSelectedAIs(prev => 
      prev.includes(aiName) 
        ? prev.filter(name => name !== aiName)
        : [...prev, aiName]
    );
  };

  const getTrainingProgress = (aiName: string) => {
    return trainingStatus.progress[aiName] || 0;
  };

  const getTrainingQuality = (aiName: string) => {
    return trainingStatus.quality[aiName];
  };

  const formatTrainingTime = (startTime?: number) => {
    if (!startTime) return '0:00';
    const elapsed = Date.now() - startTime;
    const minutes = Math.floor(elapsed / 60000);
    const seconds = Math.floor((elapsed % 60000) / 1000);
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  };

  return (
    <div className="bg-card border border-border rounded-lg p-4 space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="text-lg font-semibold">AI Training Management</h3>
        <button
          onClick={() => setShowQualityReport(!showQualityReport)}
          className="px-3 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors"
        >
          {showQualityReport ? 'Hide' : 'Show'} Quality Report
        </button>
      </div>
      
      {/* Training Controls */}
      <div className="space-y-3">
        <div className="flex space-x-2">
          <button
            onClick={handleStartTraining}
            disabled={trainingStatus.active}
            className="flex-1 px-3 py-2 bg-green-600 text-white rounded-md hover:bg-green-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed text-sm font-medium"
          >
            {selectedAIs.length > 0 ? `Start Training (${selectedAIs.length})` : 'Start All Training'}
          </button>
          <button
            onClick={handleStopTraining}
            disabled={!trainingStatus.active}
            className="flex-1 px-3 py-2 bg-red-600 text-white rounded-md hover:bg-red-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed text-sm font-medium"
          >
            Stop Training
          </button>
        </div>
        
        {trainingStatus.active && (
          <div className="bg-green-50 border border-green-200 rounded-md p-3">
            <div className="text-sm space-y-1">
              <div className="flex justify-between">
                <span className="font-medium text-green-800">Training Active</span>
                <span className="text-green-600">{formatTrainingTime(trainingStatus.startTime)}</span>
              </div>
              <div className="flex justify-between text-xs text-green-700">
                <span>Games: {trainingStatus.completedGames || 0}/{trainingStatus.totalGames || 0}</span>
                <span>Progress: {Math.round((trainingStatus.completedGames || 0) / (trainingStatus.totalGames || 1) * 100)}%</span>
              </div>
            </div>
          </div>
        )}
      </div>
      
      {/* AI Systems Grid */}
      <div className="space-y-3">
        <h4 className="text-sm font-medium">AI Systems (12 Total)</h4>
        <div className="grid grid-cols-1 gap-2 max-h-64 overflow-y-auto">
          {Object.values(aiSystems).map((ai) => (
            <div
              key={ai.name}
              className={`p-3 rounded-md border transition-colors ${
                selectedAIs.includes(ai.name) ? 'bg-blue-50 border-blue-200' : 'bg-muted border-border'
              }`}
            >
              <div className="flex items-center justify-between mb-2">
                <div className="flex items-center space-x-2">
                  <input
                    type="checkbox"
                    checked={selectedAIs.includes(ai.name)}
                    onChange={() => toggleAISelection(ai.name)}
                    className="rounded"
                  />
                  <span className="font-medium text-sm">{ai.name}</span>
                </div>
                <div className="flex items-center space-x-2">
                  <div className={`w-2 h-2 rounded-full ${
                    ai.status === 'training' ? 'bg-green-500 animate-pulse' :
                    ai.status === 'error' ? 'bg-red-500' :
                    'bg-gray-400'
                  }`} />
                  <span className="text-xs text-muted-foreground capitalize">{ai.status}</span>
                </div>
              </div>
              
              {/* Progress Bar */}
              {ai.status === 'training' && (
                <div className="mb-2">
                  <div className="flex justify-between text-xs text-muted-foreground mb-1">
                    <span>Progress</span>
                    <span>{getTrainingProgress(ai.name)}%</span>
                  </div>
                  <div className="w-full bg-gray-200 rounded-full h-1.5">
                    <div 
                      className="bg-green-600 h-1.5 rounded-full transition-all duration-300"
                      style={{ width: `${getTrainingProgress(ai.name)}%` }}
                    />
                  </div>
                </div>
              )}
              
              {/* Quality Metrics */}
              {showQualityReport && getTrainingQuality(ai.name) && (
                <div className="text-xs text-muted-foreground space-y-1">
                  <div className="flex justify-between">
                    <span>Win Rate:</span>
                    <span className="font-medium">{getTrainingQuality(ai.name).winRate.toFixed(1)}%</span>
                  </div>
                  <div className="flex justify-between">
                    <span>Avg Response:</span>
                    <span className="font-medium">{getTrainingQuality(ai.name).averageResponseTime.toFixed(0)}ms</span>
                  </div>
                  <div className="flex justify-between">
                    <span>Error Rate:</span>
                    <span className="font-medium">{getTrainingQuality(ai.name).errorRate.toFixed(1)}%</span>
                  </div>
                </div>
              )}
              
              {/* Action Buttons */}
              <div className="flex space-x-1 mt-2">
                <button
                  onClick={() => handleEvaluateTraining(ai.name)}
                  className="px-2 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700 transition-colors"
                >
                  Evaluate
                </button>
                <button
                  onClick={() => handleDeleteTraining(ai.name)}
                  className="px-2 py-1 text-xs bg-red-600 text-white rounded hover:bg-red-700 transition-colors"
                >
                  Delete
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>
      
      {/* MCP Status */}
      <div className="pt-4 border-t border-border">
        <h4 className="text-sm font-medium mb-2">MCP Server Status</h4>
        <div className="grid grid-cols-2 gap-4 text-sm">
          <div>
            <div className="flex justify-between">
              <span>Status:</span>
              <span className={`font-medium ${mcpStatus.connected ? 'text-green-600' : 'text-red-600'}`}>
                {mcpStatus.connected ? 'Connected' : 'Disconnected'}
              </span>
            </div>
            <div className="flex justify-between">
              <span>Active Agents:</span>
              <span className="font-medium">{mcpStatus.activeAgents}</span>
            </div>
          </div>
          <div>
            <div className="flex justify-between">
              <span>Total Sessions:</span>
              <span className="font-medium">{mcpStatus.totalSessions}</span>
            </div>
            <div className="flex justify-between">
              <span>Last Activity:</span>
              <span className="font-medium text-xs">
                {mcpStatus.lastActivity ? new Date(mcpStatus.lastActivity).toLocaleTimeString() : 'Never'}
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
