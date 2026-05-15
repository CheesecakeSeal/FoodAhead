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

/**
 * SettingsFragment displays and manages app-wide settings.
 *
 * Current settings include:
 * - Light mode / dark mode
 * - Daily targets / weekly targets
 * - Clearing saved app data
 *
 * SharedPreferences is used for simple settings because these values are small key-value pairs.
 * SQLite is used only for structured app data such as recipes and meal planner entries.
 */
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

    /*
     * sharedPreferences stores settings and macro progress values.
     * dbHelper clears SQLite data when the user chooses Clear All Data.
     */
    private SharedPreferences sharedPreferences;
    private RecipeDbHelper dbHelper;

    /*
     * Prevents the weekly target confirmation dialog from appearing while the switch is being set
     * programmatically during initial loading or when cancelling a change.
     */
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

    /**
     * Loads saved settings from SharedPreferences and updates the switches.
     */
    private void loadSavedSettings() {
        boolean lightMode = sharedPreferences.getBoolean(KEY_LIGHT_MODE, false);

        boolean dailyTargets = sharedPreferences.getBoolean(KEY_DAILY_TARGETS, true);

        boolean weeklyTargets = !dailyTargets;

        lightModeSwitch.setChecked(lightMode);

        /*
         * Set the switch without triggering its confirmation dialog.
         */
        suppressWeeklyTargetsListener = true;
        weeklyTargetsSwitch.setChecked(weeklyTargets);
        suppressWeeklyTargetsListener = false;
    }

    /**
     * Sets up all button/switch listeners for the Settings screen.
     */
    private void setupListeners() {
        /*
         * Save and apply the light/dark mode setting immediately.
         *
         * AppCompatDelegate recreates the activity as needed so the theme change is applied across
         * the app.
         */
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

        /*
         * Weekly Targets switch:
         * - ON means weekly targets
         * - OFF means daily targets
         *
         * A confirmation dialog is shown because switching target type resets current macro
         * progress.
         */
        weeklyTargetsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressWeeklyTargetsListener) {
                return;
            }

            showTargetTypeConfirmation(isChecked);
        });

        /*
         * Clear recipes, planner entries, and macro progress after confirmation.
         */
        clearAllDataButton.setOnClickListener(v -> showClearDataConfirmation());
    }

    /**
     * Shows a confirmation dialog before changing between daily and weekly targets.
     *
     * Changing the target type resets current macro progress because a daily total should not be
     * mixed with a weekly total.
     */
    private void showTargetTypeConfirmation(boolean enableWeeklyTargets) {
        /*
         * Store previous state so the switch can be restored if the user cancels.
         */
        boolean previousDailyTargets = sharedPreferences.getBoolean(KEY_DAILY_TARGETS, true);
        boolean previousWeeklyTargets = !previousDailyTargets;

        new AlertDialog.Builder(requireContext())
                .setTitle(enableWeeklyTargets ? "Enable Weekly Targets?" : "Enable Daily Targets?")
                .setMessage("Changing target type will reset current macro progress and clear the previous result display.")
                .setPositiveButton("Continue", (dialog, which) -> {
                    /*
                     * The saved preference stores dailyTargets, while the switch represents weekly
                     * targets. Therefore the saved value is the inverse of the switch value.
                     */
                    boolean newDailyTargets = !enableWeeklyTargets;

                    sharedPreferences.edit()
                            .putBoolean(KEY_DAILY_TARGETS, newDailyTargets)

                            /*
                             * Reset the period start so HomeFragment begins tracking from the
                             * correct daily/weekly boundary.
                             */
                            .putLong(KEY_PERIOD_START, getCurrentPeriodStartMillis(newDailyTargets))

                            /*
                             * Clear previous result display and current progress to avoid mixing
                             * daily and weekly data.
                             */
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
                    /*
                     * Restore the switch to its previous state without triggering this listener
                     * again.
                     */
                    suppressWeeklyTargetsListener = true;
                    weeklyTargetsSwitch.setChecked(previousWeeklyTargets);
                    suppressWeeklyTargetsListener = false;
                })
                .show();
    }

    /**
     * Shows a confirmation dialog before deleting app data.
     *
     * This prevents accidental deletion because recipes and planner entries cannot currently be
     * recovered after clearing.
     */
    private void showClearDataConfirmation() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Clear All Data")
                .setMessage("This will permanently delete all recipes and meal planner entries. Are you sure?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    /*
                     * Clear SQLite data: recipes and meal planner entries.
                     */
                    dbHelper.clearAllData();

                    /*
                     * Also clear macro progress/result values stored in SharedPreferences.
                     *
                     * The target values themselves are left unchanged so the user does not need to
                     * re-enter their goals after clearing recipes/planner data.
                     */
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

    /**
     * Returns the correct period start depending on target mode.
     */
    private long getCurrentPeriodStartMillis(boolean dailyTargets) {
        if (dailyTargets) {
            return getTodayMidnightMillis();
        } else {
            return getCurrentMondayMidnightMillis();
        }
    }

    /**
     * Returns today's date at midnight.
     *
     * Used as the start of the current period for daily targets.
     */
    private long getTodayMidnightMillis() {
        Calendar calendar = Calendar.getInstance();

        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        return calendar.getTimeInMillis();
    }

    /**
     * Returns Monday at midnight for the current week.
     *
     * Used as the start of the current period for weekly targets.
     */
    private long getCurrentMondayMidnightMillis() {
        Calendar calendar = Calendar.getInstance();

        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        /*
         * Calendar.DAY_OF_WEEK uses Sunday = 1 and Monday = 2.
         * Subtracting Calendar.MONDAY gives the number of days since Monday.
         */
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int daysSinceMonday = dayOfWeek - Calendar.MONDAY;

        /*
         * If today is Sunday, daysSinceMonday becomes negative.
         * Add 7 so the calculation moves back to the previous Monday.
         */
        if (daysSinceMonday < 0) {
            daysSinceMonday += 7;
        }

        calendar.add(Calendar.DAY_OF_MONTH, -daysSinceMonday);

        return calendar.getTimeInMillis();
    }
}