package com.edupulse.teacher;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.ServerError;
import com.android.volley.TimeoutError;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.textfield.TextInputEditText;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "EduPulse";
    private static final String FIREBASE_API_KEY = "AIzaSyDPYjylSBhng6CRL77P0MXTUcuq7jBFnnA";
    private static final String FIREBASE_AUTH_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + FIREBASE_API_KEY;
    private static final String FIREBASE_IDP_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=" + FIREBASE_API_KEY;
    private static final String GITHUB_API_URL = "https://api.github.com/repos/ShadowMan2010/edupulse-teacher/releases/latest";
    private static final String PHOTO_BASE_URL = "https://edupulse-attendance-qr-default-rtdb.asia-southeast1.firebasedatabase.app/students/";
    private static final String PREFS_NAME = "edupulse_teacher";
    private static final int RC_SIGN_IN = 9001;

    public static final List<String> SECTIONS = Arrays.asList("A", "B", "C", "D", "E");

    private RequestQueue requestQueue;
    private SharedPreferences prefs;
    private GoogleSignInClient googleSignInClient;
    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo biometricPromptInfo;
    private MediaPlayer beepPlayer;
    private MediaPlayer successPlayer;
    private MediaPlayer notifPlayer;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler updateProgressHandler = new Handler(Looper.getMainLooper());

    private String currentEmail = "";
    private String currentIdToken = "";
    private String apiBaseUrl = "";
    private String currentClass = "";
    private String currentSection = "A";
    private String currentSubject = "";
    private boolean isPushing = false;

    private List<String> scannedStudentIds = new ArrayList<>();
    private List<Map<String, String>> scannedStudents = new ArrayList<>();
    private List<String> classList = new ArrayList<>();
    private Map<String, List<String>> subjectsMap = new HashMap<>();
    private Map<String, Map<String, String>> studentsCache = new HashMap<>();
    private List<String> currentSubjects = new ArrayList<>();
    private ScanStudentAdapter studentAdapter;
    private Map<String, Bitmap> photoCache = new HashMap<>();

    private View splashContainer, loginContainer, connectContainer, mainContainer;
    private View scanSummarySheet, loadingOverlay, successOverlay, biometricOverlay, updateOverlay;
    private View lastScannedCard, loadingCard, successCard, biometricCard, updateCard;
    private View statCard, classSectionCard, topBarCard, connectCard, loginCard;
    private Button signInButton, connectButton, scanButton, scanFinishedButton;
    private View googleSignInButton;
    private Button pushButton, addMoreButton, successDoneButton, usePasswordButton;
    private Button updateNowButton, updateLaterButton, exitButton;
    private TextInputEditText emailField, passwordField, ipField, portField, schoolIdField;
    private ImageView successAnimation;
    private TextView loginError, connectionStatus, scannedCount, classSubjectInfo;
    private TextView lastScannedName, lastScannedDetail, summaryCount;
    private TextView loadingText, loadingSubtext, successTitle, successSubtext;
    private TextView biometricStatus, updateVersion, updateChangelog, updateProgressText;
    private TextView userEmail;
    private Button classSelectButton, sectionSelectButton, subjectSelectButton;
    private TextView loginTeacherLabel;
    private ImageView lastScannedPhoto;
    private RecyclerView studentRecyclerView;
    private ProgressBar updateProgressBar;
    private View updateNotification;
    private TextView updateNotifyClose;
    private long updateDownloadId = -1;
    private String pendingUpdateTag = "";
    private String pendingUpdateBody = "";
    private String pendingUpdateUrl = "";
    private BroadcastReceiver fcmReceiver;

    // Student portal views
    private View studentContainer, studentInfoCard, studentStatsGrid, studentTopBar;
    private TextView studentNameDisplay, studentDetailDisplay;
    private TextView statTotal, statPresent, statLate, statAbsent;
    private RecyclerView studentAttendanceList;
    private TextView noRecordsText;
    private Button studentLogoutButton, studentModeToggle, studentSignInButton;
    private TextInputEditText rollField, dobField;
    private LinearLayout teacherLoginFields, studentLoginFields;
    private boolean isStudentMode = false;
    private String currentStudentId = "";
    private List<Map<String, String>> studentAttendanceData = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setTheme(R.style.Theme_EduPulse);
            setContentView(R.layout.activity_main);

            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            requestQueue = Volley.newRequestQueue(this);

            initViews();
            initSounds();
            initGoogleSignIn();
            initBiometric();
            setupClickListeners();
            setupSelectionButtons();
            setupRecyclerView();
            loadSavedState();
            routeUser();
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            String stack = sw.toString();
            Log.e(TAG, "onCreate crash: " + stack);
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Startup Error");
            builder.setMessage(stack.length() > 1000 ? stack.substring(0, 1000) : stack);
            builder.setPositiveButton("EXIT", (d, w) -> finish());
            builder.setCancelable(false);
            builder.show();
        }
    }

    private void initViews() {
        splashContainer = findViewById(R.id.splashContainer);
        loginContainer = findViewById(R.id.loginContainer);
        connectContainer = findViewById(R.id.connectContainer);
        mainContainer = findViewById(R.id.mainContainer);
        scanSummarySheet = findViewById(R.id.scanSummarySheet);
        loadingOverlay = findViewById(R.id.loadingOverlay);
        successOverlay = findViewById(R.id.successOverlay);
        biometricOverlay = findViewById(R.id.biometricOverlay);
        updateOverlay = findViewById(R.id.updateOverlay);
        lastScannedCard = findViewById(R.id.lastScannedCard);
        loadingCard = findViewById(R.id.loadingCard);
        successCard = findViewById(R.id.successCard);
        biometricCard = findViewById(R.id.biometricCard);
        updateCard = findViewById(R.id.updateCard);
        statCard = findViewById(R.id.statCard);
        classSectionCard = findViewById(R.id.classSectionCard);
        topBarCard = findViewById(R.id.topBarCard);
        connectCard = findViewById(R.id.connectCard);
        loginCard = findViewById(R.id.loginCard);

        signInButton = findViewById(R.id.signInButton);
        googleSignInButton = findViewById(R.id.googleSignInButton);
        connectButton = findViewById(R.id.connectButton);
        scanButton = findViewById(R.id.scanButton);
        scanFinishedButton = findViewById(R.id.scanFinishedButton);
        pushButton = findViewById(R.id.pushButton);
        addMoreButton = findViewById(R.id.addMoreButton);
        successDoneButton = findViewById(R.id.successDoneButton);
        usePasswordButton = findViewById(R.id.usePasswordButton);
        updateNowButton = findViewById(R.id.updateNowButton);
        updateLaterButton = findViewById(R.id.updateLaterButton);
        exitButton = findViewById(R.id.exitButton);

        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        ipField = findViewById(R.id.ipField);
        portField = findViewById(R.id.portField);
        schoolIdField = findViewById(R.id.schoolIdField);

        loginError = findViewById(R.id.loginError);
        connectionStatus = findViewById(R.id.connectionStatus);
        scannedCount = findViewById(R.id.scannedCount);
        classSubjectInfo = findViewById(R.id.classSubjectInfo);
        lastScannedName = findViewById(R.id.lastScannedName);
        lastScannedDetail = findViewById(R.id.lastScannedDetail);
        summaryCount = findViewById(R.id.summaryCount);
        loadingText = findViewById(R.id.loadingText);
        loadingSubtext = findViewById(R.id.loadingSubtext);
        successAnimation = findViewById(R.id.successAnimation);
        successTitle = findViewById(R.id.successTitle);
        successSubtext = findViewById(R.id.successSubtext);
        biometricStatus = findViewById(R.id.biometricStatus);
        updateVersion = findViewById(R.id.updateVersion);
        updateChangelog = findViewById(R.id.updateChangelog);
        updateProgressText = findViewById(R.id.updateProgressText);
        userEmail = findViewById(R.id.userEmail);

        lastScannedPhoto = findViewById(R.id.lastScannedPhoto);
        studentRecyclerView = findViewById(R.id.studentRecyclerView);
        updateProgressBar = findViewById(R.id.updateProgressBar);


        classSelectButton = findViewById(R.id.classSelectButton);
        sectionSelectButton = findViewById(R.id.sectionSelectButton);
        subjectSelectButton = findViewById(R.id.subjectSelectButton);

        updateNotification = findViewById(R.id.updateNotification);
        updateNotifyClose = findViewById(R.id.updateNotifyClose);

        loginTeacherLabel = findViewById(R.id.loginTeacherLabel);

        // Student portal views
        studentContainer = findViewById(R.id.studentContainer);
        studentTopBar = findViewById(R.id.studentTopBar);
        studentInfoCard = findViewById(R.id.studentInfoCard);
        studentStatsGrid = findViewById(R.id.studentStatsGrid);
        studentNameDisplay = findViewById(R.id.studentNameDisplay);
        studentDetailDisplay = findViewById(R.id.studentDetailDisplay);
        statTotal = findViewById(R.id.statTotal);
        statPresent = findViewById(R.id.statPresent);
        statLate = findViewById(R.id.statLate);
        statAbsent = findViewById(R.id.statAbsent);
        studentAttendanceList = findViewById(R.id.studentAttendanceList);
        noRecordsText = findViewById(R.id.noRecordsText);
        studentLogoutButton = findViewById(R.id.studentLogoutButton);
        studentModeToggle = findViewById(R.id.studentModeToggle);
        studentSignInButton = findViewById(R.id.studentSignInButton);
        rollField = findViewById(R.id.rollField);
        dobField = findViewById(R.id.dobField);
        teacherLoginFields = findViewById(R.id.teacherLoginFields);
        studentLoginFields = findViewById(R.id.studentLoginFields);
    }

    private void initSounds() {
        try {
            beepPlayer = MediaPlayer.create(this, R.raw.scan_beep);
            successPlayer = MediaPlayer.create(this, R.raw.scan_success);
            notifPlayer = MediaPlayer.create(this, R.raw.scan_notify);
        } catch (Exception e) {
            Log.e(TAG, "Error loading sounds", e);
        }
    }

    private void initGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("695473374968-7t0o5p7p7p7p7p7p7p7p7p7p7p7p7p7p.apps.googleusercontent.com")
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void initBiometric() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            return;
        }

        Executor executor = Executors.newSingleThreadExecutor();
        biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                mainHandler.post(() -> {
                    animateBiometricExit();
                    prefs.edit().putBoolean("biometric_enabled", true).apply();
                });
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                mainHandler.post(() -> {
                    biometricStatus.setText(errString);
                });
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                mainHandler.post(() -> biometricStatus.setText("Fingerprint not recognized"));
            }
        });

        biometricPromptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("EduPulse Teacher")
                .setSubtitle("Authenticate to continue")
                .setNegativeButtonText("Cancel")
                .build();
    }

    private void setupSelectionButtons() {
        sectionSelectButton.setText(currentSection.isEmpty() ? "A" : currentSection);
        if (!classList.isEmpty() && currentClass.isEmpty()) {
            currentClass = classList.get(0);
        }
        if (!currentClass.isEmpty()) {
            classSelectButton.setText(currentClass);
        }

        classSelectButton.setOnClickListener(v -> {
            if (classList.isEmpty()) return;
            String[] items = classList.toArray(new String[0]);
            int checked = classList.indexOf(currentClass);
            new android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
                    .setTitle("Select Class")
                    .setSingleChoiceItems(items, checked < 0 ? 0 : checked, (d, which) -> {
                        String cls = classList.get(which);
                        if (!cls.equals(currentClass)) {
                            currentClass = cls;
                            classSelectButton.setText(cls);
                            updateSubjectsForClass(cls);
                            prefetchStudents(cls);
                        }
                        d.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        sectionSelectButton.setOnClickListener(v -> {
            String[] items = SECTIONS.toArray(new String[0]);
            int checked = SECTIONS.indexOf(currentSection);
            new android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
                    .setTitle("Select Section")
                    .setSingleChoiceItems(items, checked < 0 ? 0 : checked, (d, which) -> {
                        currentSection = SECTIONS.get(which);
                        sectionSelectButton.setText(currentSection);
                        d.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        subjectSelectButton.setOnClickListener(v -> {
            if (currentSubjects.isEmpty()) {
                Toast.makeText(this, "No subjects available", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] items = currentSubjects.toArray(new String[0]);
            int checked = currentSubjects.indexOf(currentSubject);
            new android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
                    .setTitle("Select Subject")
                    .setSingleChoiceItems(items, checked < 0 ? 0 : checked, (d, which) -> {
                        currentSubject = currentSubjects.get(which);
                        subjectSelectButton.setText(currentSubject);
                        d.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void setupClickListeners() {
        signInButton.setOnClickListener(v -> loginWithEmail());
        googleSignInButton.setOnClickListener(v -> signInWithGoogle());
        connectButton.setOnClickListener(v -> testConnection());
        scanButton.setOnClickListener(v -> startQrScan());
        scanFinishedButton.setOnClickListener(v -> showSummarySheet());
        pushButton.setOnClickListener(v -> pushAttendance());
        addMoreButton.setOnClickListener(v -> hideSummarySheet());
        successDoneButton.setOnClickListener(v -> resetAfterSuccess());
        usePasswordButton.setOnClickListener(v -> {
            animateBiometricExit();
            prefs.edit().putBoolean("biometric_enabled", false).apply();
        });
        updateNowButton.setOnClickListener(v -> downloadUpdate());
        updateLaterButton.setOnClickListener(v -> hideUpdateOverlay());
        exitButton.setOnClickListener(v -> logout());
        updateNotification.setOnClickListener(v -> {
            hideUpdateNotification();
            showUpdateOverlay(pendingUpdateTag, pendingUpdateBody, pendingUpdateUrl);
        });
        updateNotifyClose.setOnClickListener(v -> {
            hideUpdateNotification();
            prefs.edit().putString("dismissed_update", pendingUpdateTag).apply();
        });

        // Student portal
        studentModeToggle.setOnClickListener(v -> toggleStudentMode());
        studentSignInButton.setOnClickListener(v -> loginStudent());
        studentLogoutButton.setOnClickListener(v -> studentLogout());
    }

    private void setupRecyclerView() {
        studentAdapter = new ScanStudentAdapter(scannedStudents, studentId -> {
            removeStudent(studentId);
        });
        studentRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        studentRecyclerView.setAdapter(studentAdapter);
    }

    private void loadSavedState() {
        currentEmail = prefs.getString("email", "");
        currentIdToken = prefs.getString("id_token", "");
        String ip = prefs.getString("api_ip", "");
        String port = prefs.getString("api_port", "3000");
        if (!ip.isEmpty()) {
            apiBaseUrl = "http://" + ip + ":" + port;
            ipField.setText(ip);
            portField.setText(port);
        }
    }

    private void routeUser() {
        String crashLog = EduPulseApp.getLastCrash(prefs);
        if (!crashLog.isEmpty()) {
            EduPulseApp.clearCrash(prefs);
            Log.e(TAG, "Previous crash:\n" + crashLog);
            String firstLine = crashLog.contains("\n") ? crashLog.substring(0, crashLog.indexOf("\n")) : crashLog;
            Toast.makeText(this, "Last crash: " + firstLine, Toast.LENGTH_LONG).show();
        }
        if (prefs.getBoolean("logged_in", false) && !currentIdToken.isEmpty()) {
            showSplash(() -> {
                if (apiBaseUrl.isEmpty()) {
                    showScreen("connect");
                } else {
                    showScreen("main");
                    loadSubjects();
                    checkUpdate();
                    registerFcmTokenWithServer();
                    if (prefs.getBoolean("biometric_enabled", false)) {
                        showBiometricLock();
                    }
                }
            });
        } else {
            showSplash(() -> showScreen("login"));
        }
    }

    private void showSplash(Runnable onComplete) {
        showScreen("splash");
        GlassCardView card = findViewById(R.id.splashCard);
        if (card != null) card.startSheenAnimation();
        mainHandler.postDelayed(onComplete, 1500);
    }

    private void showScreen(String screen) {
        splashContainer.setVisibility("splash".equals(screen) ? View.VISIBLE : View.GONE);
        loginContainer.setVisibility("login".equals(screen) ? View.VISIBLE : View.GONE);
        connectContainer.setVisibility("connect".equals(screen) ? View.VISIBLE : View.GONE);
        mainContainer.setVisibility("main".equals(screen) ? View.VISIBLE : View.GONE);
        studentContainer.setVisibility("student".equals(screen) ? View.VISIBLE : View.GONE);

        if ("login".equals(screen)) {
            animateScreenEntrance(loginCard);
        } else if ("connect".equals(screen)) {
            animateScreenEntrance(connectCard);
        } else if ("main".equals(screen)) {
            animateMultipleCards();
        }
    }

    private void animateScreenEntrance(View card) {
        card.setAlpha(1f);
        card.setTranslationY(0f);
    }

    private void animateMultipleCards() {
        View[] cards = {statCard, classSectionCard};
        for (int i = 0; i < cards.length; i++) {
            final View card = cards[i];
            if (card != null) {
                card.setAlpha(0f);
                card.setTranslationY(40f);
                card.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(400)
                        .setStartDelay(i * 80L)
                        .setInterpolator(new DecelerateInterpolator())
                        .withEndAction(() -> {
                            if (card instanceof GlassCardView) {
                                ((GlassCardView) card).startSheenAnimation();
                            }
                        })
                        .start();
            }
        }
    }

    private void loginWithEmail() {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            loginError.setVisibility(View.VISIBLE);
            loginError.setText("Enter email and password");
            return;
        }

        showLoading("Signing in...", "");
        loginError.setVisibility(View.GONE);

        try {
            JSONObject body = new JSONObject();
            body.put("email", email);
            body.put("password", password);
            body.put("returnSecureToken", true);

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, FIREBASE_AUTH_URL, body,
                    response -> {
                        try {
                            String idToken = response.getString("idToken");
                            String userEmail = response.getString("email");
                            onLoginSuccess(userEmail, idToken);
                        } catch (JSONException e) {
                            hideLoading();
                            loginError.setVisibility(View.VISIBLE);
                            loginError.setText("Parse error");
                        }
                    },
                    error -> {
                        hideLoading();
                        loginError.setVisibility(View.VISIBLE);
                        loginError.setText(getLoginErrorMessage(error));
                    });

            request.setRetryPolicy(new DefaultRetryPolicy(15000, 1, 1f));
            requestQueue.add(request);
        } catch (JSONException e) {
            hideLoading();
            loginError.setVisibility(View.VISIBLE);
            loginError.setText("Error creating request");
        }
    }

    private void signInWithGoogle() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (scanResult != null) {
            handleScanResult(scanResult);
            return;
        }

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) {
                    exchangeGoogleToken(account);
                }
            } catch (ApiException e) {
                loginError.setVisibility(View.VISIBLE);
                loginError.setText("Google sign-in failed");
            }
        }
    }

    private void exchangeGoogleToken(GoogleSignInAccount account) {
        showLoading("Signing in...", "");

        try {
            JSONObject body = new JSONObject();
            body.put("postBody", "id_token=" + account.getIdToken() + "&providerId=google.com");
            body.put("requestUri", "http://localhost");
            body.put("returnSecureToken", true);

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, FIREBASE_IDP_URL, body,
                    response -> {
                        try {
                            String idToken = response.getString("idToken");
                            String email = response.optString("email", account.getEmail());
                            onLoginSuccess(email, idToken);
                        } catch (JSONException e) {
                            hideLoading();
                            loginError.setVisibility(View.VISIBLE);
                            loginError.setText("Parse error");
                        }
                    },
                    error -> {
                        hideLoading();
                        loginError.setVisibility(View.VISIBLE);
                        loginError.setText("Firebase exchange failed");
                    });

            request.setRetryPolicy(new DefaultRetryPolicy(15000, 1, 1f));
            requestQueue.add(request);
        } catch (JSONException e) {
            hideLoading();
            loginError.setVisibility(View.VISIBLE);
            loginError.setText("Error creating request");
        }
    }

    private void onLoginSuccess(String email, String idToken) {
        currentEmail = email;
        currentIdToken = idToken;

        prefs.edit()
                .putBoolean("logged_in", true)
                .putString("email", email)
                .putString("id_token", idToken)
                .apply();

        hideLoading();
        userEmail.setText(email);

        if (apiBaseUrl.isEmpty()) {
            showScreen("connect");
        } else {
            showScreen("main");
            loadSubjects();
            checkUpdate();
            registerFcmTokenWithServer();
        }

        if (isBiometricAvailable() && !prefs.getBoolean("biometric_enabled", false)) {
            showBiometricEnrollmentPrompt();
        }
    }

    private boolean isBiometricAvailable() {
        BiometricManager biometricManager = BiometricManager.from(this);
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void showBiometricEnrollmentPrompt() {
        Toast.makeText(this, "Enable fingerprint for faster login?", Toast.LENGTH_LONG).show();
    }

    private void showBiometricLock() {
        biometricOverlay.setVisibility(View.VISIBLE);
        biometricOverlay.setAlpha(0f);
        biometricOverlay.animate().alpha(1f).setDuration(300).start();
        biometricStatus.setText("Touch the fingerprint sensor");
        if (biometricPrompt != null) {
            biometricPrompt.authenticate(biometricPromptInfo);
        }
    }

    private void animateBiometricExit() {
        biometricOverlay.animate()
                .alpha(0f)
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> biometricOverlay.setVisibility(View.GONE))
                .start();
    }

    // ── Student Portal Methods ──

    private void toggleStudentMode() {
        isStudentMode = !isStudentMode;
        if (isStudentMode) {
            teacherLoginFields.setVisibility(View.GONE);
            googleSignInButton.setVisibility(View.GONE);
            studentLoginFields.setVisibility(View.VISIBLE);
            studentModeToggle.setText(R.string.switch_to_teacher);
            loginTeacherLabel.setText(R.string.student_portal);
        } else {
            teacherLoginFields.setVisibility(View.VISIBLE);
            googleSignInButton.setVisibility(View.VISIBLE);
            studentLoginFields.setVisibility(View.GONE);
            studentModeToggle.setText(R.string.student_portal);
            loginTeacherLabel.setText(R.string.teacher_sign_in);
        }
        loginError.setVisibility(View.GONE);
    }

    private void loginStudent() {
        String roll = rollField.getText().toString().trim();
        String dob = dobField.getText().toString().trim();

        if (roll.isEmpty() || dob.isEmpty()) {
            loginError.setVisibility(View.VISIBLE);
            loginError.setText("Enter roll number and DOB (YYYY-MM-DD)");
            return;
        }

        if (apiBaseUrl.isEmpty()) {
            loginError.setVisibility(View.VISIBLE);
            loginError.setText("Connect to server first");
            return;
        }

        showLoading("Signing in...", "");
        loginError.setVisibility(View.GONE);

        try {
            JSONObject body = new JSONObject();
            body.put("roll_no", roll);
            body.put("dob", dob);

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST,
                    apiBaseUrl + "/api/student/login", body,
                    response -> {
                        hideLoading();
                        try {
                            if (response.optBoolean("success", false)) {
                                JSONObject data = response.getJSONObject("data");
                                currentStudentId = data.getString("id");
                                String name = data.optString("name", "");
                                String rollNo = data.optString("roll_no", "");
                                String cls = data.optString("class", "");
                                String section = data.optString("section", "");

                                showStudentDashboard(name, rollNo, cls, section);
                            } else {
                                loginError.setVisibility(View.VISIBLE);
                                loginError.setText(response.optString("error", getString(R.string.invalid_credentials)));
                            }
                        } catch (JSONException e) {
                            loginError.setVisibility(View.VISIBLE);
                            loginError.setText("Parse error");
                        }
                    },
                    error -> {
                        hideLoading();
                        loginError.setVisibility(View.VISIBLE);
                        loginError.setText(getLoginErrorMessage(error));
                    });

            request.setRetryPolicy(new DefaultRetryPolicy(15000, 1, 1f));
            requestQueue.add(request);
        } catch (JSONException e) {
            hideLoading();
            loginError.setVisibility(View.VISIBLE);
            loginError.setText("Error creating request");
        }
    }

    private void showStudentDashboard(String name, String rollNo, String cls, String section) {
        studentNameDisplay.setText(name);
        studentDetailDisplay.setText("Roll " + rollNo + "  ·  Class " + cls + (section.isEmpty() ? "" : " " + section));

        showScreen("student");
        loadStudentStats();
        loadStudentAttendance();
    }

    private void loadStudentStats() {
        if (currentStudentId.isEmpty() || apiBaseUrl.isEmpty()) return;

        StringRequest request = new StringRequest(Request.Method.GET,
                apiBaseUrl + "/api/student/" + currentStudentId + "/stats",
                response -> {
                    try {
                        JSONObject json = new JSONObject(response);
                        if (json.optBoolean("success", false)) {
                            JSONObject data = json.getJSONObject("data");
                            statTotal.setText(String.valueOf(data.optInt("total", 0)));
                            statPresent.setText(String.valueOf(data.optInt("present", 0)));
                            statLate.setText(String.valueOf(data.optInt("late", 0)));
                            statAbsent.setText(String.valueOf(data.optInt("absent", 0)));
                        }
                    } catch (JSONException ignored) {}
                },
                error -> {});

        request.setRetryPolicy(new DefaultRetryPolicy(10000, 1, 1f));
        requestQueue.add(request);
    }

    private void loadStudentAttendance() {
        if (currentStudentId.isEmpty() || apiBaseUrl.isEmpty()) return;

        StringRequest request = new StringRequest(Request.Method.GET,
                apiBaseUrl + "/api/student/" + currentStudentId + "/attendance?limit=50",
                response -> {
                    try {
                        JSONObject json = new JSONObject(response);
                        if (json.optBoolean("success", false)) {
                            JSONArray arr = json.getJSONArray("data");
                            studentAttendanceData.clear();
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject item = arr.getJSONObject(i);
                                Map<String, String> map = new HashMap<>();
                                map.put("date", item.optString("date", ""));
                                map.put("time", item.optString("time", ""));
                                map.put("status", item.optString("status", ""));
                                map.put("subject", item.optString("subject", ""));
                                studentAttendanceData.add(map);
                            }
                            showStudentAttendanceList();
                        }
                    } catch (JSONException ignored) {}
                },
                error -> {});

        request.setRetryPolicy(new DefaultRetryPolicy(10000, 1, 1f));
        requestQueue.add(request);
    }

    private void showStudentAttendanceList() {
        if (studentAttendanceData.isEmpty()) {
            noRecordsText.setVisibility(View.VISIBLE);
            studentAttendanceList.setVisibility(View.GONE);
            return;
        }
        noRecordsText.setVisibility(View.GONE);
        studentAttendanceList.setVisibility(View.VISIBLE);
        studentAttendanceList.setLayoutManager(new LinearLayoutManager(this));
        studentAttendanceList.setAdapter(new StudentAttendanceAdapter(studentAttendanceData));
    }

    private void studentLogout() {
        currentStudentId = "";
        studentAttendanceData.clear();
        rollField.setText("");
        dobField.setText("");
        if (isStudentMode) {
            isStudentMode = false;
            teacherLoginFields.setVisibility(View.VISIBLE);
            googleSignInButton.setVisibility(View.VISIBLE);
            studentLoginFields.setVisibility(View.GONE);
            studentModeToggle.setText(R.string.student_portal);
            loginTeacherLabel.setText(R.string.teacher_sign_in);
        }
        showScreen("login");
    }

    // Student attendance adapter
    private class StudentAttendanceAdapter extends RecyclerView.Adapter<StudentAttendanceAdapter.ViewHolder> {
        private List<Map<String, String>> items;

        StudentAttendanceAdapter(List<Map<String, String>> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_student_scan, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, String> item = items.get(position);
            String date = item.get("date");
            String status = item.get("status");
            String time = item.get("time");
            String subject = item.get("subject");

            holder.nameText.setText(date);
            holder.detailText.setText(time != null && !time.isEmpty() ? time : "");
            holder.photoView.setVisibility(View.GONE);

            int statusColor;
            String badgeText;
            switch (status != null ? status : "") {
                case "present":
                    statusColor = Color.parseColor("#00FF88");
                    badgeText = "PRESENT";
                    break;
                case "late":
                    statusColor = Color.parseColor("#F59E0B");
                    badgeText = "LATE";
                    break;
                case "absent":
                    statusColor = Color.parseColor("#FF3B3B");
                    badgeText = "ABSENT";
                    break;
                case "half-day":
                    statusColor = Color.parseColor("#BB00FF");
                    badgeText = "HALF DAY";
                    break;
                default:
                    statusColor = Color.parseColor("#8CFFFFFF");
                    badgeText = status != null ? status.toUpperCase() : "";
            }

            holder.statusBadge.setText(badgeText);
            holder.statusBadge.setTextColor(statusColor);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(50);
            bg.setColor(Color.parseColor("#1AFFFFFF"));
            holder.statusBadge.setBackground(bg);
            holder.removeButton.setVisibility(View.GONE);
            holder.itemView.setBackground(null);

            if (subject != null && !subject.isEmpty()) {
                holder.detailText.setText((time != null ? time : "") + "  ·  " + subject);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView photoView;
            TextView nameText, detailText, statusBadge, removeButton;

            ViewHolder(View v) {
                super(v);
                photoView = v.findViewById(R.id.studentPhoto);
                nameText = v.findViewById(R.id.studentName);
                detailText = v.findViewById(R.id.studentDetail);
                statusBadge = v.findViewById(R.id.statusBadge);
                removeButton = v.findViewById(R.id.removeButton);
            }
        }
    }

    private void testConnection() {
        String ip = ipField.getText().toString().trim();
        String port = portField.getText().toString().trim();
        String enteredSchoolId = schoolIdField.getText().toString().trim();

        if (ip.isEmpty()) {
            connectionStatus.setText("Enter server IP");
            connectionStatus.setVisibility(View.VISIBLE);
            connectionStatus.setTextColor(Color.parseColor("#FF3B3B"));
            return;
        }

        if (port.isEmpty()) port = "3000";
        String finalPort = port;

        String baseUrl = "http://" + ip + ":" + finalPort;
        showLoading("Testing connection...", baseUrl);
        connectionStatus.setVisibility(View.GONE);

        StringRequest request = new StringRequest(Request.Method.GET, baseUrl + "/api/health",
                response -> {
                    hideLoading();
                    apiBaseUrl = baseUrl;
                    try {
                        JSONObject healthJson = new JSONObject(response);
                        String serverSchoolId = healthJson.optString("schoolId", "");

                        if (!enteredSchoolId.isEmpty() && !enteredSchoolId.equals(serverSchoolId)) {
                            connectionStatus.setText("School ID mismatch. Check your School ID.");
                            connectionStatus.setTextColor(Color.parseColor("#FF3B3B"));
                            connectionStatus.setVisibility(View.VISIBLE);
                            return;
                        }

                        if (!serverSchoolId.isEmpty()) {
                            prefs.edit().putString("school_id", serverSchoolId).apply();
                            TextView schoolIdDisplay = findViewById(R.id.schoolIdDisplay);
                            schoolIdDisplay.setText("ID: " + serverSchoolId.substring(0, Math.min(8, serverSchoolId.length())) + "...");
                            schoolIdDisplay.setVisibility(View.VISIBLE);
                        }
                    } catch (JSONException ignored) {}
                    prefs.edit()
                            .putString("api_ip", ip)
                            .putString("api_port", finalPort)
                            .apply();
                    connectionStatus.setText("Connection successful");
                    connectionStatus.setTextColor(Color.parseColor("#00FF88"));
                    connectionStatus.setVisibility(View.VISIBLE);
                    showScreen("main");
                    loadSubjects();
                    checkUpdate();
                },
                error -> {
                    hideLoading();
                    connectionStatus.setText("Connection failed: " + getConnectionErrorMessage(error));
                    connectionStatus.setTextColor(Color.parseColor("#FF3B3B"));
                    connectionStatus.setVisibility(View.VISIBLE);
                });

        request.setRetryPolicy(new DefaultRetryPolicy(10000, 1, 1f));
        requestQueue.add(request);
    }

    private void loadSubjects() {
        if (apiBaseUrl.isEmpty()) return;

        StringRequest request = new StringRequest(Request.Method.GET, apiBaseUrl + "/api/subjects",
                response -> {
                    try {
                        JSONObject json = new JSONObject(response);
                        if (json.optBoolean("success", false)) {
                            JSONObject data = json.getJSONObject("data");
                            subjectsMap.clear();
                            classList.clear();

                            Iterator<String> keys = data.keys();
                            while (keys.hasNext()) {
                                String cls = keys.next();
                                classList.add(cls);
                                JSONArray subs = data.getJSONArray(cls);
                                List<String> subList = new ArrayList<>();
                                for (int i = 0; i < subs.length(); i++) {
                                    subList.add(subs.getString(i));
                                }
                                subjectsMap.put(cls, subList);
                            }

                            if (!classList.isEmpty()) {
                                if (currentClass.isEmpty() || !classList.contains(currentClass)) {
                                    currentClass = classList.get(0);
                                }
                                classSelectButton.setText(currentClass);
                                updateSubjectsForClass(currentClass);
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing subjects", e);
                    }
                },
                error -> Log.e(TAG, "Error loading subjects", error));

        request.setRetryPolicy(new DefaultRetryPolicy(10000, 1, 1f));
        requestQueue.add(request);
    }

    private void updateSubjectsForClass(String cls) {
        currentSubjects = subjectsMap.get(cls);
        if (currentSubjects == null) currentSubjects = new ArrayList<>();

        if (!currentSubjects.contains(currentSubject)) {
            currentSubject = "";
            subjectSelectButton.setText("Select Subject");
        }
    }

    private void prefetchStudents(String cls) {
        if (apiBaseUrl.isEmpty()) return;

        StringRequest request = new StringRequest(Request.Method.GET, apiBaseUrl + "/api/students?class=" + cls,
                response -> {
                    try {
                        JSONObject json = new JSONObject(response);
                        if (json.optBoolean("success", false)) {
                            JSONObject data = json.getJSONObject("data");
                            studentsCache.clear();
                            Iterator<String> keys = data.keys();
                            while (keys.hasNext()) {
                                String studentId = keys.next();
                                JSONObject student = data.getJSONObject(studentId);
                                Map<String, String> info = new HashMap<>();
                                info.put("name", student.optString("name", ""));
                                info.put("class", student.optString("class", ""));
                                info.put("section", student.optString("section", ""));
                                info.put("photo", student.optString("photo", ""));
                                studentsCache.put(studentId, info);
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing students", e);
                    }
                },
                error -> Log.e(TAG, "Error prefetching students", error));

        request.setRetryPolicy(new DefaultRetryPolicy(10000, 1, 1f));
        requestQueue.add(request);
    }

    private void startQrScan() {
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setCaptureActivity(PortraitCaptureActivity.class);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("Scan student QR code");
        integrator.setCameraId(0);
        integrator.setBeepEnabled(false);
        integrator.setBarcodeImageEnabled(false);
        integrator.initiateScan();
    }

    private void handleScanResult(IntentResult result) {
        if (result == null || result.getContents() == null) return;

        String contents = result.getContents();
        String studentId = extractStudentId(contents);

        if (studentId == null) {
            Toast.makeText(this, "Invalid QR code", Toast.LENGTH_SHORT).show();
            return;
        }

        if (scannedStudentIds.contains(studentId)) {
            Toast.makeText(this, "Student already scanned", Toast.LENGTH_SHORT).show();
            return;
        }

        processScannedStudent(studentId);
    }

    private String extractStudentId(String qrData) {
        try {
            JSONObject json = new JSONObject(qrData);
            if (json.has("student_id")) return json.getString("student_id");
            if (json.has("id")) return json.getString("id");
        } catch (JSONException e) {
            if (qrData.matches("^[A-Za-z0-9]+$")) return qrData;
        }
        return null;
    }

    private void processScannedStudent(String studentId) {
        if (studentsCache.containsKey(studentId)) {
            addScannedStudent(studentId, studentsCache.get(studentId));
        } else {
            fetchStudentDetails(studentId);
        }
    }

    private void fetchStudentDetails(String studentId) {
        if (apiBaseUrl.isEmpty()) return;

        StringRequest request = new StringRequest(Request.Method.GET, apiBaseUrl + "/api/students/" + studentId,
                response -> {
                    try {
                        JSONObject json = new JSONObject(response);
                        if (json.optBoolean("success", false)) {
                            JSONObject data = json.getJSONObject("data");
                            Map<String, String> info = new HashMap<>();
                            info.put("name", data.optString("name", "Unknown"));
                            info.put("class", data.optString("class", ""));
                            info.put("section", data.optString("section", ""));
                            info.put("photo", data.optString("photo", ""));
                            studentsCache.put(studentId, info);
                            addScannedStudent(studentId, info);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing student details", e);
                        addScannedStudent(studentId, null);
                    }
                },
                error -> addScannedStudent(studentId, null));

        request.setRetryPolicy(new DefaultRetryPolicy(10000, 1, 1f));
        requestQueue.add(request);
    }

    private void addScannedStudent(String studentId, Map<String, String> info) {
        String studentClass = info != null ? info.get("class") : "";
        if (!studentClass.isEmpty() && !studentClass.equals(currentClass)) {
            Toast.makeText(this, "Student is not in selected class", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = info != null ? info.get("name") : "Unknown";
        String section = info != null ? info.get("section") : currentSection;
        String photo = info != null ? info.get("photo") : "";

        if (scannedStudentIds.contains(studentId)) {
            Toast.makeText(this, "Student already scanned", Toast.LENGTH_SHORT).show();
            return;
        }

        scannedStudentIds.add(studentId);

        Map<String, String> student = new HashMap<>();
        student.put("id", studentId);
        student.put("name", name);
        student.put("class", currentClass);
        student.put("section", section);
        student.put("photo", photo);
        student.put("status", "present");
        scannedStudents.add(student);

        updateScannedUI();
        playBeep();
        playNotify();
        loadStudentPhoto(studentId, photo);
        updateLastScanned(student);
    }

    private void removeStudent(String studentId) {
        for (int i = 0; i < scannedStudents.size(); i++) {
            if (scannedStudents.get(i).get("id").equals(studentId)) {
                scannedStudents.remove(i);
                break;
            }
        }
        scannedStudentIds.remove(studentId);
        updateScannedUI();
    }

    private void updateScannedUI() {
        int count = scannedStudents.size();
        scannedCount.setText(String.valueOf(count));
        summaryCount.setText(String.valueOf(count));
        studentAdapter.notifyDataSetChanged();

        if (count > 0) {
            scanFinishedButton.setVisibility(View.VISIBLE);
        } else {
            scanFinishedButton.setVisibility(View.GONE);
            scanSummarySheet.setVisibility(View.GONE);
        }
    }

    private void updateLastScanned(Map<String, String> student) {
        String name = student.get("name");
        String detail = student.get("class") + " \u00b7 " + student.get("section");
        lastScannedName.setText(name);
        lastScannedDetail.setText(detail);
        lastScannedCard.setVisibility(View.VISIBLE);
    }

    private void loadStudentPhoto(String studentId, String photoUrl) {
        if (photoUrl == null || photoUrl.isEmpty()) {
            loadPhotoFromFirebase(studentId);
            return;
        }

        ImageRequest request = new ImageRequest(photoUrl,
                bitmap -> {
                    photoCache.put(studentId, bitmap);
                    studentAdapter.notifyDataSetChanged();
                    if (scannedStudents.size() > 0) {
                        Map<String, String> last = scannedStudents.get(scannedStudents.size() - 1);
                        if (last.get("id").equals(studentId)) {
                            lastScannedPhoto.setImageBitmap(bitmap);
                        }
                    }
                }, 0, 0, ImageView.ScaleType.CENTER_CROP, null,
                error -> loadPhotoFromFirebase(studentId));
        requestQueue.add(request);
    }

    private void loadPhotoFromFirebase(String studentId) {
        String fbUrl = PHOTO_BASE_URL + studentId + ".json";
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, fbUrl, null,
                response -> {
                    String photoStr = response.optString("photo", "");
                    if (!photoStr.isEmpty()) {
                        ImageRequest imgReq = new ImageRequest(photoStr,
                                bitmap -> {
                                    photoCache.put(studentId, bitmap);
                                    studentAdapter.notifyDataSetChanged();
                                }, 0, 0, ImageView.ScaleType.CENTER_CROP, null, null);
                        requestQueue.add(imgReq);
                    }
                },
                error -> {});
        requestQueue.add(request);
    }

    private void showSummarySheet() {
        scanSummarySheet.setVisibility(View.VISIBLE);
        scanSummarySheet.setTranslationY(scanSummarySheet.getHeight());
        scanSummarySheet.animate()
                .translationY(0)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void hideSummarySheet() {
        scanSummarySheet.animate()
                .translationY(scanSummarySheet.getHeight())
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> scanSummarySheet.setVisibility(View.GONE))
                .start();
    }

    private void pushAttendance() {
        if (isPushing) return;
        if (scannedStudents.isEmpty()) return;
        if (currentSubject.isEmpty()) {
            Toast.makeText(this, "Select a subject", Toast.LENGTH_SHORT).show();
            return;
        }

        isPushing = true;
        pushButton.setEnabled(false);
        showLoading("Pushing attendance...", scannedStudents.size() + " students");

        pushNextStudent(0);
    }

    private void pushNextStudent(final int index) {
        if (index >= scannedStudents.size()) {
            hideLoading();
            isPushing = false;
            pushButton.setEnabled(true);
            playSuccess();
            showSuccessOverlay();
            return;
        }

        Map<String, String> student = scannedStudents.get(index);

        try {
            JSONObject body = new JSONObject();
            body.put("student_id", student.get("id"));
            body.put("class", student.get("class"));
            body.put("section", student.get("section"));
            body.put("subject", currentSubject);

            JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST,
                    apiBaseUrl + "/api/attendance/scan", body,
                    response -> {
                        student.put("status", "pushed");
                        studentAdapter.notifyDataSetChanged();
                        pushNextStudent(index + 1);
                    },
                    error -> {
                        student.put("status", "failed");
                        studentAdapter.notifyDataSetChanged();
                        pushNextStudent(index + 1);
                    });

            loadingSubtext.setText((index + 1) + "/" + scannedStudents.size());
            loadingSubtext.setVisibility(View.VISIBLE);
            request.setRetryPolicy(new DefaultRetryPolicy(10000, 1, 1f));
            requestQueue.add(request);
        } catch (JSONException e) {
            pushNextStudent(index + 1);
        }
    }

    private void resetAfterSuccess() {
        successOverlay.setVisibility(View.GONE);
        scannedStudentIds.clear();
        scannedStudents.clear();
        studentAdapter.notifyDataSetChanged();
        updateScannedUI();
        lastScannedCard.setVisibility(View.GONE);
        scannedCount.setText("0");
        summaryCount.setText("0");
        scanSummarySheet.setVisibility(View.GONE);
        scanFinishedButton.setVisibility(View.GONE);
    }

    private void showSuccessOverlay() {
        successOverlay.setVisibility(View.VISIBLE);
        successOverlay.setAlpha(0f);
        successOverlay.animate().alpha(1f).setDuration(300).start();

        successCard.setScaleX(0f);
        successCard.setScaleY(0f);
        successCard.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> successCard.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start());
    }

    private void checkUpdate() {
        String dismissed = prefs.getString("dismissed_update", "");
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                URL url = new URL(GITHUB_API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject release = new JSONObject(sb.toString());
                    String tagName = release.optString("tag_name", "");
                    String body = release.optString("body", "");
                    JSONArray assets = release.optJSONArray("assets");

                    if (tagName.equals(dismissed)) return;

                    int latestVersion = parseVersionCode(tagName);
                    int currentVersion = getVersionCode();

                    if (latestVersion > currentVersion && assets != null && assets.length() > 0) {
                        String apkUrl = assets.getJSONObject(0).optString("browser_download_url", "");
                        mainHandler.post(() -> showUpdateNotification(tagName, body, apkUrl));
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Error checking update", e);
            }
        });
    }

    private int parseVersionCode(String tagName) {
        try {
            String clean = tagName.startsWith("v") ? tagName.substring(1) : tagName;
            String[] parts = clean.split("\\.");
            if (parts.length == 2) {
                return Integer.parseInt(parts[0]) * 1000 + Integer.parseInt(parts[1]);
            }
            String digits = tagName.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) return 0;
            return Integer.parseInt(digits) * 1000;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int getVersionCode() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 5;
        }
    }

    private void showUpdateOverlay(String version, String changelog, String apkUrl) {
        updateOverlay.setVisibility(View.VISIBLE);
        updateVersion.setText(version);
        updateChangelog.setText(changelog);
        updateCard.setTranslationY(updateCard.getHeight());
        updateCard.animate()
                .translationY(0)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        prefs.edit().putString("ota_url", apkUrl).apply();
    }

    private void hideUpdateOverlay() {
        String tag = updateVersion.getText().toString();
        if (!tag.isEmpty()) {
            prefs.edit().putString("dismissed_update", tag).apply();
        }
        updateCard.animate()
                .translationY(updateCard.getHeight())
                .setDuration(250)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> updateOverlay.setVisibility(View.GONE))
                .start();
    }

    private void showUpdateNotification(String tag, String changelog, String url) {
        pendingUpdateTag = tag;
        pendingUpdateBody = changelog;
        pendingUpdateUrl = url;
        updateNotification.setVisibility(View.VISIBLE);
        updateNotification.setAlpha(0f);
        updateNotification.animate().alpha(1f).setDuration(300).start();
    }

    private void hideUpdateNotification() {
        updateNotification.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> updateNotification.setVisibility(View.GONE))
                .start();
    }

    private void downloadUpdate() {
        String url = prefs.getString("ota_url", "");
        if (url.isEmpty()) url = pendingUpdateUrl;
        if (url.isEmpty()) return;

        downloadUpdateFromUrl(url);
    }

    private void downloadUpdateFromUrl(String url) {
        updateNowButton.setEnabled(false);
        updateLaterButton.setEnabled(false);
        updateProgressBar.setVisibility(View.VISIBLE);
        updateProgressBar.setProgress(0);
        updateProgressText.setVisibility(View.VISIBLE);
        updateProgressText.setText("0%");

        hideUpdateNotification();

        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            updateProgressText.setText("Download failed");
            updateNowButton.setEnabled(true);
            updateLaterButton.setEnabled(true);
            return;
        }

        Uri uri = Uri.parse(url);
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setTitle("EduPulse Update");
        request.setDescription("Downloading APK...");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalFilesDir(this, null, "updates/edupulse-update.apk");

        updateDownloadId = downloadManager.enqueue(request);
        startUpdateProgressPolling();

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == updateDownloadId) {
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(updateDownloadId);
                    Cursor cursor = downloadManager.query(query);
                    if (cursor.moveToFirst()) {
                        int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        int status = cursor.getInt(statusIndex);
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            updateProgressText.setText("Installing...");
                            updateProgressBar.setProgress(100);
                            updateProgressHandler.removeCallbacksAndMessages(null);
                            installApk();
                        } else if (status == DownloadManager.STATUS_FAILED) {
                            updateProgressText.setText("Download failed");
                            updateNowButton.setEnabled(true);
                            updateLaterButton.setEnabled(true);
                            updateProgressHandler.removeCallbacksAndMessages(null);
                        }
                    }
                    cursor.close();
                    unregisterReceiver(this);
                }
            }
        };

        registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED);
    }

    private void startUpdateProgressPolling() {
        if (updateDownloadId == -1) return;
        DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (downloadManager == null) return;

        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(updateDownloadId);
        Cursor cursor = downloadManager.query(query);

        if (cursor.moveToFirst()) {
            int statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if (statusCol >= 0) {
                int status = cursor.getInt(statusCol);
                if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PAUSED || status == DownloadManager.STATUS_PENDING) {
                    long total = 0;
                    long downloaded = 0;
                    int totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                    int downCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                    if (totalCol >= 0) total = cursor.getLong(totalCol);
                    if (downCol >= 0) downloaded = cursor.getLong(downCol);
                    if (total > 0) {
                        int progress = (int) (downloaded * 100 / total);
                        updateProgressBar.setProgress(progress);
                        updateProgressText.setText(progress + "%");
                    }
                    cursor.close();
                    updateProgressHandler.postDelayed(this::startUpdateProgressPolling, 1000);
                    return;
                }
            }
        }
        cursor.close();
    }

    private void installApk() {
        File updateDir = new File(getExternalFilesDir(null), "updates");
        File apkFile = new File(updateDir, "edupulse-update.apk");

        if (!apkFile.exists()) {
            updateProgressText.setText("Download failed");
            updateNowButton.setEnabled(true);
            return;
        }

        Uri apkUri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", apkFile);

        Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        installIntent.setData(apkUri);
        installIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(installIntent);
    }

    private void showLoading(String text, String subtext) {
        loadingText.setText(text);
        loadingSubtext.setText(subtext);
        loadingSubtext.setVisibility(subtext.isEmpty() ? View.GONE : View.VISIBLE);
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingOverlay.setAlpha(0f);
        loadingOverlay.animate().alpha(1f).setDuration(200).start();
    }

    private void hideLoading() {
        loadingOverlay.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> loadingOverlay.setVisibility(View.GONE))
                .start();
    }

    private void playBeep() {
        try {
            if (beepPlayer != null) {
                beepPlayer.seekTo(0);
                beepPlayer.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing beep", e);
        }
    }

    private void playNotify() {
        try {
            if (notifPlayer != null) {
                notifPlayer.seekTo(0);
                notifPlayer.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing notification sound", e);
        }
    }

    private void playSuccess() {
        try {
            if (successPlayer != null) {
                successPlayer.seekTo(0);
                successPlayer.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error playing success sound", e);
        }
    }

    private void logout() {
        prefs.edit().clear().apply();
        scannedStudentIds.clear();
        scannedStudents.clear();
        studentsCache.clear();
        subjectsMap.clear();
        classList.clear();
        currentSubjects.clear();
        photoCache.clear();
        studentAdapter.notifyDataSetChanged();
        apiBaseUrl = "";
        currentIdToken = "";
        showScreen("login");
    }

    private String getLoginErrorMessage(VolleyError error) {
        if (error instanceof NetworkError || error instanceof NoConnectionError) {
            return "Network error. Check connection.";
        } else if (error instanceof ServerError) {
            return "Invalid email or password";
        } else if (error instanceof TimeoutError) {
            return "Connection timed out";
        } else if (error instanceof AuthFailureError) {
            return "Authentication failed";
        }
        return "Login failed";
    }

    private String getConnectionErrorMessage(VolleyError error) {
        if (error instanceof NetworkError || error instanceof NoConnectionError) {
            return "Cannot reach server";
        } else if (error instanceof TimeoutError) {
            return "Connection timed out";
        }
        return "Unknown error";
    }

    private class ScanStudentAdapter extends RecyclerView.Adapter<ScanStudentAdapter.ViewHolder> {
        private List<Map<String, String>> students;
        private OnRemoveListener removeListener;

        interface OnRemoveListener {
            void onRemove(String studentId);
        }

        ScanStudentAdapter(List<Map<String, String>> students, OnRemoveListener listener) {
            this.students = students;
            this.removeListener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_student_scan, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, String> student = students.get(position);
            holder.name.setText(student.get("name"));
            String detail = student.get("class") + " \u00b7 " + student.get("section")
                    + " \u00b7 #" + student.get("id");
            holder.detail.setText(detail);

            String status = student.get("status");
            if ("pushed".equals(status)) {
                holder.statusBadge.setText("PUSHED");
                holder.statusBadge.setTextColor(Color.parseColor("#00FF88"));
                holder.statusBadge.setBackground(createBadgeBg("#0a2e1a"));
            } else if ("failed".equals(status)) {
                holder.statusBadge.setText("FAILED");
                holder.statusBadge.setTextColor(Color.parseColor("#FF3B3B"));
                holder.statusBadge.setBackground(createBadgeBg("#2d0000"));
            } else if ("late".equals(status)) {
                holder.statusBadge.setText("LATE");
                holder.statusBadge.setTextColor(Color.parseColor("#f59e0b"));
                holder.statusBadge.setBackground(createBadgeBg("#2d1f00"));
            } else if ("early".equals(status)) {
                holder.statusBadge.setText("EARLY");
                holder.statusBadge.setTextColor(Color.parseColor("#00E5FF"));
                holder.statusBadge.setBackground(createBadgeBg("#003333"));
            } else {
                holder.statusBadge.setText("PRESENT");
                holder.statusBadge.setTextColor(Color.parseColor("#00E5FF"));
                holder.statusBadge.setBackground(createBadgeBg("#003333"));
            }

            String studentId = student.get("id");
            Bitmap cached = photoCache.get(studentId);
            if (cached != null) {
                holder.photo.setImageBitmap(cached);
            } else {
                String photoUrl = student.get("photo");
                if (photoUrl != null && !photoUrl.isEmpty()) {
                    loadPhotoToImageView(photoUrl, holder.photo);
                }
            }

            holder.removeButton.setVisibility(View.VISIBLE);
            holder.removeButton.setOnClickListener(v -> {
                if (removeListener != null) {
                    removeListener.onRemove(student.get("id"));
                }
            });
        }

        @Override
        public int getItemCount() {
            return students.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView photo;
            TextView name, detail, statusBadge, removeButton;

            ViewHolder(View itemView) {
                super(itemView);
                photo = itemView.findViewById(R.id.studentPhoto);
                name = itemView.findViewById(R.id.studentName);
                detail = itemView.findViewById(R.id.studentDetail);
                statusBadge = itemView.findViewById(R.id.statusBadge);
                removeButton = itemView.findViewById(R.id.removeButton);
            }
        }
    }

    private void loadPhotoToImageView(String url, ImageView imageView) {
        ImageRequest request = new ImageRequest(url,
                bitmap -> {
                    imageView.setImageBitmap(bitmap);
                    imageView.setVisibility(View.VISIBLE);
                },
                0, 0, ImageView.ScaleType.CENTER_CROP, null, null);
        requestQueue.add(request);
    }

    private GradientDrawable createBadgeBg(String colorHex) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(100);
        drawable.setColor(Color.parseColor(colorHex));
        return drawable;
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerFcmReceiver();
        checkFcmPendingUpdate();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (fcmReceiver != null) {
            unregisterReceiver(fcmReceiver);
            fcmReceiver = null;
        }
    }

    private void registerFcmReceiver() {
        fcmReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String tag = intent.getStringExtra(EduPulseFcmService.EXTRA_TAG);
                String body = intent.getStringExtra(EduPulseFcmService.EXTRA_BODY);
                String url = intent.getStringExtra(EduPulseFcmService.EXTRA_URL);
                if (tag != null && url != null) {
                    showUpdateNotification(tag, body, url);
                }
            }
        };
        registerReceiver(fcmReceiver, new IntentFilter(EduPulseFcmService.ACTION_UPDATE_AVAILABLE),
                Context.RECEIVER_NOT_EXPORTED);
    }

    private void checkFcmPendingUpdate() {
        SharedPreferences prefs = getSharedPreferences("edupulse_fcm", MODE_PRIVATE);
        String tag = prefs.getString(EduPulseFcmService.EXTRA_TAG, "");
        String url = prefs.getString(EduPulseFcmService.EXTRA_URL, "");
        if (!tag.isEmpty() && !url.isEmpty()) {
            String dismissed = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString("dismissed_update", "");
            if (!tag.equals(dismissed)) {
                String body = prefs.getString(EduPulseFcmService.EXTRA_BODY, "");
                showUpdateNotification(tag, body, url);
                prefs.edit().remove(EduPulseFcmService.EXTRA_TAG)
                        .remove(EduPulseFcmService.EXTRA_BODY)
                        .remove(EduPulseFcmService.EXTRA_URL)
                        .apply();
            }
        }
    }

    private void registerFcmTokenWithServer() {
        if (apiBaseUrl.isEmpty()) return;
        String token = getSharedPreferences("edupulse_fcm", MODE_PRIVATE)
                .getString(EduPulseFcmService.KEY_FCM_TOKEN, "");
        if (token.isEmpty()) return;
        JSONObject body = new JSONObject();
        try {
            body.put("token", token);
            body.put("teacher_id", currentEmail != null ? currentEmail : "");
            body.put("device", "android");
        } catch (Exception ignored) {}
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST,
                apiBaseUrl + "/api/fcm/token", body,
                response -> {},
                error -> Log.e(TAG, "FCM token register failed: " + error.getMessage()));
        requestQueue.add(req);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        updateProgressHandler.removeCallbacksAndMessages(null);
        if (beepPlayer != null) beepPlayer.release();
        if (successPlayer != null) successPlayer.release();
        if (notifPlayer != null) notifPlayer.release();
        if (requestQueue != null) requestQueue.cancelAll(request -> true);
    }
}
