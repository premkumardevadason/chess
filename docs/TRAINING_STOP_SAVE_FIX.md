# Training Stop Save Fix - CNN File Creation Issue

## Problem
CNN AI (and other AI systems) were unable to save their final models when training stopped naturally (user clicked stop). The async I/O system was blocking legitimate final saves.

## Symptoms
```
[TrainingDataIOWrapper] *** ASYNC I/O: CNN using NIO.2 async SAVE path (AI enabled: true, Async enabled: true) ***
[AsyncTrainingDataManager] *** ASYNC I/O: Blocking new save request for CNN - training stopped ***
```

## Root Cause
In `AsyncTrainingDataManager.saveOnTrainingStop()`:
1. **`trainingStopRequested = true`** was set BEFORE saving
2. **`clearAllDirtyFlags()`** was clearing all dirty flags after training stop save
3. AI systems trying to save final models after training stop were blocked

## Fix Applied
```java
public CompletableFuture<Void> saveOnTrainingStop() {
    return coordinator.executeAtomicFeature(AtomicFeatureCoordinator.AtomicFeature.TRAINING_STOP_SAVE, () -> {
        // Cancel all queued operations first
        cancelQueuedOperations();
        // Save only dirty data that was marked before training stopped
        saveAllDirtyData().join();
        // Set stop flag AFTER saving to allow final AI saves
        trainingStopRequested = true;
        logger.info("*** ASYNC I/O: Training stop save completed ***");
    });
}
```

## Changes Made
1. **Moved `trainingStopRequested = true`** to AFTER the save operation
2. **Removed `clearAllDirtyFlags()`** call that prevented final AI saves
3. **Preserved existing logic** that allows saves when not shutting down

## Result
- ✅ AI systems can now save final models when training stops naturally
- ✅ Application shutdown still properly blocks saves
- ✅ CNN ZIP files are created when training stops
- ✅ All other AI systems can save final state

## Key Insight
The distinction between:
- **Training stop** (natural completion) - should allow final saves
- **Application shutdown** - should block new saves

Only application shutdown should block saves, not training stop.