package com.example.delaylauncher;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerApp1, spinnerApp2, spinnerLauncher;
    private Button btnMinus, btnPlus, btnStart;
    private TextView tvDelayValue, tvRiepilogoApp1, tvRiepilogoApp2, tvRiepilogoDelayNum, tvRiepilogoStatoTesto, tvRiepilogoStatoSub, tvRiepilogoStatoTitolo;
    private FrameLayout countdownOverlay;
    private TextView tvCountdownNumber;

    private int delaySeconds = 5;
    private List<AppInfo> installedApps = new ArrayList<>();
    private List<AppInfo> launcherApps = new ArrayList<>();
    
    private boolean isUpdatingSpinners = false; // Flag anti-loop per la mutua esclusione

    static class AppInfo {
        String label;
        String packageName;
        AppInfo(String label, String packageName) {
            this.label = label;
            this.packageName = packageName;
        }
        @NonNull
        @Override
        public String toString() { return label; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inizializzazione Elementi Layout
        spinnerApp1 = findViewById(R.id.spinnerApp1);
        spinnerApp2 = findViewById(R.id.spinnerApp2);
        spinnerLauncher = findViewById(R.id.spinnerLauncher);
        btnMinus = findViewById(R.id.btnMinus);
        btnPlus = findViewById(R.id.btnPlus);
        btnStart = findViewById(R.id.btnStart);
        tvDelayValue = findViewById(R.id.tvDelayValue);
        
        tvRiepilogoApp1 = findViewById(R.id.tvRiepilogoApp1);
        tvRiepilogoApp2 = findViewById(R.id.tvRiepilogoApp2);
        tvRiepilogoDelayNum = findViewById(R.id.tvRiepilogoDelayNum);
        tvRiepilogoStatoTesto = findViewById(R.id.tvRiepilogoStatoTesto);
        tvRiepilogoStatoSub = findViewById(R.id.tvRiepilogoStatoSub);
        tvRiepilogoStatoTitolo = findViewById(R.id.tvRiepilogoStatoTitolo);
        
        countdownOverlay = findViewById(R.id.countdownOverlay);
        tvCountdownNumber = findViewById(R.id.tvCountdownNumber);

        // Caricamento Applicazioni di Sistema
        loadInstalledApps();

        // Configurazione degli Adapter con gestione contrasto scuro (Punto 10)
        setupSpinnerAdapters();

        // Gestione pulsanti Delay
        tvDelayValue.setText(delaySeconds + "s");
        tvRiepilogoDelayNum.setText(String.valueOf(delaySeconds));

        btnMinus.setOnClickListener(v -> {
            if (delaySeconds > 1) {
                delaySeconds--;
                tvDelayValue.setText(delaySeconds + "s");
                tvRiepilogoDelayNum.setText(String.valueOf(delaySeconds));
            }
        });

        btnPlus.setOnClickListener(v -> {
            if (delaySeconds < 60) {
                delaySeconds++;
                tvDelayValue.setText(delaySeconds + "s");
                tvRiepilogoDelayNum.setText(String.valueOf(delaySeconds));
            }
        });

        // Logica dei Listener dei tre Spinner con Mutua Esclusione Rigorosa (Punto 11 & 12)
        setupSpinnerListeners();

        // Gestione Tasto Avvio e Countdown (Punto 1 & 4)
        btnStart.setOnClickListener(v -> startExecutionSequence());

        // Validazione iniziale Stato (Punto 11)
        aggiornaStatoConfigurazione();
    }

    private void loadInstalledApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        
        installedApps.clear();
        launcherApps.clear();
        
        installedApps.add(new AppInfo("Nessuna", ""));
        launcherApps.add(new AppInfo("Nessuna", ""));

        List<AppInfo> tempApps = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                String label = app.loadLabel(pm).toString();
                AppInfo appInfo = new AppInfo(label, app.packageName);
                tempApps.add(appInfo);
                
                // Se è un launcher di sistema potenziale, lo aggiungiamo anche alla lista launcher
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.setPackage(app.packageName);
                if (pm.queryIntentActivities(intent, 0).size() > 0 || app.packageName.contains("launcher")) {
                    launcherApps.add(appInfo);
                }
            }
        }

        // Ordinamento alfabetico delle liste per ordine visivo pulito
        Collections.sort(tempApps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        installedApps.addAll(tempApps);
        
        if(launcherApps.size() == 1) { // Fallback se non rileva launcher specifici
            launcherApps.addAll(tempApps);
        }
    }

    private void setupSpinnerAdapters() {
        // Adapter personalizzato per forzare testi chiari e drop-down scuri su ogni display/radio (Punto 10)
        ArrayAdapter<AppInfo> appAdapter = new ArrayAdapter<AppInfo>(this, android.R.layout.simple_spinner_item, installedApps) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.WHITE);
                return view;
            }
            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setBackgroundColor(Color.parseColor("#1A1A1A"));
                view.setTextColor(Color.WHITE);
                return view;
            }
        };
        appAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        ArrayAdapter<AppInfo> launcherAdapter = new ArrayAdapter<AppInfo>(this, android.R.layout.simple_spinner_item, launcherApps) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextColor(Color.WHITE);
                return view;
            }
            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setBackgroundColor(Color.parseColor("#1A1A1A"));
                view.setTextColor(Color.WHITE);
                return view;
            }
        };
        launcherAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinnerApp1.setAdapter(appAdapter);
        spinnerApp2.setAdapter(appAdapter);
        spinnerLauncher.setAdapter(launcherAdapter);
    }

    private void setupSpinnerListeners() {
        spinnerApp1.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingSpinners) return;
                AppInfo selectedApp1 = (AppInfo) spinnerApp1.getSelectedItem();
                AppInfo selectedApp2 = (AppInfo) spinnerApp2.getSelectedItem();

                // MUTUA ESCLUSIONE RIGOROSA (Punto 12)
                if (!selectedApp1.packageName.isEmpty() && selectedApp1.packageName.equals(selectedApp2.packageName)) {
                    isUpdatingSpinners = true;
                    spinnerApp2.setSelection(0); // Resetta APP 2 a "Nessuna"
                    tvRiepilogoApp2.setText("Nessuna");
                    isUpdatingSpinners = false;
                }
                
                tvRiepilogoApp1.setText(selectedApp1.label);
                aggiornaStatoConfigurazione();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerApp2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingSpinners) return;
                AppInfo selectedApp1 = (AppInfo) spinnerApp1.getSelectedItem();
                AppInfo selectedApp2 = (AppInfo) spinnerApp2.getSelectedItem();

                // MUTUA ESCLUSIONE RIGOROSA (Punto 12)
                if (!selectedApp2.packageName.isEmpty() && selectedApp2.packageName.equals(selectedApp1.packageName)) {
                    isUpdatingSpinners = true;
                    spinnerApp1.setSelection(0); // Resetta APP 1 a "Nessuna"
                    tvRiepilogoApp1.setText("Nessuna");
                    isUpdatingSpinners = false;
                }

                tvRiepilogoApp2.setText(selectedApp2.label);
                aggiornaStatoConfigurazione();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerLauncher.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                aggiornaStatoConfigurazione();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // LOGICA DI CONTROLLO COLORI E STATI (Punto 11)
    private void aggiornaStatoConfigurazione() {
        AppInfo launcher = (AppInfo) spinnerLauncher.getSelectedItem();
        AppInfo app1 = (AppInfo) spinnerApp1.getSelectedItem();
        AppInfo app2 = (AppInfo) spinnerApp2.getSelectedItem();

        // CASO 1: Manca la configurazione della Home (Stato Giallo iniziale - Attenzione)
        if (launcher == null || launcher.packageName.isEmpty()) {
            tvRiepilogoStatoTitolo.setTextColor(Color.parseColor("#FFCC00"));
            tvRiepilogoStatoTesto.setText("Scegli Home");
            tvRiepilogoStatoTesto.setTextColor(Color.parseColor("#FFCC00"));
            tvRiepilogoStatoSub.setText("Attesa config");
            btnStart.setEnabled(false);
            btnStart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#445522"))); // Tasto opaco disabilitato
            return;
        }

        // CASO 2: Manca la selezione di almeno un'applicazione da avviare
        if (app1.packageName.isEmpty() && app2.packageName.isEmpty()) {
            tvRiepilogoStatoTitolo.setTextColor(Color.parseColor("#FFCC00"));
            tvRiepilogoStatoTesto.setText("Configura App");
            tvRiepilogoStatoTesto.setTextColor(Color.parseColor("#FFCC00"));
            tvRiepilogoStatoSub.setText("Seleziona App1 o 2");
            btnStart.setEnabled(false);
            btnStart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#445522")));
            return;
        }

        // CASO 3: Tutto pronto e configurato correttamente (Stato Verde - Operativo)
        tvRiepilogoStatoTitolo.setTextColor(Color.parseColor("#A6FF00"));
        tvRiepilogoStatoTesto.setText("Pronto");
        tvRiepilogoStatoTesto.setTextColor(Color.parseColor("#A6FF00"));
        tvRiepilogoStatoSub.setText("Sistema pronto");
        btnStart.setEnabled(true);
        btnStart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#A6FF00"))); // Ripristina Verde Acido splendente
    }

    private void startExecutionSequence() {
        final AppInfo app1 = (AppInfo) spinnerApp1.getSelectedItem();
        final AppInfo app2 = (AppInfo) spinnerApp2.getSelectedItem();
        final AppInfo launcher = (AppInfo) spinnerLauncher.getSelectedItem();

        // Mostra overlay di countdown circolare (Punto 1)
        countdownOverlay.setVisibility(View.VISIBLE);
        
        new CountDownTimer(delaySeconds * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secondsLeft = (int) (millisUntilFinished / 1000) + 1;
                tvCountdownNumber.setText(String.valueOf(secondsLeft));
            }

            @Override
            public void onFinish() {
                countdownOverlay.setVisibility(View.GONE);
                PackageManager pm = getPackageManager();

                // 1. Avvia Prima Applicazione (se impostata)
                if (!app1.packageName.isEmpty()) {
                    Intent intent1 = pm.getLaunchIntentForPackage(app1.packageName);
                    if (intent1 != null) {
                        startActivity(intent1);
                    }
                }

                // 2. Avvia Seconda Applicazione (se impostata)
                if (!app2.packageName.isEmpty()) {
                    Intent intent2 = pm.getLaunchIntentForPackage(app2.packageName);
                    if (intent2 != null) {
                        startActivity(intent2);
                    }
                }

                // 3. Infine stabilizza lanciando la Home/Launcher predefinita
                if (!launcher.packageName.isEmpty()) {
                    Intent intentLauncher = pm.getLaunchIntentForPackage(launcher.packageName);
                    if (intentLauncher != null) {
                        startActivity(intentLauncher);
                    }
                }
                
                finish(); // Chiude l'activity del launcher di delay liberando memoria
            }
        }.start();
    }
}
