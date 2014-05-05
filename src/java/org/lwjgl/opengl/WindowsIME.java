package org.lwjgl.opengl;

/**
 * This is the Windows implementation of the IME handling.
 */

import java.util.Queue;

import org.lwjgl.LWJGLException;
import org.lwjgl.input.IME.IMEEvent;
import org.lwjgl.input.IME.State;

final class WindowsIME {

    public static final int WM_IME_STARTCOMPOSITION         = 0x010D;
    public static final int WM_IME_ENDCOMPOSITION           = 0x010E;
    public static final int WM_IME_COMPOSITION              = 0x010F;
    public static final int WM_IME_KEYLAST                  = 0x010F;
    public static final int WM_IME_SETCONTEXT               = 0x0281;
    public static final int WM_IME_NOTIFY                   = 0x0282;
    public static final int WM_IME_CONTROL                  = 0x0283;
    public static final int WM_IME_COMPOSITIONFULL          = 0x0284;
    public static final int WM_IME_SELECT                   = 0x0285;
    public static final int WM_IME_CHAR                     = 0x0286;
    public static final int WM_IME_REQUEST                  = 0x0288;
    public static final int WM_IME_KEYDOWN                  = 0x0290;
    public static final int WM_IME_KEYUP                    = 0x0291;

    public static final int GCS_COMPREADSTR                 = 0x0001;
    public static final int GCS_COMPREADATTR                = 0x0002;
    public static final int GCS_COMPREADCLAUSE              = 0x0004;
    public static final int GCS_COMPSTR                     = 0x0008;
    public static final int GCS_COMPATTR                    = 0x0010;
    public static final int GCS_COMPCLAUSE                  = 0x0020;
    public static final int GCS_CURSORPOS                   = 0x0080;
    public static final int GCS_DELTASTART                  = 0x0100;
    public static final int GCS_RESULTREADSTR               = 0x0200;
    public static final int GCS_RESULTREADCLAUSE            = 0x0400;
    public static final int GCS_RESULTSTR                   = 0x0800;
    public static final int GCS_RESULTCLAUSE                = 0x1000;

    public static final int IMM_ERROR_NODATA                = -1;
    public static final int IMM_ERROR_GENERAL               = -2;

    /**
     * An int return value with optional String result.
     */
    public static class StringReturner
    {
        public int result;
        public String buf;
    }

    WindowsIME (long hwnd)
        throws LWJGLException
    {
        this.hwnd = hwnd;
        himc = CreateContext();
        setEnabled(true);
    }

    public void destroy ()
    {
        setEnabled(false);
        DestroyContext(himc);
    }

    public void setEnabled (boolean enabled)
    {
        if (_enabled != enabled) {
            _enabled = enabled;
            AssociateContext(hwnd, enabled ? himc : 0L);
        }
    }

    public void setComposing (boolean composing)
    {
        _composing = composing;
    }

    public void read (Queue<IMEEvent> queue)
    {
        _queue.copyEvents(queue);
    }

    public boolean handlesMessage (int msg)
    {
        switch (msg) {
        case WM_IME_STARTCOMPOSITION:
        case WM_IME_ENDCOMPOSITION:
        case WM_IME_COMPOSITION:
        case WM_IME_NOTIFY:
        case WM_IME_SETCONTEXT:
        case WM_IME_CONTROL:
        case WM_IME_COMPOSITIONFULL:
        case WM_IME_SELECT :
        case WM_IME_CHAR:
        case WM_IME_REQUEST:
            return _composing;
        default:
            return false;
        }
    }

    public int doHandleMessage (long hwnd, int msg, long wParam, long lParam)
    {
        switch (msg) {
        case WM_IME_STARTCOMPOSITION:
            _event.reset();
            _event.state = State.START;
            _queue.putEvent(_event);
            break;
        case WM_IME_ENDCOMPOSITION:
            _event.state = State.END;
            _queue.putEvent(_event);
            _event.reset();
            break;
        case WM_IME_COMPOSITION:
            if ((lParam & GCS_RESULTSTR) != 0) {
                StringReturner result = ImmGetCompositionString(himc, GCS_RESULTSTR);
                if (result.result >= 0) {
                    _event.str = result.buf;
                    _event.state = State.RESULT;
                    _queue.putEvent(_event);
                }
            }
            if ((lParam & GCS_COMPSTR) != 0) {
                StringReturner result = ImmGetCompositionString(himc, GCS_COMPSTR);
                if (result.result >= 0) {
                    _event.str = result.buf;
                    _event.state = State.COMPOSE;
                    _queue.putEvent(_event);
                }
            }
            if ((lParam & GCS_CURSORPOS) != 0) {
                StringReturner result = ImmGetCompositionString(himc, GCS_CURSORPOS);
                if (result.result >= 0) {
                    _event.cursorPos = result.result;
                    _event.state = State.COMPOSE;
                    _queue.putEvent(_event);
                }
            }
            break;
        }
        return 0;
    }


    private static native long CreateContext ();
    private static native boolean DestroyContext (long himc);
    private static native long AssociateContext (long hwnd, long himc);
    private static native StringReturner ImmGetCompositionString (long himc, long dwIndex);

    private boolean _enabled = false;
    private boolean _composing = false;

    private IMEEvent _event = new IMEEvent();
    private IMEQueue _queue = new IMEQueue();

    private final long hwnd;
    private final long himc;
}
