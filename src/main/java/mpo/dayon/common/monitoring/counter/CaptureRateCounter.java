package mpo.dayon.common.monitoring.counter;

public class CaptureRateCounter extends RateCounter {
    public CaptureRateCounter(String uid, String shortDescription) {
        super(uid, shortDescription);
    }

    @Override
    public String formatRate(Double rate) {
        if (rate == null || Double.isNaN(rate)) {
            return "- FPS";
        }
        return String.format("%.0f FPS", rate);
    }

    @Override
    public int getWidth() {
        return 80;
    }
}
