package com.example.delaylauncherimprove;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
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
    private TextView tvDelayValue, tvRiepilogo;
    private Button btnMinus, btnPlus;

    private SharedPreferences prefs;
    private int currentDelay = 20;
    private List<AppInfo> installedApps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("DelayLauncherPrefs", Context.MODE_PRIVATE);
        currentDelay = prefs.getInt("delay_seconds", 20);

        spinnerApp1 = findViewById(R.id.spinner_app1);
        spinnerApp2 = findViewById(R.id.spinner_app2);
        spinnerLauncher = findViewById(R.id.spinner_launcher);
        tvDelayValue = findViewById(R.id.tv_delay_value);
        tvRiepilogo = findViewById(R.id.tv_riepilogo);
        btnMinus = findViewById(R.id.btn_minus);
        btnPlus = findViewById(R.id.btn_plus);

        tvDelayValue.setText(String.valueOf(currentDelay));

        recuperaApplicazioniInstallate();

        configuraSpinner(spinnerApp1, "app1_package");
        configuraSpinner(spinnerApp2, "app2_package");
        configuraSpinner(spinnerLauncher, "launcher_package");

        btnMinus.setOnClickListener(v -> {
            if (currentDelay > 3) {
                currentDelay--;
                tvDelayValue.setText(String.valueOf(currentDelay));
                saveDelay();
                updateRiepilogo();
            } else {
                Toast.makeText(MainActivity.this, "Minimo 3 secondi richiesti per proteggere l'avvio!", Toast.LENGTH_SHORT).show();
            }
        });

        btnPlus.setOnClickListener(v -> {
            currentDelay++;
            tvDelayValue.setText(String.valueOf(currentDelay));
            saveDelay();
            updateRiepilogo();
        });

        updateRiepilogo();
    }

    private void recuperaApplicazioniInstallate() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        installedApps.clear();
        // Slot vuoto usando il costruttore senza icona
        installedApps.add(new AppInfo("Nessuna Applicazione Selezionata", ""));

        for (ApplicationInfo app : apps) {
            if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                String nomeLabel = app.loadLabel(pm).toString();
                Drawable iconaApp = app.loadIcon(pm); // Recupera l'icona reale dell'app
                installedApps.add(new AppInfo(nomeLabel, app.packageName, iconaApp));
            }
        }

        Collections.sort(installedApps, (a, b) -> a.getLabel().compareToIgnoreCase(b.getLabel()));
    }

    private void configuraSpinner(Spinner spinner, String prefKey) {
        List<String> labels = new ArrayList<>();
        for (AppInfo app : installedApps) {
            labels.add(app.getLabel());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        String savedPkg = prefs.getString(prefKey, "");
        if (!savedPkg.isEmpty()) {
            for (int i = 0; i < installedApps.size(); i++) {
                if (installedApps.get(i).getPackageName().equals(savedPkg)) {
                    spinner.setSelection(i);
                    break;
                }
            }
        }

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                AppInfo selectedApp = installedApps.get(position);
                prefs.edit().putString(prefKey, selectedApp.getPackageName()).apply();
                updateRiepilogo();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void saveDelay() {
        prefs.edit().putInt("delay_seconds", currentDelay).apply();
    }

    private void updateRiepilogo() {
        String app1 = spinnerApp1.getSelectedItem() != null ? spinnerApp1.getSelectedItem().toString() : "Nessuna";
        String app2 = spinnerApp2.getSelectedItem() != null ? spinnerApp2.getSelectedItem().toString() : "Nessuna";
        String launcher = spinnerLauncher.getSelectedItem() != null ? spinnerLauncher.getSelectedItem().toString() : "Non impostato";

        String testoRiepilogo = "CONFIGURAZIONE ATTUALE:\n" +
                "⏱️ Delay: " + currentDelay + " secondi (Freno attivo)\n" +
                "🚀 1° Background: " + app1 + "\n" +
                "🎵 2° Background: " + app2 + "\n" +
                "🏠 Launcher Finale: " + launcher;

        tvRiepilogo.setText(testoRiepilogo);
    }
}
