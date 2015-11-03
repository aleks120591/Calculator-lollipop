package com.android2.calculator3;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.TextView;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.touchpad.Gesture;
import com.google.android.glass.touchpad.GestureDetector;
import com.xlythe.math.EquationFormatter;
import com.xlythe.math.Solver;
import com.xlythe.math.Voice;

public class GlassResultActivity extends Activity {
    public static final String EXTRA_QUERY = "query";
    public static final String EXTRA_RESULT = "result";

    private String mQuery;
    private String mResult;
    private boolean mIsTextToSpeechInit = false;
    private TextToSpeech mTextToSpeech;
    private GestureDetector mGestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.glass_result);

        mQuery = getIntent().getStringExtra(EXTRA_QUERY);
        mResult = getIntent().getStringExtra(EXTRA_RESULT);

        TextView queryView = (TextView) findViewById(R.id.query);
        TextView resultView = (TextView) findViewById(R.id.result);

        queryView.setText(mQuery);
        resultView.setText(mResult);

        // Add comas to format the text
        final Solver solver = new Solver();
        EquationFormatter formatter = new EquationFormatter();
        queryView.setText(formatter.addComas(solver, mQuery));
        resultView.setText(formatter.addComas(solver, mResult));

        mGestureDetector = new GestureDetector(this);
        mGestureDetector.setBaseListener(new GestureDetector.BaseListener() {
            @Override
            public boolean onGesture(Gesture gesture) {
                if (gesture == Gesture.TAP) {
                    ((AudioManager) getSystemService(AUDIO_SERVICE)).playSoundEffect(Sounds.TAP);
                    openOptionsMenu();
                    return true;
                }

                return false;
            }
        });
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (mGestureDetector != null) {
            return mGestureDetector.onMotionEvent(event);
        }
        return false;
    }

    private void askNewQuestion() {
        startActivity(new Intent(getBaseContext(), GlassHomeActivity.class));
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_glass, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.repeat:
                askNewQuestion();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mIsTextToSpeechInit = false;
        mTextToSpeech = new TextToSpeech(getBaseContext(), new OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status == TextToSpeech.SUCCESS) {
                    mIsTextToSpeechInit = true;
                    speakResult();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mTextToSpeech != null) mTextToSpeech.shutdown();
    }

    private void speakResult() {
        if(mTextToSpeech != null && mIsTextToSpeechInit) {
            String question = Voice.createSpokenText(mQuery);
            String result = Voice.createSpokenText(mResult);
            mTextToSpeech.speak(getString(R.string.speech_helper_equals, question, result), TextToSpeech.QUEUE_ADD, null);
        }
    }
}
