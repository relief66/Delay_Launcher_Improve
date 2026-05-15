package com.example.delaylauncherimprove;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Spinner spinnerApp1, spinnerApp2, spinnerLauncher;
    private TextView tvDelayValue;
    private TextView tvColApplicazioni, tvColDelay, tvColStato;
    private Button btnMinus, btnPlus, btnAvvio;

    private SharedPreferences prefs;
    private int currentDelay = 20;
    private List<AppInfo> installedApps = new ArrayList<>();
    private boolean isUpdatingSpinners = false;

    // Gestione Auto-Repeat scorrimento continuo secondi
    private final Handler repeatUpdateHandler = new Handler();
    private boolean mAutoIncrement = false;
    private boolean mAutoDecrement = false;
    private static final long REPEAT_DELAY = 100; // Millisecondi tra i passi

    private final Runnable incrementRunnable = new Runnable() {
        @Override
        public void run() {
            if (mAutoIncrement) {
                if (currentDelay < 60) {
                    currentDelay++;
                    tvDelayValue.setText(String.valueOf(currentDelay));
                    saveDelay();
                    updateTabellaRiepilogo();
                    repeatUpdateHandler.postDelayed(this, REPEAT_DELAY);
                } else {
                    mAutoIncrement = false;
                    Toast.makeText(MainActivity.this, "Massimo 60 secondi consentiti!", Toast.LENGTH_SHORT).show();
                }
            }
        }
    };

    private final Runnable decrementRunnable = new Runnable() {
        @Override
        public void run() {
            if (mAutoDecrement) {
                if (currentDelay > 3) {
                    currentDelay--;
                    tvDelayValue.setText(String.valueOf(currentDelay));
                    saveDelay();
                    updateTabellaRiepilogo();
                    repeatUpdateHandler.postDelayed(this, REPEAT_DELAY);
                } else {
                    mAutoDecrement = false;
                    Toast.makeText(MainActivity.this, "Minimo 3 secondi richiesti!", Toast.LENGTH_SHORT).show();
                }
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

        // Collegamento componenti grafici
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

        tvDelayValue.setText(String.valueOf(currentDelay));

        recuperaApplicazioniInstallate();

        // Popolamento iniziale
        popolaSpinner(spinnerApp1, installedApps);
        popolaSpinner(spinnerApp2, installedApps);
        popolaSpinner(spinnerLauncher, installedApps);

        // Ripristino dati salvati
        setSpinnerSelection(spinnerApp1, prefs.getString("app1_package", ""));
        setSpinnerSelection(spinnerApp2, prefs.getString("app2_package", ""));
        setSpinnerSelection(spinnerLauncher, prefs.getString("launcher_package", ""));

        // Listener per il salvataggio e filtri incrociati
        setupSpinnerListener(spinnerApp1, "app1_package");
        setupSpinnerListener(spinnerApp2, "app2_package");
        setupSpinnerListener(spinnerLauncher, "launcher_package");

        // Touch continuo tasto MENO
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

        // Touch continuo tasto PIÙ
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

        // Click sul tasto AVVIO manuale
        btnAvvio.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Avvio manuale sequenza...", Toast.LENGTH_SHORT).show();
            // Qui si interfaccerà con il servizio di countdown che scatta al boot
        });

        aggiornaFiltriEsclusione();
        updateTabellaRiepilogo();
    }

    private void recuperaApplicazioniInstallate() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        installedApps.clear();
        installedApps.add(new AppInfo("Nessuna Applicazione", ""));

        List<AppInfo> temporanea = new ArrayList<>();
        for (ApplicationInfo app : apps) {
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                String nomeLabel = app.loadLabel(pm).toString();
                Drawable iconaApp = app.loadIcon(pm);
                temporanea.add(new AppInfo(nomeLabel, app.packageName, iconaApp));
            }
        }
        Collections.sort(temporanea, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));
        installedApps.addAll(temporanea);
    }

    private void popolaSpinner(Spinner spinner, List<AppInfo> listaApps) {
        List<String> labels = new ArrayList<>();
        for (AppInfo app : listaApps) {
            labels.add(app.getLabel());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void setSpinnerSelection(Spinner spinner, String packageName) {
        if (packageName.isEmpty()) {
            spinner.setSelection(0);
            return;
        }
        for (int i = 0; i < installedApps.size(); i++) {
            if (installedApps.get(i).getPackageName().equals(packageName)) {
                for (int j = 0; j < spinner.getCount(); j++) {
                    if (spinner.getItemAtPosition(j).toString().equals(installedApps.get(i).getLabel())) {
                        spinner.setSelection(j);
                        return;
                    }
                }
            }
        }
    }

    private void setupSpinnerListener(Spinner spinner, String prefKey) {
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isUpdatingSpinners) return;

                String selectedLabel = parent.getItemAtPosition(position).toString();
                String targetPackage = "";
                for (AppInfo app : installedApps) {
                    if (app.getLabel().equals(selectedLabel)) {
                        targetPackage = app.getPackageName();
                        break;
                    }
                }

                prefs.edit().putString(prefKey, targetPackage).apply();
                
                if (spinner == spinnerApp1 || spinner == spinnerApp2) {
                    aggiornaFiltriEsclusione();
                }
                updateTabellaRiepilogo();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void aggiornaFiltriEsclusione() {
        isUpdatingSpinners = true;

        String pkg1 = prefs.getString("app1_package", "");
        String pkg2 = prefs.getString("app2_package", "");

        List<AppInfo> listaPerApp2 = new ArrayList<>();
        for (AppInfo app : installedApps) {
            if (app.getPackageName().isEmpty() || !app.getPackageName().equals(pkg1)) {
                listaPerApp2.add(app);
            }
        }
        popolaSpinner(spinnerApp2, listaPerApp2);
        setSpinnerSelection(spinnerApp2, pkg2);

        List<AppInfo> listaPerApp1 = new ArrayList<>();
        for (AppInfo app : installedApps) {
            if (app.getPackageName().isEmpty() || !app.getPackageName().equals(pkg2)) {
                listaPerApp1.add(app);
            }
        }
        popolaSpinner(spinnerApp1, listaPerApp1);
        setSpinnerSelection(spinnerApp1, pkg1);

        isUpdatingSpinners = false;
    }

    private void saveDelay() {
        prefs.edit().putInt("delay_seconds", currentDelay).apply();
    }

    private void updateTabellaRiepilogo() {
        String pkg1 = prefs.getString("app1_package", "");
        String pkg2 = prefs.getString("app2_package", "");
        String pkgLauncher = prefs.getString("launcher_package", "");

        String app1 = "Nessuna";
        String app2 = "Nessuna";
        String launcher = "Non impostato";

        for (AppInfo app : installedApps) {
            if (!app.getPackageName().isEmpty()) {
                if (app.getPackageName().equals(pkg1)) app1 = app.getLabel();
                if (app.getPackageName().equals(pkg2)) app2 = app.getLabel();
                if (app.getPackageName().equals(pkgLauncher)) launcher = app.getLabel();
            }
        }

        // Limitiamo la lunghezza del testo per non rompere le colonne in auto
        if (app1.length() > 18) app1 = app1.substring(0, 16) + "..";
        if (app2.length() > 18) app2 = app2.substring(0, 16) + "..";
        if (launcher.length() > 18) launcher = launcher.substring(0, 16) + "..";

        String testoApplicazioni = "App 1:\n" + app1 + "\nApp 2:\n" + app2 + "\nLauncher:\n" + launcher;
        tvColApplicazioni.setText(testoApplicazioni);

        String testoDelay = currentDelay + " secondi\n\nCountdown\ncon animazione";
        tvColDelay.setText(testoDelay);

        if (pkgLauncher.isEmpty()) {
            tvColStato.setText("⚠ Incompleto\n\nScegli un\nLauncher");
        } else {
            tvColStato.setText("✓ Pronto\n\nTutto pronto\nper l'avvio");
        }
    }
}
