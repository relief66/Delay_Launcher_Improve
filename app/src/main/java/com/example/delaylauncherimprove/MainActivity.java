package com.example.delaylauncherimprove;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private LinearLayout layoutConfigurazione;
    private RelativeLayout layoutCountdown;
    private Spinner spinnerApp1, spinnerApp2, spinnerLauncher;
    private TextView tvDelayValue, tvCountdownBig;
    private ProgressBar progressCircolare;
    private TextView tvColApplicazioni, tvColDelay, tvColStato;
    private Button btnMinus, btnPlus, btnAvvio;

    private SharedPreferences prefs;
    private int currentDelay = 20;
    
    private final List<AppInfo> installedApps = new ArrayList<>();
    private final List<AppInfo> launcherApps = new ArrayList<>();

    private final Handler countdownHandler = new Handler();
    private int tempoRimanente = 0;
    private boolean isCountingDown = false;

    private MediaPlayer playerLoop = null;

    private final Handler repeatUpdateHandler = new Handler();
    private boolean mAutoIncrement = false;
    private boolean mAutoDecrement = false;
    private static final long REPEAT_DELAY = 100;

    private final Runnable incrementRunnable = new Runnable() {
        @Override
        public void run() {
            if (mAutoIncrement && currentDelay < 60) {
                currentDelay++;
                tvDelayValue.setText(String.valueOf(currentDelay));
                saveDelay();
                updateTabellaRiepilogo();
                repeatUpdateHandler.postDelayed(this, REPEAT_DELAY);
            }
        }
    };

    private final Runnable decrementRunnable = new Runnable() {
        @Override
        public void run() {
            if (mAutoDecrement && currentDelay > 3) {
                currentDelay--;
                tvDelayValue.setText(String.valueOf(currentDelay));
                saveDelay();
                updateTabellaRiepilogo();
                repeatUpdateHandler.postDelayed(this, REPEAT_DELAY);
            }
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("DelayLauncherPrefs", Context.MODE_PRIVATE);
        currentDelay = prefs.getInt("delay_seconds", 20);

        // Inizializzazione Layouts Doppi
        layoutConfigurazione = findViewById(R.id.layout_configurazione);
        layoutCountdown = findViewById(R.id.layout_countdown);

        // Inizializzazione Viste Configurazione
        spinnerApp1 = findViewById(R.id.spinner_app1);
        spinnerApp2 = findViewById(R.id.spinner_app2);
        spinnerLauncher = findViewById(R.id.spinner_launcher);
        tvDelayValue = findViewById(R.id.tv_delay_value);
        tvColApplicazioni = findViewById(R.id.tv_col_applicazioni);
        tvColDelay = findViewById(R.id.tv_col_delay);
        tvColStato = findViewById(R.id.tv_col_stato);
        btnMinus = findViewById(R.id.btn_minus);
        btnPlus = findViewById(R.id.btn_plus);
        btnAvvio = findViewById(R.id.btn_avvio);

        // Inizializzazione Viste Immersive Countdown
        tvCountdownBig = findViewById(R.id.tv_countdown_big);
        progressCircolare = findViewById(R.id.progress_circolare);

        tvDelayValue.setText(String.valueOf(currentDelay));

        // SETUP ADATTATORI (Inizialmente vuoti per mostrare subito la UI)
        spinnerApp1.setAdapter(new CustomAppAdapter(this, installedApps, spinnerApp1, 1));
        spinnerApp2.setAdapter(new CustomAppAdapter(this, installedApps, spinnerApp2, 2));
        spinnerLauncher.setAdapter(new CustomAppAdapter(this, launcherApps, spinnerLauncher, 3));

        // ELIMINAZIONE SCHERMATA NERA: Scansione spostata in un Thread asincrono
        new Thread(() -> {
            recuperaEFlitraApplicazioni();
            runOnUiThread(() -> {
                // Notifica gli spinner che i dati sono pronti con controlli di sicurezza null
                if (spinnerApp1.getAdapter() != null) ((CustomAppAdapter) spinnerApp1.getAdapter()).notifyDataSetChanged();
                if (spinnerApp2.getAdapter() != null) ((CustomAppAdapter) spinnerApp2.getAdapter()).notifyDataSetChanged();
                if (spinnerLauncher.getAdapter() != null) ((CustomAppAdapter) spinnerLauncher.getAdapter()).notifyDataSetChanged();

                String salvatoApp1 = prefs.getString("app1_package", "");
                String salvatoApp2 = prefs.getString("app2_package", "");
                String salvatoLauncher = prefs.getString("launcher_package", "DEFAULT_QUICKSTEP");

                if (salvatoLauncher.equals("DEFAULT_QUICKSTEP")) {
                    String quickstepPackage = trovaPackageLauncherDaLabel("Quickstep");
                    if (!quickstepPackage.isEmpty()) {
                        salvatoLauncher = quickstepPackage;
                        prefs.edit().putString("launcher_package", quickstepPackage).apply();
                    } else {
                        salvatoLauncher = "";
                        prefs.edit().putString("launcher_package", "").apply();
                    }
                }

                setSpinnerSelection(spinnerApp1, salvatoApp1);
                setSpinnerSelection(spinnerApp2, salvatoApp2);
                setSpinnerSelection(spinnerLauncher, salvatoLauncher);

                setupSpinnerListener(spinnerApp1, "app1_package");
                setupSpinnerListener(spinnerApp2, "app2_package");
                setupSpinnerListener(spinnerLauncher, "launcher_package");

                updateTabellaRiepilogo();
            });
        }).start();

        // Listener Annullamento Rapido: Tocca lo schermo ovunque per fermare tutto
        layoutCountdown.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                fermaSequenza();
                return true;
            }
            return false;
        });

        // Controlli Incremento/Decremento Delay
        btnMinus.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                mAutoDecrement = true;
                repeatUpdateHandler.post(decrementRunnable);
                return true;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mAutoDecrement = false;
                return true;
            }
            return false;
        });

        btnPlus.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                mAutoIncrement = true;
                repeatUpdateHandler.post(incrementRunnable);
                return true;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mAutoIncrement = false;
                return true;
            }
            return false;
        });

        btnAvvio.setOnClickListener(v -> eseguiSequenzaLancio());
    }

    private void recuperaEFlitraApplicazioni() {
        PackageManager pm = getPackageManager();
        String myPackageName = getPackageName();

        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> homeApps = pm.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        Set<String> launcherPackageSet = new HashSet<>();
        
        for (ResolveInfo info : homeApps) {
            if (info.activityInfo != null) {
                launcherPackageSet.add(info.activityInfo.packageName);
            }
        }

        List<ApplicationInfo> allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        installedApps.clear();
        launcherApps.clear();

        Drawable defaultIcon = ContextCompat.getDrawable(this, android.R.drawable.ic_menu_close_clear_cancel);
        installedApps.add(new AppInfo("Nessuna Applicazione", "", defaultIcon));
        launcherApps.add(new AppInfo("Non impostato (Scegli Home)", "", defaultIcon));

        List<AppInfo> tempNormalApps = new ArrayList<>();
        List<AppInfo> tempLauncherApps = new ArrayList<>();

        for (ApplicationInfo app : allApps) {
            if (app.packageName.equals(myPackageName)) continue;

            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                String label = app.loadLabel(pm).toString();
                Drawable icon = app.loadIcon(pm);
                AppInfo appObject = new AppInfo(label, app.packageName, icon);

                if (launcherPackageSet.contains(app.packageName)) {
                    tempLauncherApps.add(appObject);
                } else {
                    tempNormalApps.add(appObject);
                }
            }
        }

        Collections.sort(tempNormalApps, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));
        Collections.sort(tempLauncherApps, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));

        installedApps.addAll(tempNormalApps);
        launcherApps.addAll(tempLauncherApps);
    }

    private String trovaPackageLauncherDaLabel(String targetLabel) {
        for (AppInfo app : launcherApps) {
            if (app.getLabel().toLowerCase().contains(targetLabel.toLowerCase())) return app.getPackageName();
        }
        return "";
    }

    private void setSpinnerSelection(Spinner spinner, String packageName) {
        CustomAppAdapter adapter = (CustomAppAdapter) spinner.getAdapter();
        if (adapter == null) return;
        if (packageName.isEmpty()) {
            spinner.setSelection(0);
            return;
        }
        for (int i = 0; i < adapter.getCount(); i++) {
            AppInfo app = adapter.getItem(i);
            if (app != null && app.getPackageName().
