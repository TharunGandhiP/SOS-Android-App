package com.neolit.womensos;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

public class MainActivity extends AppCompatActivity {

    private SpeechRecognizer speechRecognizer;
    private Intent speechRecognizerIntent;
    private static final String TRIGGER_KEYWORD = "help";
    private static final int REQUEST_PERMISSIONS = 1000;
    private static final String TAG = "SOS";
    private static final int LISTEN_DURATION_MS = 20000;

    Button send;
    FloatingActionButton aboutButton;
    FusedLocationProviderClient fusedLocationClient;
    String currentLocationString;
    dbHelper databaseHelper;
    String recognizedText;
    Handler handler = new Handler();
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeSpeechRecognizer();
        startListeningForKeyword();

        send = findViewById(R.id.button);
        aboutButton = findViewById(R.id.about);
        databaseHelper = new dbHelper(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        getPermissions();
        getLocation();

        send.setOnClickListener(view -> {
            Log.i(TAG, "Location: " + currentLocationString);
            sendSOS();
        });

        Button addContactButton = findViewById(R.id.add);
        addContactButton.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, add.class)));

        aboutButton.setOnClickListener(v -> new MaterialAlertDialogBuilder(MainActivity.this)
                .setMessage("An Android app by Team Rithun and Friends to send SOS alerts to emergency contacts.")
                .setTitle("About the App")
                .setNegativeButton("Done", (dialog, which) -> dialog.cancel())
                .show());
    }

    private void sendSOS() {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            if (currentLocationString == null) {
                Toast.makeText(getApplicationContext(), "Please enable location first!", Toast.LENGTH_LONG).show();
                return;
            }

            Cursor cursor = databaseHelper.getAllContacts();

            if (cursor != null) {
                // Start speech recognition once for NER processing
                startListeningForNER(() -> {
                    if (recognizedText != null) {
                        String message = "SOS: I need help. " + recognizedText + " Location: " + currentLocationString;

                        // Iterate over each contact and send the message
                        int numberIndex = cursor.getColumnIndex("number");
                        int emailIndex = cursor.getColumnIndex("email");

                        while (cursor.moveToNext()) {
                            String number = cursor.getString(numberIndex);
                            String email = cursor.getString(emailIndex);

                            // Send SMS to the contact
                            smsManager.sendTextMessage(number, null, message, null, null);

                            // Send email to the contact
                            sendEmailRequest(email, currentLocationString, recognizedText);
                        }
                        cursor.close();
                        Toast.makeText(getApplicationContext(), "SOS sent to all contacts", Toast.LENGTH_LONG).show();
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "SOS message failed", e);
            Toast.makeText(getApplicationContext(), "Failed to send SOS", Toast.LENGTH_LONG).show();
        }
    }

    // Adjusted startListeningForNER to accept a callback after recognition
    private void startListeningForNER(Runnable onRecognitionComplete) {
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) {}

            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    recognizedText = matches.get(0);
                    Log.i(TAG, "Recognized text: " + recognizedText);
                    if (onRecognitionComplete != null) {
                        onRecognitionComplete.run(); // Execute the callback after recognition
                    }
                }
            }

            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        // Start listening for the user's speech to recognize text
        speechRecognizer.startListening(speechRecognizerIntent);
        handler.postDelayed(() -> speechRecognizer.stopListening(), LISTEN_DURATION_MS);
    }

    private void getPermissions() {
        String[] permissions = {Manifest.permission.SEND_SMS, Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION};
        ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
    }

    private void getLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS);
            return;
        }
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                currentLocationString = "https://maps.google.com/?q=" + location.getLatitude() + "," + location.getLongitude();
            }
        });
    }

    private void initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, this.getPackageName());

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) { startListeningForKeyword(); }
            @Override public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null) {
                    for (String result : matches) {
                        if (result.equalsIgnoreCase(TRIGGER_KEYWORD)) {
                            sendSOS();
                            break;
                        }
                    }
                }
                startListeningForKeyword();
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void startListeningForKeyword() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_PERMISSIONS);
        } else {
            speechRecognizer.startListening(speechRecognizerIntent);
        }
    }

    private void sendEmailRequest(String recipient, String locationLink, String nerText) {
        new Thread(() -> {
            OkHttpClient client = new OkHttpClient();
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("recipient_email", recipient);
                jsonObject.put("location", locationLink + " " + nerText);
                jsonObject.put("ner_text", nerText);
                RequestBody body = RequestBody.create(JSON, jsonObject.toString());
                Request request = new Request.Builder()
                        .url("https://flask-sos-mail.vercel.app/send-email")
                        .post(body)
                        .build();

                Response response = client.newCall(request).execute();
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            } catch (Exception e) {
                Log.e(TAG, "Failed to send email", e);
            }
        }).start();
    }
}
