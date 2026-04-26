package com.example.chess.mcp.agent;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Thread-safe FIFO queue for cross-session move communication
 */
public class SessionMoveQueue {
    
    private static final Logger logger = LogManager.getLogger(SessionMoveQueue.class);
    
    private final BlockingQueue<String> moveQueue = new LinkedBlockingQueue<>();
    private final String sessionName;
    
    public SessionMoveQueue(String sessionName) {
        this.sessionName = sessionName;
    }
    
    /**
     * Add move to queue (thread-safe)
     */
    public void enqueue(String move) {
        moveQueue.offer(move);
        logger.debug("📥 Queued move to " + sessionName + " queue: " + move);
    }
    
    /**
     * Get next move from queue (non-blocking)
     * @return next move or null if queue is empty
     */
    public String dequeue() {
        String move = moveQueue.poll();
        if (move != null) {
            logger.debug("📤 Dequeued move from " + sessionName + " queue: " + move);
        }
        return move;
    }
    
    /**
     * Check if queue has moves available
     */
    public boolean hasMove() {
        return !moveQueue.isEmpty();
    }
    
    /**
     * Get queue size
     */
    public int size() {
        return moveQueue.size();
    }
    
    /**
     * Clear all moves from queue
     */
    public void clear() {
        moveQueue.clear();
        logger.debug("🧹 Cleared " + sessionName + " queue");
    }
}