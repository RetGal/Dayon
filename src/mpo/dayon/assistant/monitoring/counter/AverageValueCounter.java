package mpo.dayon.assistant.monitoring.counter;

public abstract class AverageValueCounter extends Counter<Double>
{
    private double instantWeight = 0;

    private double instantValue = 0;

    AverageValueCounter(String uid, String shortDescription)
    {
        super(uid, shortDescription);
    }

    public void add(double value)
    {
        add(1.0, value);
    }

    public void add(double weight, double value)
    {
        synchronized (this)
        {
            final double xvalue = weight * value;

            instantWeight += weight;
            instantValue += xvalue;
        }
    }

    public void computeAndResetInstantValue()
    {
        double value = Double.NaN;

        synchronized (this)
        {
            if (instantStart != -1)
            {
                value = (instantWeight == 0.0) ? 0.0 : instantValue / instantWeight;

                instantStart = System.currentTimeMillis();

                instantWeight = 0;
                instantValue = 0;
            }
        }

        fireOnInstantValueUpdated(value);
    }

}