package ch.nacht.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class CalculationProgressService {

    private final ConcurrentHashMap<Long, CalculationProgress> progressMap = new ConcurrentHashMap<>();

    public void startCalculation(Long orgId, int total) {
        progressMap.put(orgId, new CalculationProgress(total, 0));
    }

    public void updateProgress(Long orgId, int processed) {
        progressMap.computeIfPresent(orgId, (k, v) -> new CalculationProgress(v.total(), processed));
    }

    public CalculationProgress getProgress(Long orgId) {
        return progressMap.getOrDefault(orgId, new CalculationProgress(0, 0));
    }

    public record CalculationProgress(int total, int processed) {}
}
