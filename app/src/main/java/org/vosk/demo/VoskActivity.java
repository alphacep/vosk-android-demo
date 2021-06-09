// Copyright 2019 Alpha Cephei Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.vosk.demo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.api.VoskHelperAndroid;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.function.BiConsumer;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;

public class VoskActivity extends Activity implements
        RecognitionListener {
    private final static String TAG = VoskActivity.class.getSimpleName();

    static private final int STATE_START = 0;
    static private final int STATE_READY = 1;
    static private final int STATE_DONE = 2;
    static private final int STATE_FILE = 3;
    static private final int STATE_MIC = 4;

    static private final int ON_LANGUAGE_CHANGED = 5;

    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;
    private static final int PERMISSIONS_REQUEST_ALL_FILES_ACCESS = 2;
    private static final boolean DEBUG = false;
    private static final String CONFIRM_DOWNLOAD = "Confirm Download";

    private Model model;
    private SpeechService speechService;
    private SpeechStreamService speechStreamService;
    private TextView resultView;

    private BiConsumer<String, Integer> progress = null;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);

        // Setup layout
        resultView = findViewById(R.id.result_text);
        setUiState(STATE_START);

        findViewById(R.id.recognize_file).setOnClickListener(view -> recognizeFile());
        findViewById(R.id.recognize_mic).setOnClickListener(view -> recognizeMicrophone());
        ((ToggleButton) findViewById(R.id.pause)).setOnCheckedChangeListener((view, isChecked) -> pause(isChecked));

        LibVosk.setLogLevel(LogLevel.INFO);

        // Check if user has given permission to record audio, request all files access permission after permission is granted
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            requestAllFilesAccessPermission();
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                showSettings();
                return true;

            default:
                // The user's action was not recognized.
                // Invoke the superclass to handle it.
                return super.onOptionsItemSelected(item);
        }
    }

    private void requestAllFilesAccessPermission() {
        // Check if user has given all files access permission to record audio, init model after permission is granted
        if (Build.VERSION.SDK_INT >= 30) {
            Log.i(VoskActivity.class.getName(), "API level >= 30");
            if (!Environment.isExternalStorageManager()) {
                // Request permission
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivityForResult(intent, PERMISSIONS_REQUEST_ALL_FILES_ACCESS);
                } catch (android.content.ActivityNotFoundException e) {
                    setErrorState("Failed to request all files access permission");
                }
            } else {
                customInitModel();
            }
        } else {
            Log.i(VoskActivity.class.getName(), "API level < 30");
            // Request permission
            int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_ALL_FILES_ACCESS);
            } else {
                customInitModel();
            }
        }
    }

    /** (Down)Load current Model */
    private void customInitModel() {
        Log.i(VoskActivity.class.getName(), "Custom init model");

        String currentLanguageModelId = getCurrentLanguageModelId();
        if (currentLanguageModelId == null) {
            // first app start: Ask user which language to use
            showSettings();
        } else {
            VoskHelperAndroid.loadOrDownloadModelInBackground(
                    this, currentLanguageModelId, CONFIRM_DOWNLOAD,
                    (m,ex) -> onLoadModelResult(m,ex), progress);
        }
    }

    /** called after (Down)Load Model finished or failed */
    private void onLoadModelResult(Model model, Throwable exception) {
        if (exception != null) {
            if (exception.getCause() == null) {
                if (exception.getMessage().contains(CONFIRM_DOWNLOAD)) {
                    onConfirmDownload();
                } else {
                    // unknown language ask for language
                    showSettings();
                }
            } else {
                //!!! download/Load failed
                setErrorState(exception.getMessage());
            }
        } else {
            //!!! success
            setModel(model);
            setUiState(STATE_READY);
        }
    }

    /** Model has not been downloaded. Ask for Confirmation */
    private void onConfirmDownload() {
        String currentLanguageModelId = getCurrentLanguageModelId();

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(getString(R.string.title_download_model, currentLanguageModelId));
        builder.setMessage(getString(R.string.description_download_model, currentLanguageModelId))
                .setCancelable(false)
                .setPositiveButton(R.string.download,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(
                                    final DialogInterface dialog,
                                    final int id) {
                                VoskHelperAndroid.loadOrDownloadModelInBackground(
                                        VoskActivity.this, currentLanguageModelId, null,
                                        (m,ex) -> onLoadModelResult(m,ex), progress);
                            }
                        }
                )
                .setNeutralButton(R.string.title_activity_settings, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showSettings();
                    }
                })
                .setNegativeButton(android.R.string.cancel,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(
                                    final DialogInterface dialog,
                                    final int id) {
                                dialog.cancel();
                                finish();
                            }
                        }
                );

        builder.create().show();

    }

    public void setModel(Model model) {
        this.model = model;
    }

    private void showSettings() {
        startActivityForResult(new Intent(this, SettingsActivity.class), ON_LANGUAGE_CHANGED);
    }

    private String getCurrentLanguageModelId() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String lang = sharedPreferences.getString("lang", null);
        setTitle(getText(R.string.app_name) + " " + lang);
        return lang;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSIONS_REQUEST_ALL_FILES_ACCESS) {
            if (Build.VERSION.SDK_INT >= 30) {
                if (Environment.isExternalStorageManager()) {
                    customInitModel();
                } else {
                    setErrorState("All files access permission needed");
                }
            }
        } else if (requestCode == ON_LANGUAGE_CHANGED) {
            customInitModel();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestAllFilesAccessPermission();
            } else {
                setErrorState("Record audio permission needed");
            }
        } else if (requestCode == PERMISSIONS_REQUEST_ALL_FILES_ACCESS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                customInitModel();
            } else {
                setErrorState("All files access permission needed");
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }

        if (speechStreamService != null) {
            speechStreamService.stop();
        }
    }

    @Override
    public void onResult(String hypothesis) {
        // json: {"result":[], "text":"hello world"}

        if (DEBUG) {
            debug("onResult:" + hypothesis);
        } else {
            String textFromJson = getTextFromJson(hypothesis);
            if (!textFromJson.trim().isEmpty()) {
                appendMessage(textFromJson);
            }
        }
    }

    private void appendMessage(String text) {
        resultView.append(text);
        resultView.append("\n");
    }

    private void debug(String text) {
        appendMessage(text);
    }

    private String getTextFromJson(String hypothesis) {
        Log.i(TAG, hypothesis);
        Gson gson = new Gson();
        Map<String, String> map = gson.fromJson(hypothesis, Map.class);
        return map.get("text");
    }

    @Override
    public void onFinalResult(String hypothesis) {
        debug("onFinalResult:" + hypothesis);
        setUiState(STATE_DONE);
        if (speechStreamService != null) {
            speechStreamService = null;
        }
    }

    @Override
    public void onPartialResult(String hypothesis) {
        // json: {"partial":"hello world"}
        if (DEBUG) {
            debug("onPartialResult: " + hypothesis);
        }
    }

    @Override
    public void onError(Exception e) {
        setErrorState(e.getMessage());
    }

    @Override
    public void onTimeout() {
        setUiState(STATE_DONE);
    }

    private void setUiState(int state) {
        switch (state) {
            case STATE_START:
                resultView.setText(R.string.preparing);
                resultView.setMovementMethod(new ScrollingMovementMethod());
                findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_READY:
                resultView.setText(R.string.ready);
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_DONE:
                ((Button) findViewById(R.id.recognize_file)).setText(R.string.recognize_file);
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_FILE:
                ((Button) findViewById(R.id.recognize_file)).setText(R.string.stop_file);
                resultView.setText(getString(R.string.starting));
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_MIC:
                ((Button) findViewById(R.id.recognize_mic)).setText(R.string.stop_microphone);
                resultView.setText(getString(R.string.say_something));
                findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((true));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + state);
        }
    }

    private void setErrorState(String message) {
        resultView.setText(message);
        ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
        findViewById(R.id.recognize_file).setEnabled(false);
        findViewById(R.id.recognize_mic).setEnabled(false);
    }

    private void recognizeFile() {
        if (speechStreamService != null) {
            setUiState(STATE_DONE);
            speechStreamService.stop();
            speechStreamService = null;
        } else {
            setUiState(STATE_FILE);
            try {
                Recognizer rec = new Recognizer(model, 16000.f);

                InputStream ais = getAssets().open(
                        "10001-90210-01803.wav");
                if (ais.skip(44) != 44) throw new IOException("File too short");

                speechStreamService = new SpeechStreamService(rec, ais, 16000);
                speechStreamService.start(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

    private void recognizeMicrophone() {
        if (speechService != null) {
            setUiState(STATE_DONE);
            speechService.stop();
            speechService = null;
        } else {
            setUiState(STATE_MIC);
            try {
                Recognizer rec = new Recognizer(model, 16000.0f);
                speechService = new SpeechService(rec, 16000.0f);
                speechService.startListening(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

    private void pause(boolean checked) {
        if (speechService != null) {
            speechService.setPause(checked);
        }
    }
}
