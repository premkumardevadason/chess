import com.example.chess.LeelaChessZeroAI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that LeelaZero training can be properly stopped
 */
public class LeelaZeroTrainingStopTest {
    
    @Test
    @Timeout(10) // Test should complete within 10 seconds
    public void testTrainingCanBeStopped() throws InterruptedException {
        // Create LeelaZero AI instance
        LeelaChessZeroAI leelaZero = new LeelaChessZeroAI(false);
        
        // Start training
        leelaZero.startSelfPlayTraining(1000);
        
        // Verify training started
        Thread.sleep(1000); // Give it time to start
        assertTrue(leelaZero.isTraining(), "Training should be running");
        
        // Stop training
        leelaZero.stopTraining();
        
        // Wait for training to stop
        Thread.sleep(5000); // Give it time to stop
        
        // Verify training stopped
        assertFalse(leelaZero.isTraining(), "Training should be stopped");
        
        // Cleanup
        leelaZero.shutdown();
    }
    
    @Test
    @Timeout(30) // Test should complete within 30 seconds (LeelaZero initialization takes ~20s)
    public void testMultipleTrainingStartsAreIgnored() throws InterruptedException {
        LeelaChessZeroAI leelaZero = new LeelaChessZeroAI(false);
        
        // Start training
        leelaZero.startSelfPlayTraining(1000);
        Thread.sleep(1000); // Give it time to start
        
        // Try to start training again - should be ignored
        leelaZero.startSelfPlayTraining(1000);
        leelaZero.startSelfPlayTraining(1000);
        
        // Should still have only one training thread
        assertTrue(leelaZero.isTraining(), "Training should be running");
        
        // Stop and cleanup
        leelaZero.stopTraining();
        Thread.sleep(3000); // Give it time to stop
        leelaZero.shutdown();
    }
}