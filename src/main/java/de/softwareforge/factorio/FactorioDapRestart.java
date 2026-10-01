package de.softwareforge.factorio;

/** One restart request per session. Stop always wins until the replacement is launched. */
final class FactorioDapRestart {
    private enum State { RUNNING, REQUESTED, CANCELLED, LAUNCHED }
    private State state = State.RUNNING;
    private Object data;

    static boolean isRequested(Object data) {
        // DAP permits true or arbitrary JSON data, including an empty object.
        return data != null && !Boolean.FALSE.equals(data);
    }

    synchronized boolean request(Object data) {
        if (state != State.RUNNING || !isRequested(data)) return false;
        this.data = data;
        state = State.REQUESTED;
        return true;
    }

    synchronized void cancel() {
        state = State.CANCELLED;
        data = null;
    }

    synchronized Object take() {
        if (state != State.REQUESTED) return null;
        state = State.LAUNCHED;
        Object result = data;
        data = null;
        return result;
    }
}
