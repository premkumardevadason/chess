package com.example.chess.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "chess.ai")
public class AIStateConfig {
    
    private State state = new State();
    
    public State getState() {
        return state;
    }
    
    public void setState(State state) {
        this.state = state;
    }
    
    public static class State {
        private String directory = "state";
        private File file = new File();
        
        public String getDirectory() {
            return directory;
        }
        
        public void setDirectory(String directory) {
            this.directory = directory;
        }
        
        public File getFile() {
            return file;
        }
        
        public void setFile(File file) {
            this.file = file;
        }
        
        public static class File {
            private String qlearning = "chess_qtable.dat.gz";
            private String deeplearning = "chess_deeplearning_model.zip";
            private String deeplearningcnn = "chess_cnn_model.zip";
            private String dqn = "chess_dqn_model.zip";
            private String alphazero = "alphazero_model.zip";
            private Leelazerochess leelazerochess = new Leelazerochess();
            private String genetic = "ga_population.dat";
            private String alphafold3 = "alphafold3_state.dat";
            private String a3c = "a3c_model.zip";
            
            public String getQlearning() { return qlearning; }
            public void setQlearning(String qlearning) { this.qlearning = qlearning; }
            
            public String getDeeplearning() { return deeplearning; }
            public void setDeeplearning(String deeplearning) { this.deeplearning = deeplearning; }
            
            public String getDeeplearningcnn() { return deeplearningcnn; }
            public void setDeeplearningcnn(String deeplearningcnn) { this.deeplearningcnn = deeplearningcnn; }
            
            public String getDqn() { return dqn; }
            public void setDqn(String dqn) { this.dqn = dqn; }
            
            public String getAlphazero() { return alphazero; }
            public void setAlphazero(String alphazero) { this.alphazero = alphazero; }
            
            public Leelazerochess getLeelazerochess() { return leelazerochess; }
            public void setLeelazerochess(Leelazerochess leelazerochess) { this.leelazerochess = leelazerochess; }
            
            public static class Leelazerochess {
                private String policy = "leela_policy.zip";
                private String value = "leela_value.zip";
                private String games = "leela_training_games.dat";
                
                public String getPolicy() { return policy; }
                public void setPolicy(String policy) { this.policy = policy; }
                
                public String getValue() { return value; }
                public void setValue(String value) { this.value = value; }
                
                public String getGames() { return games; }
                public void setGames(String games) { this.games = games; }
            }
            
            public String getGenetic() { return genetic; }
            public void setGenetic(String genetic) { this.genetic = genetic; }
            
            public String getAlphafold3() { return alphafold3; }
            public void setAlphafold3(String alphafold3) { this.alphafold3 = alphafold3; }
            
            public String getA3c() { return a3c; }
            public void setA3c(String a3c) { this.a3c = a3c; }
        }
    }
    
    // Utility methods to get full paths
    public String getQlearningPath() {
        return state.directory + "/" + state.file.qlearning;
    }
    
    public String getDeeplearningPath() {
        return state.directory + "/" + state.file.deeplearning;
    }
    
    public String getDeeplearningCnnPath() {
        return state.directory + "/" + state.file.deeplearningcnn;
    }
    
    public String getDqnPath() {
        return state.directory + "/" + state.file.dqn;
    }
    
    public String getAlphazeroPath() {
        return state.directory + "/" + state.file.alphazero;
    }
    
    public String getLeelazerochessPolicyPath() {
        return state.directory + "/" + state.file.leelazerochess.policy;
    }
    
    public String getLeelazerochessValuePath() {
        return state.directory + "/" + state.file.leelazerochess.value;
    }
    
    public String getLeelazerochessGamesPath() {
        return state.directory + "/" + state.file.leelazerochess.games;
    }
    
    public String getGeneticPath() {
        return state.directory + "/" + state.file.genetic;
    }
    
    public String getAlphafold3Path() {
        return state.directory + "/" + state.file.alphafold3;
    }
    
    public String getA3cPath() {
        return state.directory + "/" + state.file.a3c;
    }
    
    public String getDqnExperiencesPath() {
        return state.directory + "/chess_dqn_experiences.dat";
    }
    
    public String getDqnTargetPath() {
        return state.directory + "/chess_dqn_target_model.zip";
    }
    
    public String getAlphazeroCachePath() {
        return state.directory + "/alphazero_cache.dat";
    }
}