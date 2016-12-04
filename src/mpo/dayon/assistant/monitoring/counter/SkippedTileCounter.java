package mpo.dayon.assistant.monitoring.counter;

public class SkippedTileCounter extends AbsoluteValueCounter
{
    public SkippedTileCounter(String uid, String shortDescription)
    {
        super(uid, shortDescription);
    }

    public String formatInstantValue(Long value)
    {
        if (value == null)
        {
            return "-";
        }

        return String.format("%d", value);
    }

    public int getWidth()
    {
        return 40;
    }
}