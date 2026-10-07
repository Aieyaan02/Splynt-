package com.aieyaan.splynt.advice;
import java.util.List;
public record AdviceContent(String summary, List<Action> actions, List<Experiment> productExperiments, List<String> limitations) {
    public record Action(String title, String rationale, String nextStep, List<String> evidenceIds) {}
    public record Experiment(String productIdea, String hypothesis, String smallTest, String measure, List<String> evidenceIds) {}
}
