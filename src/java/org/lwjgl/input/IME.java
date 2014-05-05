package org.lwjgl.input;

import java.util.ArrayDeque;
import java.util.Queue;

import org.lwjgl.LWJGLException;
import org.lwjgl.Sys;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.InputImplementation;

/**
 * The IME interface.  Can be used to control native IME functionality (on Windows at least).
 */
public class IME
{
    /** IME event states. */
    public enum State { NONE, START, END, COMPOSE, RESULT };

    /** Has the IME been created? */
    private static boolean created;

    /** One time initialization. */
    private static boolean initialized;

    /** The native implementation. */
    private static InputImplementation implementation;

    /** Current event. */
    private static IMEEvent _currentEvent = new IMEEvent();

    /** The event queue. */
    private static Queue<IMEEvent> _queue = new ArrayDeque<IMEEvent>();

    /**
     * IME cannot be constructed.
     */
    private IME ()
    {
    }

    /**
     * Static initialization
     */
    private static void initialize() {
        if (initialized)
            return;
        Sys.initialize();
        initialized = true;
    }

    /**
     * "Create" the IME with the given implementation. This is used
     * reflectively from AWTInputAdapter.
     *
     * @throws LWJGLException if the ime could not be created for any reason
     */
    private static void create(InputImplementation impl) throws LWJGLException {
        if (created)
            return;
        if (!initialized)
            initialize();
        implementation = impl;
        implementation.createIME();
        created = true;
        reset();
    }

    /**
     * "Create" the IME. The display must first have been created. The
     * reason for this is so we can attach the IME context to the display.
     *
     * @throws LWJGLException if the ime could not be created for any reason
     */
    public static void create() throws LWJGLException {
        synchronized (OpenGLPackageAccess.global_lock) {
            if (!Display.isCreated()) throw new IllegalStateException("Display must be created.");

            create(OpenGLPackageAccess.createImplementation());
        }
    }

    private static void reset ()
    {
        _currentEvent.reset();
    }

    /**
     * @return true if the ime has been created
     */
    public static boolean isCreated() {
        synchronized (OpenGLPackageAccess.global_lock) {
            return created;
        }
    }

    /**
     * "Destroy" the ime
     */
    public static void destroy() {
        synchronized (OpenGLPackageAccess.global_lock) {
            if (!created)
                return;
            created = false;
            implementation.destroyIME();
            reset();
        }
    }

    /**
     * Polls the IME to update the event queue.  Access polled values with calls to
     * <code>next</code> for each event you want to read.
     */
    public static void poll () {
        synchronized (OpenGLPackageAccess.global_lock) {
            if (!created)
                throw new IllegalStateException("IME must be created before you can poll.");
            implementation.readIME(_queue);
        }
    }

    /**
     * Set the enabled state of the ime.
     */
    public static void setEnabled (boolean enabled)
    {
        synchronized (OpenGLPackageAccess.global_lock) {
            if (!created) {
                throw new IllegalStateException(
                        "IME must be created before you can change enabled state");
            }
            implementation.setIMEEnabled(enabled);
        }
    }

    /**
     * Set the composing state of the ime.  When set to true, all IME messages are intercepted
     * and will be handled by the IME rather than the default system handling.
     */
    public static void setComposing (boolean composing)
    {
        synchronized (OpenGLPackageAccess.global_lock) {
            if (!created) {
                throw new IllegalStateException(
                        "IME must be created before you can change composing state");
            }
            implementation.setIMEComposing(composing);
        }
    }

    /**
     * Gets the next IME event.
     */
    public static boolean next ()
    {
        synchronized (OpenGLPackageAccess.global_lock) {
            if (!created) {
                throw new IllegalStateException("IME must be created before you can read events");
            }
            if (_queue.isEmpty()) {
                return false;
            }
            _queue.poll().copy(_currentEvent);
            return true;
        }
    }

    /**
     * Get the current result string.
     */
    public static String getString ()
    {
        synchronized (OpenGLPackageAccess.global_lock) {
            return _currentEvent.str;
        }
    }

    /**
     * Get the current cursor position.
     */
    public static int getCursorPosition ()
    {
        synchronized (OpenGLPackageAccess.global_lock) {
            return _currentEvent.cursorPos;
        }
    }

    /**
     * Get the current event state.
     */
    public static State getState ()
    {
        synchronized (OpenGLPackageAccess.global_lock) {
            return _currentEvent.state;
        }
    }

    public static final class IMEEvent {
        public String str;
        public int cursorPos;
        public State state = State.START;

        public void reset () {
            str = null;
            cursorPos = 0;
            state = State.NONE;
        }

        public void copy (IMEEvent other) {
            other.str = str;
            other.cursorPos = cursorPos;
            other.state = state;
        }
    }
}
