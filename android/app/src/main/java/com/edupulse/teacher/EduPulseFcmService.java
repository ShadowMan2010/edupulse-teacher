package com.edupulse.teacher;

import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class EduPulseFcmService extends FirebaseMessagingService {

    private static final String TAG = "EduPulseFCM";
    private static final String PREFS_FCM = "edupulse_fcm";
    public static final String KEY_FCM_TOKEN = "fcm_token";

    public static final String ACTION_UPDATE_AVAILABLE = "com.edupulse.teacher.UPDATE_AVAILABLE";
    public static final String EXTRA_TAG = "update_tag";
    public static final String EXTRA_BODY = "update_body";
    public static final String EXTRA_URL = "update_url";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.d(TAG, "New FCM token: " + token);
        getSharedPreferences(PREFS_FCM, MODE_PRIVATE)
                .edit()
                .putString(KEY_FCM_TOKEN, token)
                .apply();
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        Log.d(TAG, "FCM message received: " + message.getData());

        if (!message.getData().containsKey("type")) return;

        String type = message.getData().get("type");
        if (!"update_available".equals(type)) return;

        String tag = message.getData().get("tag");
        String body = message.getData().get("body");
        String url = message.getData().get("url");

        if (tag == null || url == null) return;

        SharedPreferences prefs = getSharedPreferences(PREFS_FCM, MODE_PRIVATE);
        prefs.edit()
                .putString(EXTRA_TAG, tag)
                .putString(EXTRA_BODY, body != null ? body : "")
                .putString(EXTRA_URL, url)
                .apply();

        Intent intent = new Intent(ACTION_UPDATE_AVAILABLE);
        intent.putExtra(EXTRA_TAG, tag);
        intent.putExtra(EXTRA_BODY, body != null ? body : "");
        intent.putExtra(EXTRA_URL, url);
        sendBroadcast(intent);
    }
}
