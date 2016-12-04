package mpo.dayon.assisted.control;

import mpo.dayon.common.network.NetworkEngine;
import mpo.dayon.common.network.message.NetworkKeyControlMessage;
import mpo.dayon.common.network.message.NetworkMouseControlMessage;

import java.awt.*;
import java.awt.event.InputEvent;

public class RobotNetworkControlMessageHandler implements NetworkControlMessageHandler
{
    private final Robot robot;

    public RobotNetworkControlMessageHandler()
    {
        try
        {
            robot = new Robot();
        }
        catch (AWTException ex)
        {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Should not block as called from the network incoming message thread (!)
     */
    public void handleMessage(NetworkEngine engine, NetworkMouseControlMessage message)
    {
        if (message.isPressed())
        {
            if (message.isButton1())
            {
                robot.mousePress(InputEvent.BUTTON1_MASK);
            }
            else if (message.isButton2())
            {
                robot.mousePress(InputEvent.BUTTON2_MASK);
            }
            else if (message.isButton3())
            {
                robot.mousePress(InputEvent.BUTTON3_MASK);
            }
        }
        else if (message.isReleased())
        {
            if (message.isButton1())
            {
                robot.mouseRelease(InputEvent.BUTTON1_MASK);
            }
            else if (message.isButton2())
            {
                robot.mouseRelease(InputEvent.BUTTON2_MASK);
            }
            else if (message.isButton3())
            {
                robot.mouseRelease(InputEvent.BUTTON3_MASK);
            }
        }
        else if (message.isWheel())
        {
            robot.mouseWheel(message.getRotations());
        }

        robot.mouseMove(message.getX(), message.getY());
    }

    /**
     * Should not block as called from the network incoming message thread (!)
     */
    public void handleMessage(NetworkEngine engine, NetworkKeyControlMessage message)
    {
        if (message.isPressed())
        {
            robot.keyPress(message.getKeyCode());
        }
        else if (message.isReleased())
        {
            robot.keyRelease(message.getKeyCode());
        }
    }
}
