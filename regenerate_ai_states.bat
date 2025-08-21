@echo off
echo *** REGENERATING MISSING AI STATE FILES ***
echo.
echo Missing files detected:
echo - chess_qtable.dat (Q-Learning)
echo - chess_deeplearning_model.zip (Deep Learning)
echo - chess_dqn_model.zip + chess_dqn_target_model.zip + chess_dqn_experiences.dat (DQN)
echo - alphafold3_state.dat (AlphaFold3)
echo - ga_population.dat + ga_generation.dat (Genetic Algorithm)
echo - leela_training_games.dat (Leela Chess Zero training counter)
echo.
echo Existing files:
echo + alphazero_cache.dat (AlphaZero)
echo + chess_cnn_model.zip (CNN)
echo + leela_policy.zip + leela_value.zip (Leela Chess Zero networks)
echo.
echo SOLUTION: Start the chess application and run training for each AI system
echo to regenerate the missing state files.
echo.
echo Commands to run in the web interface:
echo 1. Start Q-Learning training (10 games minimum)
echo 2. Start Deep Learning training (5 epochs minimum)  
echo 3. Start DQN training (10 episodes minimum)
echo 4. Start AlphaFold3 training (5 games minimum)
echo 5. Start Genetic Algorithm evolution (20 generations minimum)
echo 6. Start Leela Chess Zero training (10 games minimum)
echo.
pause