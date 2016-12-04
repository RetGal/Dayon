package mpo.dayon.assistant.monitoring.counter;

public class CaptureCompressionCounter extends AverageValueCounter
{
    public CaptureCompressionCounter(String uid, String shortDescription)
    {
        super(uid, shortDescription);
    }

    public String formatInstantValue(Double value)
    {
        if (value == null || Double.isNaN(value))
        {
            return "-";
        }
        return String.format("%.2f", value);
    }

    public int getWidth()
    {
        return 60;
    }
}
