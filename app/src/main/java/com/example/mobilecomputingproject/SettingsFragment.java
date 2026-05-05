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

import java.util.Calendar;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "FoodAheadPrefs";
    private static final String KEY_LIGHT_MODE = "light_mode";
    private static final String KEY_DAILY_TARGETS = "daily_targets";
    private static final String KEY_PERIOD_START = "macro_period_start";
    private static final String KEY_CURRENT_CALORIES = "current_calories";
    private static final String KEY_CURRENT_PROTEIN = "current_protein";
    private static final String KEY_CURRENT_CARBS = "current_carbs";
    private static final String KEY_CURRENT_FAT = "current_fat";
    private static final String KEY_HAS_LAST_RESULT = "has_last_result";
    private Switch lightModeSwitch;
    private Switch weeklyTargetsSwitch;
    private Button clearAllDataButton;
    private SharedPreferences sharedPreferences;
    private RecipeDbHelper dbHelper;
    private boolean suppressWeeklyTargetsListener = false;

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
        weeklyTargetsSwitch = view.findViewById(R.id.weekly_targets_switch);
        clearAllDataButton = view.findViewById(R.id.clear_all_data_button);

        loadSavedSettings();
        setupListeners();
    }

    private void loadSavedSettings() {
        boolean lightMode = sharedPreferences.getBoolean(KEY_LIGHT_MODE, false);

        // Default is daily targets, so dailyTargets defaults to true.
        boolean dailyTargets = sharedPreferences.getBoolean(KEY_DAILY_TARGETS, true);

        // Switch shows weekly targets, so it is checked when dailyTargets is false.
        boolean weeklyTargets = !dailyTargets;

        lightModeSwitch.setChecked(lightMode);

        suppressWeeklyTargetsListener = true;
        weeklyTargetsSwitch.setChecked(weeklyTargets);
        suppressWeeklyTargetsListener = false;
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

        weeklyTargetsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressWeeklyTargetsListener) {
                return;
            }

            showTargetTypeConfirmation(isChecked);
        });

        clearAllDataButton.setOnClickListener(v -> showClearDataConfirmation());
    }

    private void showTargetTypeConfirmation(boolean enableWeeklyTargets) {
        boolean previousDailyTargets = sharedPreferences.getBoolean(KEY_DAILY_TARGETS, true);
        boolean previousWeeklyTargets = !previousDailyTargets;

        new AlertDialog.Builder(requireContext())
                .setTitle(enableWeeklyTargets ? "Enable Weekly Targets?" : "Enable Daily Targets?")
                .setMessage("Changing target type will reset current macro progress and clear the previous result display.")
                .setPositiveButton("Continue", (dialog, which) -> {
                    boolean newDailyTargets = !enableWeeklyTargets;

                    sharedPreferences.edit()
                            .putBoolean(KEY_DAILY_TARGETS, newDailyTargets)
                            .putLong(KEY_PERIOD_START, getCurrentPeriodStartMillis(newDailyTargets))
                            .putBoolean(KEY_HAS_LAST_RESULT, false)
                            .putFloat(KEY_CURRENT_CALORIES, 0f)
                            .putFloat(KEY_CURRENT_PROTEIN, 0f)
                            .putFloat(KEY_CURRENT_CARBS, 0f)
                            .putFloat(KEY_CURRENT_FAT, 0f)
                            .apply();

                    Toast.makeText(
                            getContext(),
                            enableWeeklyTargets ? "Weekly targets enabled" : "Daily targets enabled",
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    suppressWeeklyTargetsListener = true;
                    weeklyTargetsSwitch.setChecked(previousWeeklyTargets);
                    suppressWeeklyTargetsListener = false;
                })
                .show();
    }

    private void showClearDataConfirmation() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Clear All Data")
                .setMessage("This will permanently delete all recipes and meal planner entries. Are you sure?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    dbHelper.clearAllData();

                    sharedPreferences.edit()
                            .putFloat(KEY_CURRENT_CALORIES, 0f)
                            .putFloat(KEY_CURRENT_PROTEIN, 0f)
                            .putFloat(KEY_CURRENT_CARBS, 0f)
                            .putFloat(KEY_CURRENT_FAT, 0f)
                            .putBoolean(KEY_HAS_LAST_RESULT, false)
                            .apply();

                    Toast.makeText(getContext(), "All data cleared", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private long getCurrentPeriodStartMillis(boolean dailyTargets) {
        if (dailyTargets) {
            return getTodayMidnightMillis();
        } else {
            return getCurrentMondayMidnightMillis();
        }
    }

    private long getTodayMidnightMillis() {
        Calendar calendar = Calendar.getInstance();

        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar.getTimeInMillis();
    }

    private long getCurrentMondayMidnightMillis() {
        Calendar calendar = Calendar.getInstance();

        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int daysSinceMonday = dayOfWeek - Calendar.MONDAY;

        if (daysSinceMonday < 0) {
            daysSinceMonday += 7;
        }

        calendar.add(Calendar.DAY_OF_MONTH, -daysSinceMonday);

        return calendar.getTimeInMillis();
    }
}