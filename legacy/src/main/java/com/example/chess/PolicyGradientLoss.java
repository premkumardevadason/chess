package com.example.chess;

import org.nd4j.linalg.activations.IActivation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.lossfunctions.ILossFunction;
import org.nd4j.linalg.ops.transforms.Transforms;
import org.nd4j.shade.jackson.annotation.JsonInclude;
import org.nd4j.shade.jackson.databind.JsonNode;
import org.nd4j.shade.jackson.databind.ObjectMapper;
import org.nd4j.shade.jackson.databind.node.ObjectNode;

/**
 * Custom A3C Policy Gradient Loss Function
 * L_actor = -advantage * log(π(a|s)) - β * H(π)
 * 
 * IMPORTANT: labels are expected to be one-hot vectors weighted by advantages,
 * NOT raw one-hot vectors. Format: labels = one_hot_action * advantage_value
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PolicyGradientLoss implements ILossFunction {
    
    private static final long serialVersionUID = 2555999453717725796L;
	private volatile double entropyCoeff;
    private static final double EPS = 1e-8;
    
    // Default constructor for JSON deserialization
    public PolicyGradientLoss() {
        this.entropyCoeff = 0.01; // Default value
    }
    
    public PolicyGradientLoss(double entropyCoeff) {
        this.entropyCoeff = entropyCoeff;
    }
    
    public double computeScore(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask, boolean average) {
        // labels = one_hot_action * advantage (not raw one-hot)
        INDArray y = activationFn.getActivation(preOutput, false); // Safe activation
        INDArray yStable = y.add(EPS);
        INDArray logY = Transforms.log(yStable);
        
        INDArray policy = labels.mul(logY).sum(1).neg();
        INDArray entropy = yStable.mul(logY).sum(1).mul(entropyCoeff);
        policy.addi(entropy);
        
        if (mask != null) policy.muli(mask);
        double score = policy.sumNumber().doubleValue();
        return average ? score / y.size(0) : score;
    }
    
    public INDArray computeScoreArray(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask) {
        INDArray y = activationFn.getActivation(preOutput, false);
        INDArray yStable = y.add(EPS);
        INDArray logY = Transforms.log(yStable);
        
        INDArray policy = labels.mul(logY).sum(1).neg();
        INDArray entropy = yStable.mul(logY).sum(1).mul(entropyCoeff);
        policy.addi(entropy);
        
        if (mask != null) policy.muli(mask);
        return policy;
    }
    
    public INDArray computeGradient(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask) {
        // labels = one_hot_action * advantage (not raw one-hot)
        INDArray y = activationFn.getActivation(preOutput, false);
        INDArray yStable = y.add(EPS);
        
        INDArray dLdY = labels.neg().divi(yStable);
        INDArray logY = Transforms.log(yStable);
        logY.addi(1.0).muli(entropyCoeff);
        dLdY.addi(logY);
        
        // Efficient gradient clipping
        INDArray clipped = Transforms.min(Transforms.max(dLdY, -10.0), 10.0);
        
        if (mask != null) clipped.muli(mask);
        return activationFn.backprop(preOutput, clipped).getFirst();
    }
    
    @Override
    public String name() {
        return "PolicyGradientLoss";
    }
    
    public JsonNode toJson() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode json = mapper.createObjectNode();
        json.put("@class", this.getClass().getName());
        json.put("entropyCoeff", entropyCoeff);
        return json;
    }
    
    @Override
    public org.nd4j.common.primitives.Pair<Double, INDArray> computeGradientAndScore(INDArray labels, INDArray preOutput, IActivation activationFn, INDArray mask, boolean average) {
        double score = computeScore(labels, preOutput, activationFn, mask, average);
        INDArray gradient = computeGradient(labels, preOutput, activationFn, mask);
        return org.nd4j.common.primitives.Pair.of(score, gradient);
    }
    
    public void setEntropyCoeff(double entropyCoeff) {
        this.entropyCoeff = entropyCoeff;
    }
    
    @Override
    public String toString() {
        return "PolicyGradientLoss(entropyCoeff=" + entropyCoeff + ")";
    }
}