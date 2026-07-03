package com.edupulse.teacher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.edupulse.teacher.R;
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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "edupulse_teacher";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_API_IP = "api_ip";
    private static final String KEY_API_PORT = "api_port";
    private static final String KEY_ID_TOKEN = "id_token";
    private static final String KEY_OTA_URL = "ota_url";
    private static final String FIREBASE_API_KEY = "AIzaSyDPYjylSBhng6CRL77P0MXTUcuq7jBFnnA";
    private static final int RC_GOOGLE_SIGN_IN = 9001;

    // Screens
    private View splashContainer, loginContainer, connectContainer, mainContainer;
    private View loadingOverlay, successOverlay, scanSummarySheet;
    private TextView loadingText, loadingSubtext;

    // Login
    private TextInputEditText loginEmail, loginPassword;
    private Button loginButton;
    private TextView loginError;
    private com.google.android.gms.common.SignInButton googleSignInButton;
    private GoogleSignInClient googleSignInClient;

    // Connect
    private TextInputEditText connectIp, connectPort;
    private Button connectButton;
    private TextView connectStatus;

    // Main
    private Spinner classSpinner, sectionSpinner, subjectSpinner;
    private Button scanButton, scanFinishedButton;
    private TextView scanCountText, lastScannedText, userEmailText, scanInfoText;
    private Button disconnectButton;

    // Splash
    private TextView splashTitle;

    // Summary
    private RecyclerView summaryList;
    private TextView summaryCountText;
    private Button addMoreButton, pushButton;

    // Success
    private TextView successSubtext;
    private Button successDoneButton;

    // Update Overlay
    private View updateOverlay;
    private TextView updateTitleText, updateVersionText, updateChangelogText, updateProgressText;
    private ProgressBar updateProgressBar;
    private Button updateButton, updateLaterButton;

    // Animation
    private View successIcon;

    // Data
    private List<String> scannedStudentIds = new ArrayList<>();
    private List<Map<String, String>> scannedStudents = new ArrayList<>();
    private List<String> classList = new ArrayList<>();
    private Map<String, List<String>> subjectsMap = new HashMap<>();
    private Map<String, Map<String, String>> studentsCache = new HashMap<>();
    private List<String> currentSubjects = new ArrayList<>();
    private static final List<String> SECTIONS = Arrays.asList("A", "B", "C", "D", "E");

    private RequestQueue requestQueue;
    private SharedPreferences prefs;
    private String apiBaseUrl;
    private boolean isScanning = false;

    private StudentAdapter studentAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        requestQueue = Volley.newRequestQueue(this);

        initViews();
        checkAuthState();
    }

    private void initViews() {
        // Splash
        splashContainer = findViewById(R.id.splashContainer);
        splashTitle = findViewById(R.id.splashTitle);

        if (splashTitle != null) {
            splashTitle.post(() -> {
                ValueAnimator glitch = ValueAnimator.ofFloat(0f, 1f);
                glitch.setDuration(180);
                glitch.setRepeatCount(ValueAnimator.INFINITE);
                glitch.setRepeatMode(ValueAnimator.REVERSE);
                glitch.addUpdateListener(a -> {
                    float v = a.getAnimatedFraction();
                    splashTitle.setTranslationX((float) (Math.sin(v * 50) * 2.5f));
                    splashTitle.setAlpha(0.85f + (float) (Math.sin(v * 30) * 0.15f));
                });
                glitch.start();
            });
        }

        // Login
        loginContainer = findViewById(R.id.loginContainer);
        loginEmail = findViewById(R.id.loginEmail);
        loginPassword = findViewById(R.id.loginPassword);
        loginButton = findViewById(R.id.loginButton);
        loginError = findViewById(R.id.loginError);
        googleSignInButton = findViewById(R.id.googleSignInButton);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(FIREBASE_API_KEY)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // Connect
        connectContainer = findViewById(R.id.connectContainer);
        connectIp = findViewById(R.id.connectIp);
        connectPort = findViewById(R.id.connectPort);
        connectButton = findViewById(R.id.connectButton);
        connectStatus = findViewById(R.id.connectStatus);

        // Main
        mainContainer = findViewById(R.id.mainContainer);
        classSpinner = findViewById(R.id.classSpinner);
        sectionSpinner = findViewById(R.id.sectionSpinner);
        subjectSpinner = findViewById(R.id.subjectSpinner);
        scanButton = findViewById(R.id.scanButton);
        scanFinishedButton = findViewById(R.id.scanFinishedButton);
        scanCountText = findViewById(R.id.scanCountText);
        lastScannedText = findViewById(R.id.lastScannedText);
        userEmailText = findViewById(R.id.userEmailText);
        disconnectButton = findViewById(R.id.disconnectButton);
        scanInfoText = findViewById(R.id.scanInfoText);

        // Summary
        scanSummarySheet = findViewById(R.id.scanSummarySheet);
        summaryList = findViewById(R.id.summaryList);
        summaryCountText = findViewById(R.id.summaryCountText);
        addMoreButton = findViewById(R.id.addMoreButton);
        pushButton = findViewById(R.id.pushButton);

        // Loading
        loadingOverlay = findViewById(R.id.loadingOverlay);
        loadingText = findViewById(R.id.loadingText);
        loadingSubtext = findViewById(R.id.loadingSubtext);

        // Success
        successOverlay = findViewById(R.id.successOverlay);
        successIcon = findViewById(R.id.successIcon);
        successSubtext = findViewById(R.id.successSubtext);
        successDoneButton = findViewById(R.id.successDoneButton);

        // Update Overlay
        updateOverlay = findViewById(R.id.updateOverlay);
        updateTitleText = findViewById(R.id.updateTitleText);
        updateVersionText = findViewById(R.id.updateVersionText);
        updateChangelogText = findViewById(R.id.updateChangelogText);
        updateProgressBar = findViewById(R.id.updateProgressBar);
        updateProgressText = findViewById(R.id.updateProgressText);
        updateButton = findViewById(R.id.updateButton);
        updateLaterButton = findViewById(R.id.updateLaterButton);

        summaryList.setLayoutManager(new LinearLayoutManager(this));
        studentAdapter = new StudentAdapter(scannedStudents, this::removeStudent, apiBaseUrl);
        summaryList.setAdapter(studentAdapter);

        setupClickListeners();
        setupSpinners();
    }

    private void setupClickListeners() {
        loginButton.setOnClickListener(v -> handleLogin());
        googleSignInButton.setOnClickListener(v -> startGoogleSignIn());

        connectButton.setOnClickListener(v -> handleConnect());

        scanButton.setOnClickListener(v -> startQrScan());

        scanFinishedButton.setOnClickListener(v -> showSummary());

        addMoreButton.setOnClickListener(v -> {
            scanSummarySheet.setVisibility(View.GONE);
            scanFinishedButton.setVisibility(View.VISIBLE);
            startQrScan();
        });

        pushButton.setOnClickListener(v -> pushAttendance());

        successDoneButton.setOnClickListener(v -> {
            successOverlay.setVisibility(View.GONE);
            resetScanning();
        });

        disconnectButton.setOnClickListener(v -> logout());

        updateButton.setOnClickListener(v -> {
            String url = updateButton.getTag() != null ? updateButton.getTag().toString() : "";
            downloadUpdate(url);
        });
        updateLaterButton.setOnClickListener(v -> updateOverlay.setVisibility(View.GONE));
    }

    private void setupSpinners() {
        ArrayAdapter<String> sectionAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, SECTIONS) {
            @Override public View getView(int pos, View cv, android.view.ViewGroup p) {
                View v = super.getView(pos, cv, p);
                ((TextView)v).setTextColor(Color.parseColor("#f8fafc"));
                return v;
            }
            @Override public View getDropDownView(int pos, View cv, android.view.ViewGroup p) {
                View v = super.getDropDownView(pos, cv, p);
                ((TextView)v).setTextColor(Color.parseColor("#f8fafc"));
                ((TextView)v).setBackgroundColor(Color.parseColor("#1a1a1e"));
                return v;
            }
        };
        sectionSpinner.setAdapter(sectionAdapter);

        classSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (view instanceof TextView)
                    ((TextView)view).setTextColor(Color.parseColor("#f8fafc"));
                if (position >= 0 && position < classList.size()) {
                    String cls = classList.get(position);
                    updateSubjectsForClass(cls);
                    prefetchStudentsForClass(cls);
                }
                updateScanInfoText();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        subjectSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateScanInfoText();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void checkAuthState() {
        boolean loggedIn = prefs.getBoolean(KEY_LOGGED_IN, false);
        String ip = prefs.getString(KEY_API_IP, "");

        showSplash(true);

        new Handler().postDelayed(() -> {
            if (loggedIn && !ip.isEmpty()) {
                apiBaseUrl = "http://" + ip + ":" + prefs.getString(KEY_API_PORT, "3000");
                String email = prefs.getString(KEY_EMAIL, "");
                userEmailText.setText(email);
                showMain();
            } else if (loggedIn) {
                showConnect();
            } else {
                showLogin();
            }
        }, 2000);
    }

    // ─── SPLASH ───────────────────────────────────────────

    private void showSplash(boolean show) {
        splashContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show && splashTitle != null) {
            splashTitle.post(() -> {
                try {
                    android.graphics.Paint p = splashTitle.getPaint();
                    float w = p.measureText(splashTitle.getText().toString());
                    if (w < 1) w = splashTitle.getWidth();
                    if (w < 1) w = 200;
                    Shader shader = new LinearGradient(
                            0, 0, w, 0,
                            new int[]{Color.parseColor("#00ffff"), Color.parseColor("#7c3aed")},
                            null, Shader.TileMode.CLAMP);
                    p.setShader(shader);
                    splashTitle.invalidate();
                } catch (Exception ignored) {}
            });
        }
    }

    // ─── LOGIN ────────────────────────────────────────────

    private void showLogin() {
        showSplash(false);
        loginContainer.setVisibility(View.VISIBLE);
        connectContainer.setVisibility(View.GONE);
        mainContainer.setVisibility(View.GONE);
    }

    private void handleLogin() {
        String email = loginEmail.getText().toString().trim();
        String password = loginPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            showLoginError("Enter email and password");
            return;
        }

        showLoading("Signing in", "");

        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
            body.put("password", password);
            body.put("returnSecureToken", true);
        } catch (JSONException e) {
            hideLoading();
            showLoginError("Error creating request");
            return;
        }

        String url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + FIREBASE_API_KEY;

        JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    hideLoading();
                    try {
                        String idToken = response.getString("idToken");
                        String userEmail = response.getString("email");

                        prefs.edit()
                                .putBoolean(KEY_LOGGED_IN, true)
                                .putString(KEY_EMAIL, userEmail)
                                .putString(KEY_ID_TOKEN, idToken)
                                .apply();

                        userEmailText.setText(userEmail);
                        String ip = prefs.getString(KEY_API_IP, "");
                        if (!ip.isEmpty()) {
                            apiBaseUrl = "http://" + ip + ":" + prefs.getString(KEY_API_PORT, "3000");
                            showMain();
                        } else {
                            showConnect();
                        }
                    } catch (JSONException e) {
                        showLoginError("Invalid response from server");
                    }
                },
                error -> {
                    hideLoading();
                    String msg = "Login failed";
                    if (error.networkResponse != null && error.networkResponse.data != null) {
                        try {
                            String body2 = new String(error.networkResponse.data, "UTF-8");
                            JSONObject err = new JSONObject(body2);
                            String code = err.optJSONObject("error").optString("message", "");
                            if ("EMAIL_NOT_FOUND".equals(code)) msg = "Email not registered";
                            else if ("INVALID_PASSWORD".equals(code)) msg = "Wrong password";
                            else if ("USER_DISABLED".equals(code)) msg = "Account disabled";
                            else msg = code.replace("_", " ").toLowerCase();
                        } catch (Exception ignored) {}
                    }
                    showLoginError(msg);
                });

        req.setRetryPolicy(new DefaultRetryPolicy(10000, 1, 1));
        requestQueue.add(req);
    }

    private void showLoginError(String msg) {
        loginError.setText(msg);
        loginError.setVisibility(View.VISIBLE);
    }

    // ─── GOOGLE SIGN IN ──────────────────────────────────

    private void startGoogleSignIn() {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN);
    }

    private void handleGoogleSignInResult(Intent data) {
        showLoading("Signing in", "Google Sign-In");
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            String idToken = account.getIdToken();
            if (idToken != null) {
                exchangeGoogleToken(idToken, account.getEmail());
            } else {
                hideLoading();
                showLoginError("Google Sign-In failed - no ID token");
            }
        } catch (ApiException e) {
            hideLoading();
            showLoginError("Google Sign-In cancelled");
        }
    }

    private void exchangeGoogleToken(String idToken, String email) {
        JSONObject body = new JSONObject();
        try {
            body.put("postBody", "id_token=" + idToken + "&providerId=google.com");
            body.put("requestUri", "http://localhost");
            body.put("returnSecureToken", true);
        } catch (JSONException e) {
            hideLoading();
            showLoginError("Error creating request");
            return;
        }

        String url = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp?key=" + FIREBASE_API_KEY;

        JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    hideLoading();
                    try {
                        String userEmail = response.optString("email", email);
                        prefs.edit()
                                .putBoolean(KEY_LOGGED_IN, true)
                                .putString(KEY_EMAIL, userEmail)
                                .putString(KEY_ID_TOKEN, response.optString("idToken", ""))
                                .apply();
                        userEmailText.setText(userEmail);
                        String ip = prefs.getString(KEY_API_IP, "");
                        if (!ip.isEmpty()) {
                            apiBaseUrl = "http://" + ip + ":" + prefs.getString(KEY_API_PORT, "3000");
                            showMain();
                        } else {
                            showConnect();
                        }
                    } catch (Exception e) {
                        showLoginError("Sign in failed");
                    }
                },
                error -> {
                    hideLoading();
                    showLoginError("Google Sign-In failed");
                });

        req.setRetryPolicy(new DefaultRetryPolicy(10000, 1, 1));
        requestQueue.add(req);
    }

    // ─── CONNECT ──────────────────────────────────────────

    private void showConnect() {
        showSplash(false);
        loginContainer.setVisibility(View.GONE);
        connectContainer.setVisibility(View.VISIBLE);
        mainContainer.setVisibility(View.GONE);

        String savedIp = prefs.getString(KEY_API_IP, "");
        String savedPort = prefs.getString(KEY_API_PORT, "3000");
        if (!savedIp.isEmpty()) {
            connectIp.setText(savedIp);
            connectPort.setText(savedPort);
        }
    }

    private void handleConnect() {
        String ip = connectIp.getText().toString().trim();
        String portStr = connectPort.getText().toString().trim();

        if (ip.isEmpty()) {
            connectStatus.setText("Enter server IP");
            connectStatus.setTextColor(Color.parseColor("#ef4444"));
            return;
        }

        String finalPort = portStr.isEmpty() ? "3000" : portStr;

        String testUrl = "http://" + ip + ":" + finalPort + "/api/health";
        showLoading("Connecting", "Testing connection to " + ip);

        StringRequest req = new StringRequest(Request.Method.GET, testUrl,
                response -> {
                    hideLoading();
                    apiBaseUrl = "http://" + ip + ":" + finalPort;
                    prefs.edit()
                            .putString(KEY_API_IP, ip)
                            .putString(KEY_API_PORT, finalPort)
                            .apply();
                    connectStatus.setText("✓ Connected");
                    connectStatus.setTextColor(Color.parseColor("#22c55e"));
                    new Handler().postDelayed(this::showMain, 600);
                },
                error -> {
                    hideLoading();
                    connectStatus.setText("✕ Connection failed. Check IP and port.");
                    connectStatus.setTextColor(Color.parseColor("#ef4444"));
                });

        req.setRetryPolicy(new DefaultRetryPolicy(5000, 1, 1));
        requestQueue.add(req);
    }

    // ─── MAIN ─────────────────────────────────────────────

    private void showMain() {
        showSplash(false);
        loginContainer.setVisibility(View.GONE);
        connectContainer.setVisibility(View.GONE);
        mainContainer.setVisibility(View.VISIBLE);
        lastScannedText.setVisibility(View.GONE);

        loadSubjects();
        checkForUpdate();

        // Pulse animation on scan button
        scanButton.post(() -> startPulseAnimation(scanButton));
    }

    private void startPulseAnimation(View v) {
        if (v == null) return;
        ObjectAnimator anim = ObjectAnimator.ofFloat(v, "scaleX", 1f, 1.05f, 1f);
        anim.setDuration(1500);
        anim.setRepeatCount(ObjectAnimator.INFINITE);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.start();
        ObjectAnimator animY = ObjectAnimator.ofFloat(v, "scaleY", 1f, 1.05f, 1f);
        animY.setDuration(1500);
        animY.setRepeatCount(ObjectAnimator.INFINITE);
        animY.setInterpolator(new DecelerateInterpolator());
        animY.start();
    }

    private void loadSubjects() {
        if (apiBaseUrl == null || apiBaseUrl.isEmpty()) return;

        String url = apiBaseUrl + "/api/subjects";

        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        if (response.optBoolean("success", false)) {
                            JSONObject data = response.optJSONObject("data");
                            if (data == null) data = new JSONObject();

                            subjectsMap.clear();
                            classList.clear();

                            Iterator<String> keys = data.keys();
                            while (keys.hasNext()) {
                                String cls = keys.next();
                                JSONArray arr = data.optJSONArray(cls);
                                List<String> subs = new ArrayList<>();
                                if (arr != null) {
                                    for (int i = 0; i < arr.length(); i++) {
                                        subs.add(arr.getString(i));
                                    }
                                }
                                subjectsMap.put(cls, subs);
                                classList.add(cls);
                            }
                            updateClassSpinner();
                        }
                    } catch (JSONException e) {
                        Log.e("EduPulse", "Error parsing subjects", e);
                    }
                },
                error -> Log.e("EduPulse", "Failed to load subjects", error));

        requestQueue.add(req);
    }

    private void updateClassSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, classList) {
            @Override public View getView(int pos, View cv, android.view.ViewGroup p) {
                View v = super.getView(pos, cv, p);
                ((TextView)v).setTextColor(Color.parseColor("#f8fafc"));
                return v;
            }
            @Override public View getDropDownView(int pos, View cv, android.view.ViewGroup p) {
                View v = super.getDropDownView(pos, cv, p);
                ((TextView)v).setTextColor(Color.parseColor("#f8fafc"));
                ((TextView)v).setBackgroundColor(Color.parseColor("#1a1a1e"));
                return v;
            }
        };
        classSpinner.setAdapter(adapter);
    }

    private void updateSubjectsForClass(String cls) {
        currentSubjects = subjectsMap.containsKey(cls) ? subjectsMap.get(cls) : new ArrayList<>();
        List<String> display = new ArrayList<>(currentSubjects);
        if (display.isEmpty()) display.add("No subjects");

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, display) {
            @Override public View getView(int pos, View cv, android.view.ViewGroup p) {
                View v = super.getView(pos, cv, p);
                ((TextView)v).setTextColor(Color.parseColor("#f8fafc"));
                return v;
            }
            @Override public View getDropDownView(int pos, View cv, android.view.ViewGroup p) {
                View v = super.getDropDownView(pos, cv, p);
                ((TextView)v).setTextColor(Color.parseColor("#f8fafc"));
                ((TextView)v).setBackgroundColor(Color.parseColor("#1a1a1e"));
                return v;
            }
        };
        subjectSpinner.setAdapter(adapter);
        updateScanInfoText();
    }

    private void prefetchStudentsForClass(String cls) {
        if (apiBaseUrl == null || apiBaseUrl.isEmpty()) return;
        String url = apiBaseUrl + "/api/students?class=" + cls;

        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    if (response.optBoolean("success", false)) {
                        JSONArray data = response.optJSONArray("data");
                        if (data == null) return;
                        studentsCache.clear();
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject s = data.optJSONObject(i);
                            if (s != null) {
                                String id = s.optString("id", "");
                                if (!id.isEmpty()) {
                                    Map<String, String> m = new HashMap<>();
                                    m.put("id", id);
                                    m.put("name", s.optString("name", ""));
                                    m.put("class", s.optString("class", ""));
                                    m.put("section", s.optString("section", ""));
                                    m.put("photo", s.optString("photo", ""));
                                    studentsCache.put(id, m);
                                }
                            }
                        }
                    }
                },
                error -> {});
        req.setRetryPolicy(new DefaultRetryPolicy(5000, 1, 1));
        requestQueue.add(req);
    }

    private void updateScanInfoText() {
        String cls = classSpinner.getSelectedItem() != null ? classSpinner.getSelectedItem().toString() : "";
        String sub = subjectSpinner.getSelectedItem() != null ? subjectSpinner.getSelectedItem().toString() : "";
        if (!cls.isEmpty() && !sub.isEmpty() && !sub.equals("No subjects")) {
            scanInfoText.setText("Class " + cls + "  ·  " + sub);
            scanInfoText.setVisibility(View.VISIBLE);
        } else if (!cls.isEmpty()) {
            scanInfoText.setText("Class " + cls);
            scanInfoText.setVisibility(View.VISIBLE);
        } else {
            scanInfoText.setVisibility(View.GONE);
        }
    }

    // ─── QR SCAN ──────────────────────────────────────────

    private void startQrScan() {
        if (apiBaseUrl == null || apiBaseUrl.isEmpty()) {
            Toast.makeText(this, "Not connected to server", Toast.LENGTH_SHORT).show();
            return;
        }

        isScanning = true;
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("Scan student QR code");
        integrator.setCameraId(0);
        integrator.setBeepEnabled(true);
        integrator.setBarcodeImageEnabled(false);
        integrator.setOrientationLocked(true);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            if (resultCode == RESULT_OK && data != null) {
                handleGoogleSignInResult(data);
            }
            return;
        }
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            if (result.getContents() != null) {
                handleScanResult(result.getContents());
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void handleScanResult(String qrData) {
        try {
            JSONObject json = new JSONObject(qrData);
            String studentId = json.optString("student_id", "");
            if (studentId.isEmpty()) {
                Toast.makeText(this, "Invalid QR code", Toast.LENGTH_SHORT).show();
                if (isScanning) startQrScan();
                return;
            }

            // Check if already scanned
            if (scannedStudentIds.contains(studentId)) {
                Toast.makeText(this, "Already scanned", Toast.LENGTH_SHORT).show();
                if (isScanning) startQrScan();
                return;
            }

            String selectedClass = classSpinner.getSelectedItem() != null ? classSpinner.getSelectedItem().toString() : "";

            // Try cache first
            Map<String, String> cached = studentsCache.get(studentId);
            if (cached != null) {
                String studentClass = cached.get("class");
                if (!selectedClass.isEmpty() && !selectedClass.equals(studentClass)) {
                    Toast.makeText(this, "Student not in class " + selectedClass, Toast.LENGTH_LONG).show();
                    if (isScanning) startQrScan();
                    return;
                }
                addStudentToScanList(cached);
                return;
            }

            fetchStudentInfo(studentId, selectedClass);
        } catch (JSONException e) {
            Toast.makeText(this, "Invalid QR data", Toast.LENGTH_SHORT).show();
            if (isScanning) startQrScan();
        }
    }

    private void fetchStudentInfo(String studentId, String selectedClass) {
        String url = apiBaseUrl + "/api/students/" + studentId;

        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    if (response.optBoolean("success", false)) {
                        JSONObject student = response.optJSONObject("data") != null
                                ? response.optJSONObject("data")
                                : response;
                        String name = student.optString("name", "Unknown");
                        String cls = student.optString("class", "");
                        String section = student.optString("section", "");
                        String photo = student.optString("photo", "");

                        // Validate class
                        if (!selectedClass.isEmpty() && !selectedClass.equals(cls)) {
                            Toast.makeText(this, "Student not in class " + selectedClass, Toast.LENGTH_LONG).show();
                            if (isScanning) startQrScan();
                            return;
                        }

                        // If photo empty, try Firebase fallback
                        if (photo == null || photo.isEmpty()) {
                            fetchPhotoFromFirebase(studentId, name, cls, section, selectedClass);
                            return;
                        }

                        addStudentToScanList(studentId, name, cls, section, photo);
                    } else {
                        Toast.makeText(this, "Student not found", Toast.LENGTH_SHORT).show();
                        if (isScanning) startQrScan();
                    }
                },
                error -> {
                    Toast.makeText(this, "Failed to fetch student", Toast.LENGTH_SHORT).show();
                    if (isScanning) startQrScan();
                });

        req.setRetryPolicy(new DefaultRetryPolicy(5000, 1, 1));
        requestQueue.add(req);
    }

    private void fetchPhotoFromFirebase(String studentId, String name, String cls, String section, String selectedClass) {
        String fbUrl = "https://edupulse-attendance-qr-default-rtdb.asia-southeast1.firebasedatabase.app/students/"
                + studentId + ".json";

        JsonObjectRequest req = new JsonObjectRequest(Request.Method.GET, fbUrl, null,
                response -> {
                    String photo = response.optString("photo", "");
                    addStudentToScanList(studentId, name, cls, section, photo);
                },
                error -> addStudentToScanList(studentId, name, cls, section, ""));
        requestQueue.add(req);
    }

    private void addStudentToScanList(Map<String, String> cached) {
        String id = cached.get("id");
        scannedStudentIds.add(id);
        scannedStudents.add(new HashMap<>(cached));
        updateScanDisplay();
        Toast.makeText(this, "✓ " + cached.get("name"), Toast.LENGTH_SHORT).show();
        if (isScanning) new Handler().postDelayed(this::startQrScan, 800);
    }

    private void addStudentToScanList(String studentId, String name, String cls, String section, String photo) {
        Map<String, String> entry = new HashMap<>();
        entry.put("id", studentId);
        entry.put("name", name);
        entry.put("class", cls);
        entry.put("section", section);
        entry.put("photo", photo != null ? photo : "");
        scannedStudentIds.add(studentId);
        scannedStudents.add(entry);

        updateScanDisplay();
        Toast.makeText(this, "✓ " + name, Toast.LENGTH_SHORT).show();
        if (isScanning) new Handler().postDelayed(this::startQrScan, 800);
    }

    private void updateScanDisplay() {
        int count = scannedStudents.size();
        scanCountText.setText(String.valueOf(count));

        if (count > 0) {
            showSummary();
        }
    }

    // ─── SUMMARY ──────────────────────────────────────────

    private void showSummary() {
        summaryCountText.setText(scannedStudents.size() + " students scanned");
        studentAdapter.notifyDataSetChanged();
        scanSummarySheet.setVisibility(View.VISIBLE);
    }

    private void removeStudent(int position) {
        if (position >= 0 && position < scannedStudents.size()) {
            scannedStudentIds.remove(scannedStudents.get(position).get("id"));
            scannedStudents.remove(position);
            updateScanDisplay();
            studentAdapter.notifyDataSetChanged();
            summaryCountText.setText(scannedStudents.size() + " students scanned");
            if (scannedStudents.isEmpty()) {
                scanSummarySheet.setVisibility(View.GONE);
                scanFinishedButton.setVisibility(View.GONE);
            }
        }
    }

    // ─── PUSH ─────────────────────────────────────────────

    private void pushAttendance() {
        if (scannedStudents.isEmpty()) return;

        scanSummarySheet.setVisibility(View.GONE);
        showLoading("Pushing Attendance", "0/" + scannedStudents.size());

        String selectedClass = classSpinner.getSelectedItem() != null ? classSpinner.getSelectedItem().toString() : "";
        String selectedSection = sectionSpinner.getSelectedItem() != null ? sectionSpinner.getSelectedItem().toString() : "";
        String selectedSubject = subjectSpinner.getSelectedItem() != null ? subjectSpinner.getSelectedItem().toString() : "";

        pushNext(0, selectedClass, selectedSection, selectedSubject);
    }

    private void pushNext(int index, String cls, String section, String subject) {
        if (index >= scannedStudents.size()) {
            hideLoading();
            showSuccess();
            return;
        }

        Map<String, String> student = scannedStudents.get(index);
        String url = apiBaseUrl + "/api/attendance/scan";

        JSONObject body = new JSONObject();
        try {
            body.put("student_id", student.get("id"));
            body.put("class", cls);
            body.put("section", section);
            body.put("subject", subject);
        } catch (JSONException e) {
            pushNext(index + 1, cls, section, subject);
            return;
        }

        int finalIndex = index;
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, url, body,
                response -> {
                    loadingSubtext.setText((finalIndex + 1) + "/" + scannedStudents.size() + " - " + student.get("name"));
                    String status = response.optJSONObject("attendance") != null
                            ? response.optJSONObject("attendance").optString("status", "")
                            : "";
                    if (!status.isEmpty()) {
                        scannedStudents.get(finalIndex).put("status", status);
                    }
                    pushNext(finalIndex + 1, cls, section, subject);
                },
                error -> {
                    loadingSubtext.setText((finalIndex + 1) + "/" + scannedStudents.size() + " - " + student.get("name"));
                    pushNext(finalIndex + 1, cls, section, subject);
                });

        req.setRetryPolicy(new DefaultRetryPolicy(5000, 0, 1));
        requestQueue.add(req);
    }

    // ─── SUCCESS ──────────────────────────────────────────

    private void showSuccess() {
        int count = scannedStudents.size();
        successSubtext.setText(count + " attendance records pushed successfully");

        // Animate success icon
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(successIcon, "scaleX", 0f, 1.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(successIcon, "scaleY", 0f, 1.2f, 1f);
        scaleX.setDuration(600);
        scaleY.setDuration(600);
        scaleX.setInterpolator(new DecelerateInterpolator());
        scaleY.setInterpolator(new DecelerateInterpolator());
        scaleX.start();
        scaleY.start();

        successOverlay.setVisibility(View.VISIBLE);

        // Confetti burst!
        ConfettiView confetti = findViewById(R.id.confettiView);
        if (confetti != null) confetti.burst();
    }

    // ─── LOADING OVERLAY ─────────────────────────────────

    private void showLoading(String text, String subtext) {
        loadingText.setText(text);
        loadingSubtext.setText(subtext);
        loadingOverlay.setVisibility(View.VISIBLE);
        loadingOverlay.setAlpha(0f);
        loadingOverlay.animate().alpha(1f).setDuration(200).start();
    }

    private void hideLoading() {
        loadingOverlay.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> loadingOverlay.setVisibility(View.GONE)).start();
    }

    // ─── UTILITY ─────────────────────────────────────────

    private void resetScanning() {
        scannedStudents.clear();
        scannedStudentIds.clear();
        isScanning = false;
        scanFinishedButton.setVisibility(View.GONE);
        lastScannedText.setVisibility(View.GONE);
        scanCountText.setText("0");
    }

    private void logout() {
        prefs.edit()
                .putBoolean(KEY_LOGGED_IN, false)
                .apply();
        resetScanning();
        showLogin();
    }

    // ─── OTA UPDATE ──────────────────────────────────────

    private void checkForUpdate() {
        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/ShadowMan2010/edupulse-teacher/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setRequestProperty("Accept", "application/json");
                int code = conn.getResponseCode();
                if (code != 200) return;

                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject release = new JSONObject(sb.toString());
                int remoteVersion = parseVersion(release.optString("tag_name", "v1"));
                int currentVersion = getPackageManager()
                        .getPackageInfo(getPackageName(), 0).versionCode;

                if (remoteVersion > currentVersion) {
                    String apkUrl = "";
                    JSONArray assets = release.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            if (asset.optString("name", "").endsWith(".apk")) {
                                apkUrl = asset.optString("browser_download_url", "");
                                break;
                            }
                        }
                    }
                    String changelog = release.optString("body", "New version available");
                    String versionName = release.optString("tag_name", "v" + remoteVersion);

                    String finalApkUrl = apkUrl;
                    String finalChangelog = changelog;
                    runOnUiThread(() -> showUpdateOverlay(versionName, finalChangelog, finalApkUrl));
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void showUpdateOverlay(String version, String changelog, String apkUrl) {
        updateVersionText.setText(version);
        updateChangelogText.setText(changelog);
        updateButton.setTag(apkUrl);
        updateOverlay.setVisibility(View.VISIBLE);
    }

    private void showUpdateProgress(int percent) {
        updateProgressBar.setVisibility(View.VISIBLE);
        updateProgressText.setVisibility(View.VISIBLE);
        updateProgressBar.setProgress(percent);
        updateProgressText.setText(percent + "%");
    }

    private int parseVersion(String tag) {
        try {
            return Integer.parseInt(tag.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }

    private void downloadUpdate(String apkUrl) {
        if (apkUrl == null || apkUrl.isEmpty()) {
            Toast.makeText(this, "No APK found in release", Toast.LENGTH_SHORT).show();
            return;
        }
        updateButton.setEnabled(false);
        updateButton.setText("DOWNLOADING...");
        showUpdateProgress(0);

        new Thread(() -> {
            try {
                URL url = new URL(apkUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.connect();
                int fileLength = conn.getContentLength();

                File dir = new File(getExternalCacheDir(), "updates");
                dir.mkdirs();
                File apkFile = new File(dir, "EduPulseTeacher.apk");

                try (InputStream is = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(apkFile)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    long total = 0;
                    while ((count = is.read(buffer)) > 0) {
                        fos.write(buffer, 0, count);
                        total += count;
                        if (fileLength > 0) {
                            final int pct = (int) (total * 100 / fileLength);
                            runOnUiThread(() -> showUpdateProgress(pct));
                        }
                    }
                }

                runOnUiThread(() -> {
                    updateOverlay.setVisibility(View.GONE);
                    installApk(apkFile);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    updateButton.setEnabled(true);
                    updateButton.setText("UPDATE NOW");
                    Toast.makeText(this, "Download failed", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void installApk(File apkFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Intent intent = new Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                Toast.makeText(this, "Enable install from this source", Toast.LENGTH_LONG).show();
                return;
            }
        }

        Uri uri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", apkFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    // ─── STUDENT ADAPTER (inner class) ──────────────────

    private static class StudentAdapter extends RecyclerView.Adapter<StudentAdapter.ViewHolder> {
        private final List<Map<String, String>> students;
        private final OnRemoveListener listener;
        private final String baseUrl;

        interface OnRemoveListener {
            void onRemove(int position);
        }

        StudentAdapter(List<Map<String, String>> students, OnRemoveListener listener, String baseUrl) {
            this.students = students;
            this.listener = listener;
            this.baseUrl = baseUrl != null ? baseUrl : "";
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            android.view.View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_student_scan, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Map<String, String> s = students.get(position);
            holder.name.setText(s.get("name"));
            String detail = "Class " + s.get("class");
            if (s.containsKey("section") && !s.get("section").isEmpty()) {
                detail += " · Section " + s.get("section");
            }
            holder.detail.setText(detail);

            String photo = s.get("photo");
            if (photo != null && !photo.isEmpty()) {
                if (photo.startsWith("http") || photo.startsWith("data:image")) {
                    loadPhoto(holder.photo, photo);
                } else if (photo.startsWith("/")) {
                    loadPhoto(holder.photo, baseUrl + photo);
                } else {
                    holder.photo.setImageDrawable(null);
                }
            } else {
                holder.photo.setImageDrawable(null);
            }

            // Status badge
            String status = s.get("status");
            if (status != null && !status.isEmpty()) {
                holder.statusBadge.setVisibility(View.VISIBLE);
                holder.statusBadge.setText(status.toUpperCase());
                switch (status) {
                    case "late":
                        holder.statusBadge.setTextColor(Color.parseColor("#f59e0b"));
                        holder.statusBadge.setBackgroundColor(Color.parseColor("#2d1f00"));
                        break;
                    case "early":
                        holder.statusBadge.setTextColor(Color.parseColor("#00ffff"));
                        holder.statusBadge.setBackgroundColor(Color.parseColor("#003333"));
                        break;
                    case "present":
                        holder.statusBadge.setTextColor(Color.parseColor("#22c55e"));
                        holder.statusBadge.setBackgroundColor(Color.parseColor("#0a2e1a"));
                        break;
                    default:
                        holder.statusBadge.setTextColor(Color.parseColor("#94a3b8"));
                        holder.statusBadge.setBackgroundColor(Color.parseColor("#1a1a1e"));
                }
            } else {
                holder.statusBadge.setVisibility(View.GONE);
            }
        }

        private void loadPhoto(ImageView iv, String url) {
            if (url.startsWith("data:image")) {
                byte[] decoded = android.util.Base64.decode(url.substring(url.indexOf(",") + 1), android.util.Base64.DEFAULT);
                Bitmap bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                if (bmp != null) {
                    iv.setImageBitmap(bmp);
                    return;
                }
            }
            try {
                java.net.URL u = new java.net.URL(url);
                android.os.AsyncTask.execute(() -> {
                    try {
                        Bitmap bmp = BitmapFactory.decodeStream(u.openStream());
                        if (bmp != null) {
                            iv.post(() -> iv.setImageBitmap(bmp));
                        }
                    } catch (Exception ignored) {}
                });
            } catch (Exception ignored) {}
        }

        @Override
        public int getItemCount() {
            return students.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView photo;
            TextView name, detail, removeBtn, statusBadge;
            ViewHolder(android.view.View itemView) {
                super(itemView);
                photo = itemView.findViewById(R.id.studentPhoto);
                name = itemView.findViewById(R.id.studentName);
                detail = itemView.findViewById(R.id.studentDetail);
                statusBadge = itemView.findViewById(R.id.statusBadge);
                removeBtn = itemView.findViewById(R.id.removeButton);
                removeBtn.setOnClickListener(v -> {
                    int pos = getAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        listener.onRemove(pos);
                    }
                });
            }
        }
    }
}
