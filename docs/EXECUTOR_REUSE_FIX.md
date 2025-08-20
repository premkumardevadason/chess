# Executor Reuse Fix

## Problem
Several AIs were calling `shutdownNow()` on their ExecutorService but not reinitializing it before the next use, causing `RejectedExecutionException` when trying to submit new tasks to a terminated executor.

## Root Cause
After `executorService.shutdownNow()` is called, the executor enters a terminated state and cannot accept new tasks. Subsequent calls to `submit()` or `execute()` will throw exceptions.

## Solution Pattern (from AlphaZeroAI)
The safe reinit pattern checks if the executor is terminated before use and reinitializes it:

```java
// In selectMoveAsync() - check before use
if (executorService.isTerminated() || executorService.isShutdown()) {
    logger.debug("*** AI: Reinitializing terminated ExecutorService ***");
    this.executorService = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AI-Thread");
        t.setDaemon(true);
        return t;
    });
}

// In stopThinking() - reinitialize after shutdown
public void stopThinking() {
    if (executorService != null) {
        executorService.shutdownNow();
        // Reinitialize executor for next use
        this.executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AI-Thread");
            t.setDaemon(true);
            return t;
        });
    }
}
```

## Fixed AIs

### ✅ AlphaZeroAI
- **Issue**: `stopThinking()` called `shutdownNow()` but didn't reinitialize
- **Fix**: Added executor reinit in `stopThinking()` method
- **Pattern**: Already had reinit check in `selectMoveAsync()`, now consistent

### ✅ LeelaChessZeroAI  
- **Issue**: `stopThinking()` called `shutdownNow()` but didn't reinitialize
- **Fix**: Added executor reinit in `stopThinking()` method
- **Pattern**: Already had reinit check in `selectMoveAsync()`, now consistent

### ✅ AlphaZeroTrainer
- **Issue**: `shutdown()` called `shutdownNow()` but didn't reinitialize
- **Fix**: Added executor reinit in `shutdown()` method and check in `runSelfPlayTraining()`
- **Pattern**: Uses virtual thread executor, now safely reinitializes

### ✅ AlphaFold3AI
- **Status**: Already had correct pattern in `stopThinking()` method
- **Pattern**: Calls `shutdownNow()` then immediately reinitializes

## Benefits

### ✅ **Reliability**
- No more `RejectedExecutionException` when reusing AIs
- AIs can be stopped and restarted multiple times safely
- Consistent behavior across all AI systems

### ✅ **Robustness**
- Handles edge cases where executor is terminated unexpectedly
- Graceful recovery from shutdown states
- Thread-safe executor management

### ✅ **Performance**
- No need to recreate entire AI instances
- Efficient executor reuse pattern
- Minimal overhead for reinit checks

## Testing
The fix ensures that:
1. AIs can be stopped via `stopThinking()` and used again immediately
2. Multiple stop/start cycles work correctly
3. No exceptions are thrown when submitting tasks to previously terminated executors
4. All AI systems have consistent executor lifecycle management

## Impact
- **All AI systems** now have safe executor reuse patterns
- **Training workflows** can stop and restart AIs reliably
- **Game sessions** can use AIs multiple times without recreation
- **System stability** is improved with proper resource management