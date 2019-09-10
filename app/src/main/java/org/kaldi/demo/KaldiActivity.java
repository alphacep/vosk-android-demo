/* ====================================================================
 * Copyright (c) 2019 Alpha Cephei Inc.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY ALPHA CEPHEI INC. ``AS IS'' AND
 * ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL CARNEGIE MELLON UNIVERSITY
 * NOR ITS EMPLOYEES BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * ====================================================================
 */

package org.kaldi.demo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.kaldi.Assets;
import org.kaldi.KaldiRecognizer;
import org.kaldi.Model;
import org.kaldi.SpeechRecognizer;
import org.kaldi.RecognitionListener;

public class KaldiActivity extends Activity implements
        RecognitionListener {

    static {
        System.loadLibrary("kaldi_jni");
    }

    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private SpeechRecognizer recognizer;
    TextView resultView;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);
        resultView = ((TextView) findViewById(R.id.result_text));
        resultView.setText("Preparing the recognizer");

        // Check if user has given permission to record audio
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            return;
        }
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new SetupTask(this, resultView).execute();
    }

    private static class SetupTask extends AsyncTask<Void, Void, Exception> {
        WeakReference<KaldiActivity> activityReference;
        WeakReference<TextView> resultView;
        SetupTask(KaldiActivity activity, TextView resultView) {
            this.activityReference = new WeakReference<>(activity);
            this.resultView = new WeakReference<TextView>(resultView);
        }
        @Override
        protected Exception doInBackground(Void... params) {
            try {
                Assets assets = new Assets(activityReference.get());
                File assetDir = assets.syncAssets();
                Log.d("!!!!", assetDir.toString());
                activityReference.get().setupRecognizer(assetDir);
            } catch (IOException e) {
                return e;
            }
            return null;
        }
        @Override
        protected void onPostExecute(Exception result) {
            if (result != null) {
                resultView.get().setText("Failed to init recognizer " + result);
            } else {
                resultView.get().setText("Start talking!\n");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull  int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                new SetupTask(this, resultView).execute();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }
    }

    @Override
    public void onResult(String hypothesis) {
        resultView.append(hypothesis + "\n");
    }

    @Override
    public void onPartialResult(String hypothesis) {
        resultView.append(hypothesis + "\n");
    }


    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        Model model = new Model(assetsDir.toString() + "/model-libri");

        KaldiRecognizer rec = new KaldiRecognizer(model);

        InputStream ais = getAssets().open("10001-90210-01803.wav");
        ais.skip(44);
        byte[] b = new byte[4096];
        int nbytes;
        long startTime = System.currentTimeMillis();
        while ((nbytes = ais.read(b)) >= 0) {
            ByteBuffer bb = ByteBuffer.wrap(b, 0, nbytes);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            short[] s = new short[nbytes/2];
            bb.asShortBuffer().get(s);
            rec.AcceptWaveform(s, nbytes);
        }
        Log.d("!!!!", "Result " + rec.FinalResult() + " elapsed "  + (System.currentTimeMillis() - startTime));

        recognizer = new SpeechRecognizer(model);
        recognizer.addListener(this);
        recognizer.startListening();
    }

    @Override
    public void onError(Exception error) {
        Log.d("!!!!", "On error");
    }

    @Override
    public void onTimeout() {
        Log.d("!!!!", "On timeout");
    }
}
