package com.louischan.myrx;

public class ViewState {

    public final String recordStopPlayText;
    public final boolean resetEnabled;
    public final long timeSpan;

    public ViewState(String recordStopPlayText, boolean resetEnabled, long timeSpan) {
        this.recordStopPlayText = recordStopPlayText;
        this.resetEnabled = resetEnabled;
        this.timeSpan = timeSpan;
    }

    public static ViewState viewStateFor(RecordState recordState, long timeSpan) {
        switch (recordState) {
            case READY:
                return new ViewState("ready", false, timeSpan);
            case RECORDING:
                return new ViewState("recording", false, timeSpan);
            case PLAYING:
                return new ViewState("playing", false, timeSpan);
            case STOPPED:
                return new ViewState("stopped", true, timeSpan);
            default:
                throw new AssertionError();
        }
    }
}
