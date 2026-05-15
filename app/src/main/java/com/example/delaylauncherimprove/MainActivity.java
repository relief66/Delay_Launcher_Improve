package com.example.delaylauncherimprove;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSpinner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private AppCompatSpinner spinnerApp1, spinnerApp2, spinnerLauncher;
    private TextView tvDelayValue;
    private TextView tvRiepilogoSequenza, tvRiepilogoApplicazioni, tvRiepilogoDelay, tvRiepilogoStato;
    
    private SharedPreferences prefs;
    private int currentDelay = 20; // Valore di default

    private List<AppInfo> allInstalledApps = new ArrayList<>();
    private List<AppInfo> systemLaunchers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("DelayLauncherPrefs", Context.MODE_PRIVATE);
        currentDelay = prefs.getInt("delay_seconds", 20);

        // Inizializzazione Widget UI
        spinnerApp1 = findViewById(R.id.spinner_app1);
        spinnerApp2 = findViewById(R.id.spinner_app2);
        spinnerLauncher = findViewById(R.id.spinner_launcher);
        tvDelayValue = findViewById(R.id.tv_delay_value);
        
        // Widget del Box di Riepilogo
        tvRiepilogoSequenza = findViewById(R.id.tv_riepilogo_sequenza);
        tvRiepilogoApplicazioni = findViewById(R.id.tv_riepilogo_applicazioni);
        tvRiepilogoDelay = findViewById(R.id.tv_riepilogo_delay);
        tvRiepilogoStato = findViewById(R.id.tv_riepilogo_stato);

        Button btnMinus = findViewById(R.id.btn_minus);
        Button btnPlus = findViewById(R.id.btn_plus);
        Button btnAvvio = findViewById(R.id.btn_avvio);

        tvDelayValue.setText(String.valueOf(currentDelay));

        // Gestione Click per decrementare il Delay (Freno di sicurezza a 3 secondi minimo)
        btnMinus.setOnClickListener(v -> {
            if (currentDelay > 3) { // Tagliato l'accesso a 1 e 2 secondi
                currentDelay--;
                tvDelayValue.setText(String.valueOf(currentDelay));
                saveDelay();
                updateRiepilogo();
            } else {
                Toast.makeText(MainActivity.this, "Minimo 3 secondi richiesti per proteggere l'avvio!", Toast.LENGTH_SHORT).show();
            }
        });

        // Gestione Click per incrementare il Delay
        btnPlus.setOnClickListener(v -> {
            if (currentDelay < 99) {
                currentDelay++;
                tvDelayValue.setText(String.valueOf(currentDelay));
                saveDelay();
                updateRiepilogo();
            }
        });

        // Pulsante di AVVIO manuale del Countdown
        btnAvvio.setOnClickListener(v -> {
            String launcherPkg = prefs.getString("launcher_package", "");
            if (launcherPkg.isEmpty()) {
                Toast.makeText(MainActivity.this, "Seleziona obbligatoriamente un Launcher!", Toast.LENGTH_LONG).show();
            } else {
                Intent intent = new Intent(MainActivity.this, CountdownActivity.class);
                intent.putExtra("auto_start", false); // Indica l'avvio manuale dell'utente
                startActivity(intent);
            }
        });

        // Carica la lista delle applicazioni installate asincronamente
        new LoadAppsTask().execute();
    }

    private void saveDelay() {
        prefs.edit().putInt("delay_seconds", currentDelay).apply();
    }

    // Task asincrono per non bloccare la UI dello Snapdragon durate la lettura dei pacchetti
    private class LoadAppsTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            PackageManager pm = getPackageManager();
            
            // 1. Recupero di TUTTE le app installate per gli Spinner 1 e 2
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            allInstalledApps.add(new AppInfo("[ NESSUNA APP ]", "", null));

            for (ApplicationInfo app : apps) {
                // Escludiamo le app di sistema senza interfaccia di lancio per pulizia
                if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                    String label = app.loadLabel(pm).toString();
                    Drawable icon = app.loadIcon(pm);
                    allInstalledApps.add(new AppInfo(label, app.packageName, icon));
                }
            }

            // Ordinamento alfabetico delle app (saltando il primo elemento "Nessuna")
            Collections.sort(allInstalledApps.subList(1, allInstalledApps.size()), 
                (a1, a2) -> a1.getLabel().compareToIgnoreCase(a2.getLabel()));

            // 2. Recupero selettivo di tutti i Launcher di sistema installati
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_HOME);
            List<ResolveInfo> launchers = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);

            for (ResolveInfo ri : launchers) {
                String label = ri.loadLabel(pm).toString();
                String pkgName = ri.activityInfo.packageName;
                Drawable icon = ri.loadIcon(pm);
                systemLaunchers.add(new AppInfo(label, pkgName, icon));
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            setupSpinners();
        }
    }

    private void setupSpinners() {
        // Setup Adapter per App 1 e App 2
        ArrayAdapter<AppInfo> appAdapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_spinner_item, allInstalledApps);
        appAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        spinnerApp1.setAdapter(appAdapter);
        spinnerApp2.setAdapter(appAdapter);

        // Setup Adapter per i Launcher
        ArrayAdapter<AppInfo> launcherAdapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_spinner_item, systemLaunchers);
        launcherAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        spinnerLauncher.setAdapter(launcherAdapter);

        // Ripristino delle selezioni salvate in precedenza
        restoreSpinnerSelections();

        // Listener per salvare i cambiamenti dello Spinner App 1
        spinnerApp1.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                AppInfo selected = allInstalledApps.get(position);
                prefs.edit().putString("app1_package", selected.getPackageName()).apply();
                prefs.edit().putString("app1_name", selected.getLabel()).apply();
                updateRiepilogo();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Listener per salvare i cambiamenti dello Spinner App 2
        spinnerApp2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                AppInfo selected = allInstalledApps.get(position);
                prefs.edit().putString("app2_package", selected.getPackageName()).apply();
                prefs.edit().putString("app2_name", selected.getLabel()).apply();
                updateRiepilogo();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Listener per salvare i cambiamenti dello Spinner Launcher
        spinnerLauncher.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!systemLaunchers.isEmpty()) {
                    AppInfo selected = systemLaunchers.get(position);
                    prefs.edit().putString("launcher_package", selected.getPackageName()).apply();
                    prefs.edit().putString("launcher_name", selected.getLabel()).apply();
                    updateRiepilogo();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Primo aggiornamento grafico completo del box inferiore
        updateRiepilogo();
    }

    private void restoreSpinnerSelections() {
        String app1Pkg = prefs.getString("app1_package", "");
        String app2Pkg = prefs.getString("app2_package", "");
        String launcherPkg = prefs.getString("launcher_package", "");

        // Seleziona la riga corretta per App 1
        for (int i = 0; i < allInstalledApps.size(); i++) {
            if (allInstalledApps.get(i).getPackageName().equals(app1Pkg)) {
                spinnerApp1.setSelection(i);
                break;
            }
        }

        // Seleziona la riga corretta per App 2
        for (int i = 0; i < allInstalledApps.size(); i++) {
            if (allInstalledApps.get(i).getPackageName().equals(app2Pkg)) {
                spinnerApp2.setSelection(i);
                break;
            }
        }

        // Seleziona la riga corretta per il Launcher
        for (int i = 0; i < systemLaunchers.size(); i++) {
            if (systemLaunchers.get(i).getPackageName().equals(launcherPkg)) {
                spinnerLauncher.setSelection(i);
                break;
            }
        }
    }

    private void updateRiepilogo() {
        String app1Name = prefs.getString("app1_name", "[ Nessuna ]");
        String app2Name = prefs.getString("app2_name", "[ Nessuna ]");
        String launcherName = prefs.getString("launcher_name", "NON SELEZIONATO");
        
        // 1. Aggiornamento Colonna Sequenza Dinamica
        StringBuilder sequenza = new StringBuilder();
        int step = 1;
        if (!prefs.getString("app1_package", "").isEmpty()) {
            sequenza.append(step++).append(". Avvia App 1\n");
        }
        if (!prefs.getString("app2_package", "").isEmpty()) {
            sequenza.append(step++).append(". Avvia App 2\n");
        }
        sequenza.append(step++).append(". Attendi ").append(currentDelay).append("s\n");
        sequenza.append(step).append(". Avvia Launcher");
        tvRiepilogoSequenza.setText(sequenza.toString());

        // 2. Aggiornamento Colonna Applicazioni
        String appSummary = "App 1: " + app1Name + "\nApp 2: " + app2Name + "\nLauncher: " + launcherName;
        tvRiepilogoApplicazioni.setText(appSummary);

        // 3. Aggiornamento Colonna Delay
        String delaySummary = currentDelay + " secondi\nCountdown attivo";
        tvRiepilogoDelay.setText(delaySummary);

        // 4. Aggiornamento Colonna Stato (Validazione Bloccante se manca il Launcher)
        String launcherPkg = prefs.getString("launcher_package", "");
        if (launcherPkg.isEmpty()) {
            tvRiepilogoStato.setText("❌ ERRORE\nManca Launcher!");
            tvRiepilogoStato.setTextColor(0xFFFF0000); // Rosso
        } else {
            tvRiepilogoStato.setText("✓ PRONTO\nConfig. valida");
            tvRiepilogoStato.setTextColor(0xFF76FF03); // Verde Acido
        }
    }
}
