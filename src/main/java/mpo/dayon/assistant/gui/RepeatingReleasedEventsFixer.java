package mpo.dayon.assistant.gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * From the original @author Endre Stølsvik:
 *
 * This {@link AWTEventListener} tries to work around a 12 yo
 * bug in the Linux KeyEvent handling for keyboard repeat. Linux apparently implements repeating keypresses by
 * repeating both the {@link KeyEvent#KEY_PRESSED} and {@link KeyEvent#KEY_RELEASED}, while on Windows, one only
 * gets repeating PRESSES, and then a final RELEASE when the key is released. The Windows way is obviously much more
 * useful, as one then can easily distinguish between a user holding a key pressed, and a user hammering away on the
 * key.
 * <p>
 * This class is an {@link AWTEventListener} that should be installed as the application's first ever
 * {@link AWTEventListener} using the following code, but it is simpler to invoke {@link #install() install(new
 * instance)}:
 * <p>
 * <p>
 * <p>
 * Toolkit.getDefaultToolkit().addAWTEventListener(new {@link RepeatingReleasedEventsFixer}, AWTEvent.KEY_EVENT_MASK);
 * <p>
 * <p>
 * <p>
 * <p>
 * Remember to remove it and any other installed {@link AWTEventListener} if your application have some "reboot"
 * functionality that can potentially install it again - or else you'll end up with multiple instances, which isn't too
 * hot.
 * <p>
 * Notice: Read up on the {@link Reposted} interface if you have other AWTEventListeners that resends KeyEvents
 * (as this one does) - or else we'll get the event back.
 * <p>
 * <p>
 * <p>
 * Mode of operation
 * <p>
 * The class makes use of the fact that the subsequent PRESSED event comes right after the RELEASED event - one thus
 * have a sequence like this:
 * <p>
 * <p>
 * <p>
 * PRESSED
 * -wait between key repeats-
 * RELEASED
 * PRESSED
 * -wait between key repeats-
 * RELEASED
 * PRESSED
 * etc.
 * <p>
 * <p>
 * <p>
 * <p>
 * A timer is started when receiving a RELEASED event, and if a PRESSED comes soon afterwards, the RELEASED is dropped
 * (consumed) - while if the timer times out, the event is reposted and thus becomes the final, wanted RELEASED that
 * denotes that the key actually was released.
 * <p>
 * Inspired by http://www.arco.in-berlin.de/keyevent.html
 *
 * @author Endre Stølsvik
 *
 * Refactored by Reto Galante into a proper Singleton, so you may ignore the remove method, if you are instantiating it
 * as such by calling RepeatingReleasedEventsFixer.install();
 *
 */

public final class RepeatingReleasedEventsFixer implements AWTEventListener {

    private final Map<Integer, ReleasedAction> map = new HashMap<>();

    private static RepeatingReleasedEventsFixer instance;

    private RepeatingReleasedEventsFixer() {
        // singleton
    }

    public static RepeatingReleasedEventsFixer install() {
        synchronized (RepeatingReleasedEventsFixer.class) {
            if (instance == null) {
                final RepeatingReleasedEventsFixer fixer = new RepeatingReleasedEventsFixer();
                Toolkit.getDefaultToolkit().addAWTEventListener(fixer, AWTEvent.KEY_EVENT_MASK);
                instance = fixer;
            }
        }
        return instance;
    }

    public static void remove() {
        if (instance != null) {
            Toolkit.getDefaultToolkit().removeAWTEventListener(instance);
        }
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        if (!(event instanceof KeyEvent)) {
            throw new AssertionError("Shall only listen to KeyEvents, so no other events shall come here");
        }
        assert assertEDT(); // REMEMBER THAT THIS IS SINGLE THREADED, so no need for synch.

        // ?: Is this one of our synthetic RELEASED events?
        if (event instanceof Reposted) {
            // -> Yes, so we shalln't process it again.
            return;
        }

        // ?: KEY_TYPED event? (We're only interested in KEY_PRESSED and KEY_RELEASED).
        if (event.getID() == KeyEvent.KEY_TYPED) {
            // -> Yes, TYPED, don't process.
            return;
        }

        final KeyEvent keyEvent = (KeyEvent) event;

        // ?: Is this already consumed?
        // (Note how events are passed on to all AWTEventListeners even though a previous one consumed it)
        if (keyEvent.isConsumed()) {
            return;
        }

        // ?: Is this RELEASED? (the problem we're trying to fix!)
        if (keyEvent.getID() == KeyEvent.KEY_RELEASED) {
            // -> Yes, so stick in wait
            /*
             * Really just wait until "immediately", as the point is that the subsequent PRESSED shall already have been
             * posted on the event queue, and shall thus be the direct next event no matter which events are posted
             * afterwards. The code with the ReleasedAction handles if the Timer thread actually fires the action due to
             * lags, by cancelling the action itself upon the PRESSED.
             */
            final Timer timer = new Timer(2, null);
            ReleasedAction action = new ReleasedAction(keyEvent, timer);
            timer.addActionListener(action);
            timer.start();

            map.put(keyEvent.getKeyCode(), action);

            // Consume the original
            keyEvent.consume();
        } else if (keyEvent.getID() == KeyEvent.KEY_PRESSED) {
            // Remember that this is single threaded (EDT), so we can't have races.
            ReleasedAction action = map.remove(keyEvent.getKeyCode());
            // ?: Do we have a corresponding RELEASED waiting?
            if (action != null) {
                // -> Yes, so dump it
                action.cancel();
            }
            // System.out.println("PRESSED: [" + keyEvent + "]");
        } else {
            throw new AssertionError("All IDs should be covered.");
        }
    }

    /**
     * The ActionListener that posts the RELEASED {@link RepostedKeyEvent} if the {@link Timer} times out (and hence the
     * repeat-action was over).
     */
    private class ReleasedAction implements ActionListener {

        private final KeyEvent originalKeyEvent;
        private Timer timer;

        ReleasedAction(KeyEvent originalReleased, Timer timer) {
            this.timer = timer;
            originalKeyEvent = originalReleased;
        }

        void cancel() {
            assert assertEDT();
            timer.stop();
            timer = null;
            map.remove(originalKeyEvent.getKeyCode());
        }

        @Override
        public void actionPerformed(@SuppressWarnings("unused") ActionEvent e) {
            assert assertEDT();
            // ?: Are we already cancelled?
            // (Judging by Timer and TimerQueue code, we can theoretically be raced to be posted onto EDT by TimerQueue,
            // due to some lag, unfair scheduling)
            if (timer == null) {
                // -> Yes, so don't post the new RELEASED event.
                return;
            }
            // Stop Timer and clean.
            cancel();
            // Creating new KeyEvent (we've consumed the original).
            KeyEvent newEvent = new RepostedKeyEvent((Component) originalKeyEvent.getSource(),
                    originalKeyEvent.getID(), originalKeyEvent.getWhen(), originalKeyEvent.getModifiersEx(),
                    originalKeyEvent.getKeyCode(), originalKeyEvent.getKeyChar(), originalKeyEvent.getKeyLocation());
            // Posting to EventQueue.
            Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(newEvent);
            // System.out.println("Posted synthetic RELEASED [" + newEvent + "].");
        }
    }

    /**
     * Marker interface that denotes that the {@link KeyEvent} in question is reposted from some
     * {@link AWTEventListener}, including this. It denotes that the event shall not be "hack processed" by this class
     * again. (The problem is that it is not possible to state "inject this event from this point in the pipeline" - one
     * have to inject it to the event queue directly, thus it will come through this {@link AWTEventListener} too.
     */
    public interface Reposted {
        // marker
    }

    /**
     * Dead simple extension of {@link KeyEvent} that implements {@link Reposted}.
     */
    public static class RepostedKeyEvent extends KeyEvent implements Reposted {
        public RepostedKeyEvent(@SuppressWarnings("hiding") Component source, @SuppressWarnings("hiding") int id,
                                long when, int modifiers, int keyCode, char keyChar, int keyLocation) {
            super(source, id, when, modifiers, keyCode, keyChar, keyLocation);
        }
    }

    private static boolean assertEDT() {
        if (!EventQueue.isDispatchThread()) {
            throw new AssertionError("Not EDT, but [" + Thread.currentThread() + "].");
        }
        return true;
    }
}
