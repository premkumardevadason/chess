import React, { useState } from 'react';
import useChessStore from '@/stores/chessStore';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { Checkbox } from '@/components/ui/checkbox';

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

  const handleDeleteTraining = (_aiName: string) => {
    // TODO: Implement delete training data
  };

  const handleEvaluateTraining = (_aiName: string) => {
    // TODO: Implement training evaluation
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
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle>AI Training Management</CardTitle>
          <Button
            onClick={() => setShowQualityReport(!showQualityReport)}
            variant="outline"
            size="sm"
          >
            {showQualityReport ? 'Hide' : 'Show'} Quality Report
          </Button>
        </div>
      </CardHeader>
      
      <CardContent className="space-y-6">
        {/* Training Controls */}
        <div className="space-y-3">
          <div className="flex space-x-2">
            <Button
              onClick={handleStartTraining}
              disabled={trainingStatus.active}
              className="flex-1"
              size="sm"
            >
              {selectedAIs.length > 0 ? `Start Training (${selectedAIs.length})` : 'Start All Training'}
            </Button>
            <Button
              onClick={handleStopTraining}
              disabled={!trainingStatus.active}
              variant="destructive"
              className="flex-1"
              size="sm"
            >
              Stop Training
            </Button>
          </div>
          
          {trainingStatus.active && (
            <Card className="bg-green-50 border-green-200">
              <CardContent className="p-3">
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
              </CardContent>
            </Card>
          )}
        </div>
        
        {/* AI Systems Grid */}
        <div className="space-y-3">
          <h4 className="text-sm font-medium">AI Systems (12 Total)</h4>
          <div className="grid grid-cols-1 gap-2 max-h-64 overflow-y-auto">
            {Object.values(aiSystems).map((ai) => (
              <Card
                key={ai.name}
                className={`transition-colors ${
                  selectedAIs.includes(ai.name) ? 'bg-blue-50 border-blue-200' : ''
                }`}
              >
                <CardContent className="p-3">
                  <div className="flex items-center justify-between mb-2">
                    <div className="flex items-center space-x-2">
                      <Checkbox
                        checked={selectedAIs.includes(ai.name)}
                        onCheckedChange={() => toggleAISelection(ai.name)}
                      />
                      <span className="font-medium text-sm">{ai.name}</span>
                    </div>
                    <div className="flex items-center space-x-2">
                      <div className={`w-2 h-2 rounded-full ${
                        ai.status === 'training' ? 'bg-green-500 animate-pulse' :
                        ai.status === 'error' ? 'bg-red-500' :
                        'bg-gray-400'
                      }`} />
                      <Badge variant="outline" className="text-xs">
                        {ai.status}
                      </Badge>
                    </div>
                  </div>
                  
                  {/* Progress Bar */}
                  {ai.status === 'training' && (
                    <div className="mb-2">
                      <div className="flex justify-between text-xs text-muted-foreground mb-1">
                        <span>Progress</span>
                        <span>{getTrainingProgress(ai.name)}%</span>
                      </div>
                      <Progress value={getTrainingProgress(ai.name)} className="h-1.5" />
                    </div>
                  )}
                  
                  {/* Quality Metrics */}
                  {showQualityReport && getTrainingQuality(ai.name) && (
                    <div className="text-xs text-muted-foreground space-y-1 mb-2">
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
                  <div className="flex space-x-1">
                    <Button
                      onClick={() => handleEvaluateTraining(ai.name)}
                      size="sm"
                      variant="outline"
                      className="text-xs px-2 py-1 h-auto"
                    >
                      Evaluate
                    </Button>
                    <Button
                      onClick={() => handleDeleteTraining(ai.name)}
                      size="sm"
                      variant="destructive"
                      className="text-xs px-2 py-1 h-auto"
                    >
                      Delete
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
        
        {/* MCP Status */}
        <div className="pt-4 border-t">
          <h4 className="text-sm font-medium mb-3">MCP Server Status</h4>
          <div className="grid grid-cols-2 gap-4 text-sm">
            <div className="space-y-2">
              <div className="flex justify-between">
                <span>Status:</span>
                <Badge variant={mcpStatus.connected ? "default" : "destructive"}>
                  {mcpStatus.connected ? 'Connected' : 'Disconnected'}
                </Badge>
              </div>
              <div className="flex justify-between">
                <span>Active Agents:</span>
                <span className="font-medium">{mcpStatus.activeAgents}</span>
              </div>
            </div>
            <div className="space-y-2">
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
      </CardContent>
    </Card>
  );
};
