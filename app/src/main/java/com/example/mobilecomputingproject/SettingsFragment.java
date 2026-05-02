package com.example.mobilecomputingproject;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "FoodAheadPrefs";
    private static final String KEY_LIGHT_MODE = "light_mode";

    private Switch lightModeSwitch;
    private Button clearAllDataButton;

    private SharedPreferences sharedPreferences;
    private RecipeDbHelper dbHelper;

    public SettingsFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        dbHelper = new RecipeDbHelper(requireContext());

        lightModeSwitch = view.findViewById(R.id.light_mode_switch);
        clearAllDataButton = view.findViewById(R.id.clear_all_data_button);

        loadSavedSettings();
        setupListeners();
    }

    private void loadSavedSettings() {
        boolean lightMode = sharedPreferences.getBoolean(KEY_LIGHT_MODE, false);
        lightModeSwitch.setChecked(lightMode);
    }

    private void setupListeners() {
        lightModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit()
                    .putBoolean(KEY_LIGHT_MODE, isChecked)
                    .apply();

            AppCompatDelegate.setDefaultNightMode(
                    isChecked
                            ? AppCompatDelegate.MODE_NIGHT_NO
                            : AppCompatDelegate.MODE_NIGHT_YES
            );
        });

        clearAllDataButton.setOnClickListener(v -> showClearDataConfirmation());
    }

    private void showClearDataConfirmation() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Clear All Data")
                .setMessage("This will permanently delete all recipes and meal planner entries. Are you sure?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    dbHelper.clearAllData();
                    Toast.makeText(getContext(), "All data cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}