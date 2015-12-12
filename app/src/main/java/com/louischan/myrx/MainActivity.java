package com.louischan.myrx;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.jakewharton.rxbinding.view.RxView;
import com.trello.rxlifecycle.components.support.RxAppCompatActivity;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;

public class MainActivity extends RxAppCompatActivity {

    public static final long FRAME_RATE = 16;
    public static final long MAX_RECORD_LENGTH = 5000;
    private static final String TAG = MainActivity.class.getSimpleName();

    Button buttonRecordStopPlay;
    Button buttonReset;
    TextView textTimeSpan;

    RecordState recordState = RecordState.READY;
    BehaviorSubject<Long> timeSpans = BehaviorSubject.create(MAX_RECORD_LENGTH);
    BehaviorSubject<RecordState> recordStates = BehaviorSubject.create(recordState);
    Observable<ViewState> viewStates = Observable.combineLatest(recordStates, timeSpans, this::combine1);
    PublishSubject<Long> stopClicks = PublishSubject.create();

    ViewState combine1(RecordState recordState, Long timeSpan) {
        return ViewState.viewStateFor(recordState, timeSpan);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        buttonRecordStopPlay = (Button) findViewById(R.id.button);
        buttonReset = (Button) findViewById(R.id.reset);
        textTimeSpan = (TextView) findViewById(R.id.time_span);
    }

    MediaRecorder createMediaRecorder() {
        MediaRecorder mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setOutputFile(this.getCacheDir().getPath() + "/some.3gp");
        try {
            mediaRecorder.prepare();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return mediaRecorder;
    }

    void disposeMediaRecorder(MediaRecorder mediaRecorder) {
        mediaRecorder.stop();
        mediaRecorder.reset();
        mediaRecorder.release();
    }

    Observable<Long> mediaRecorderLifeSpan(MediaRecorder mediaRecorder) {
        return Observable
                .interval(FRAME_RATE, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .takeUntil(stopClicks)
                .doOnSubscribe(() -> mediaRecorder.start());
    }

    Observable<Long> spawnBoundMediaRecorder() {
        return Observable.using(
                this::createMediaRecorder,
                this::mediaRecorderLifeSpan,
                this::disposeMediaRecorder
        );
    }

    void stopIfStillRecording(Long any) {
        if (recordState == RecordState.RECORDING) {
            handleRecordStopPlayClick(null);
        }
    }

    MediaPlayer createMediaPlayer() {
        MediaPlayer mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(this.getCacheDir().getPath() + "/some.3gp");
            mediaPlayer.prepare();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return mediaPlayer;
    }

    Observable<Long> mediaPlayerLifeSpan(MediaPlayer mediaPlayer) {
        BehaviorSubject<Void> naturalStop = BehaviorSubject.create();
        mediaPlayer.setOnCompletionListener(mp -> {
            naturalStop.onNext(null);
        });
        return Observable
                .interval(FRAME_RATE, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                .takeUntil(Observable.amb(naturalStop, stopClicks))
                .doOnSubscribe(() -> mediaPlayer.start());
    }

    void disposeMediaPlayer(MediaPlayer mediaPlayer) {
        mediaPlayer.stop();
        mediaPlayer.reset();
        mediaPlayer.release();
    }

    Observable<Long> spawnBoundMediaPlayer() {
        return Observable.using(
                this::createMediaPlayer,
                this::mediaPlayerLifeSpan,
                this::disposeMediaPlayer
        );
    }

    void stopIfStillPlaying() {
        if (recordState == RecordState.PLAYING) {
            handleRecordStopPlayClick(null);
        }
    }

    RecordState stepForward(RecordState currentState) {
        switch (currentState) {
            case READY:
                return RecordState.RECORDING;
            case RECORDING:
                return RecordState.STOPPED;
            case STOPPED:
                return RecordState.PLAYING;
            case PLAYING:
                return RecordState.STOPPED;
            default:
                throw new AssertionError();
        }
    }

    Long mapSeqToTimeSpan(Long seq) {
        return seq * FRAME_RATE;
    }

    void handleRecordStopPlayClick(Void click) {
        RecordState newState = stepForward(recordState);
        if (newState == RecordState.STOPPED) {
            stopClicks.onNext(0L);
        }
        if (newState == RecordState.RECORDING) {
            Observable
                    .timer(MAX_RECORD_LENGTH, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                    .subscribe(this::stopIfStillRecording);
            spawnBoundMediaRecorder()
                    .map(this::mapSeqToTimeSpan)
                    .subscribe(timeSpan -> timeSpans.onNext(timeSpan));
        }
        if (newState == RecordState.PLAYING) {
            spawnBoundMediaPlayer()
                    .map(this::mapSeqToTimeSpan)
                    .doOnUnsubscribe(this::stopIfStillPlaying)
                    .subscribe(timeSpan -> timeSpans.onNext(timeSpan));
        }
        recordStates.onNext(newState);
        recordState = newState;
    }

    void handleResetClick(Void click) {
        RecordState newState = RecordState.READY;
        recordStates.onNext(newState);
        recordState = newState;
    }

    @Override
    protected void onResume() {
        super.onResume();
        RxView
                .clicks(buttonRecordStopPlay)
                .compose(bindToLifecycle())
                .doOnNext(this::handleRecordStopPlayClick)
                .subscribe();
        RxView
                .clicks(buttonReset)
                .compose(bindToLifecycle())
                .doOnNext(this::handleResetClick)
                .subscribe();
        viewStates
                .asObservable()
                .compose(bindToLifecycle())
                .subscribe(viewState -> {
                    buttonRecordStopPlay.setText(viewState.recordStopPlayText);
                    buttonReset.setEnabled(viewState.resetEnabled);
                    textTimeSpan.setText(Long.toString(viewState.timeSpan));
                });
    }

}
