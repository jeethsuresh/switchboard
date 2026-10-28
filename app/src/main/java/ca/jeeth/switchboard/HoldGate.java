package ca.jeeth.switchboard;

/** One physical press, one launch; Android key repeats never re-arm the timer. */
final class HoldGate {
    private boolean pressed, fired;
    private long started;
    void down(long now) { if (!pressed) { pressed = true; fired = false; started = now; } }
    boolean tick(long now, long threshold) {
        if (!pressed || fired || now - started < threshold) return false;
        fired = true;
        return true;
    }
    boolean up() { boolean shortPress = pressed && !fired; pressed = false; return shortPress; }
    void cancel() { pressed = false; fired = false; }
}
