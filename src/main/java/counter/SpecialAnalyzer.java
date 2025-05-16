package counter;

import java.util.ArrayList;
import java.util.List;

public class SpecialAnalyzer {
    private final List<Double> data;
    private final double mean;
    private final double variance;
    private final double stdDeviation;

    // 정수 리스트를 입력받는 생성자
    public SpecialAnalyzer(List<Integer> inputData) {
        if (inputData == null || inputData.isEmpty()) {
            throw new IllegalArgumentException("입력 데이터가 비어 있습니다.");
        }
        this.data = convertToDoubleList(inputData);
        this.mean = calculateMean();
        this.variance = calculateVariance();
        this.stdDeviation = Math.sqrt(this.variance);
    }

    // Integer 리스트를 Double 리스트로 변환
    private List<Double> convertToDoubleList(List<Integer> intList) {
        List<Double> doubleList = new ArrayList<>();
        for (int num : intList) {
            doubleList.add((double) num);
        }
        return doubleList;
    }

    private double calculateMean() {
        double sum = 0.0;
        for (double num : data) {
            sum += num;
        }
        return sum / data.size();
    }

    private double calculateVariance() {
        double temp = 0.0;
        for (double num : data) {
            temp += Math.pow(num - mean, 2);
        }
        return temp / data.size();
    }

    public double getMean() {
        return mean;
    }

    public double getVariance() {
        return variance;
    }

    public double getStandardDeviation() {
        return stdDeviation;
    }

    // 정규분포 확률 밀도 함수 (PDF)
    public double getProbabilityDensity(double x) {
        double exponent = -Math.pow(x - mean, 2) / (2 * variance);
        return (1 / (stdDeviation * Math.sqrt(2 * Math.PI))) * Math.exp(exponent);
    }

    public void printStats() {
        System.out.printf("평균: %.4f\n", mean);
        System.out.printf("분산: %.4f\n", variance);
        System.out.printf("표준편차: %.4f\n", stdDeviation);
    }
}