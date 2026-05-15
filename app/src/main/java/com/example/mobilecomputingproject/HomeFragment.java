package com.example.mobilecomputingproject;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

/**
 * Home screen / dashboard for macro progress tracking.
 *
 * This fragment lets the user:
 * - Set calorie, protein, carbs, and fat targets.
 * - Track progress towards those targets with progress bars.
 * - Add progress manually.
 * - Add progress from a saved recipe.
 * - See whether the previous daily/weekly target period was successful.
 *
 * The target mode is controlled from Settings:
 * - Daily targets reset every day at midnight.
 * - Weekly targets reset every Monday at midnight.
 *
 * SharedPreferences is used here instead of SQLite because macro targets and progress values are
 * simple key-value settings rather than relational data.
 */
public class HomeFragment extends Fragment {

    private static final String PREFS_NAME = "FoodAheadPrefs";
    private static final String KEY_DAILY_TARGETS = "daily_targets";
    private static final String KEY_PERIOD_START = "macro_period_start";
    private static final String KEY_GOAL_CALORIES = "goal_calories";
    private static final String KEY_GOAL_PROTEIN = "goal_protein";
    private static final String KEY_GOAL_CARBS = "goal_carbs";
    private static final String KEY_GOAL_FAT = "goal_fat";
    private static final String KEY_CURRENT_CALORIES = "current_calories";
    private static final String KEY_CURRENT_PROTEIN = "current_protein";
    private static final String KEY_CURRENT_CARBS = "current_carbs";
    private static final String KEY_CURRENT_FAT = "current_fat";
    private static final String KEY_HAS_LAST_RESULT = "has_last_result";
    private static final String KEY_LAST_CALORIES_SUCCESS = "last_calories_success";
    private static final String KEY_LAST_PROTEIN_SUCCESS = "last_protein_success";
    private static final String KEY_LAST_CARBS_SUCCESS = "last_carbs_success";
    private static final String KEY_LAST_FAT_SUCCESS = "last_fat_success";
    private static final String KEY_LAST_CALORIES_PROGRESS = "last_calories_progress";
    private static final String KEY_LAST_PROTEIN_PROGRESS = "last_protein_progress";
    private static final String KEY_LAST_CARBS_PROGRESS = "last_carbs_progress";
    private static final String KEY_LAST_FAT_PROGRESS = "last_fat_progress";
    private static final String KEY_LAST_CALORIES_GOAL = "last_calories_goal";
    private static final String KEY_LAST_PROTEIN_GOAL = "last_protein_goal";
    private static final String KEY_LAST_CARBS_GOAL = "last_carbs_goal";
    private static final String KEY_LAST_FAT_GOAL = "last_fat_goal";
    private TextView homeHeading;
    private TextView caloriesProgressText;
    private TextView proteinProgressText;
    private TextView carbsProgressText;
    private TextView fatProgressText;
    private TextView lastPeriodHeading;
    private TextView lastWeekResultsText;
    private ProgressBar caloriesProgressBar;
    private ProgressBar proteinProgressBar;
    private ProgressBar carbsProgressBar;
    private ProgressBar fatProgressBar;
    private Button setGoalsButton;
    private Button addManualProgressButton;
    private Button addRecipeProgressButton;
    private SharedPreferences prefs;
    private RecipeDbHelper dbHelper;

    public HomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        dbHelper = new RecipeDbHelper(requireContext());

        homeHeading = view.findViewById(R.id.home_heading);

        caloriesProgressText = view.findViewById(R.id.calories_progress_text);
        proteinProgressText = view.findViewById(R.id.protein_progress_text);
        carbsProgressText = view.findViewById(R.id.carbs_progress_text);
        fatProgressText = view.findViewById(R.id.fat_progress_text);

        lastPeriodHeading = view.findViewById(R.id.last_week_heading);
        lastWeekResultsText = view.findViewById(R.id.last_week_results_text);

        caloriesProgressBar = view.findViewById(R.id.calories_progress_bar);
        proteinProgressBar = view.findViewById(R.id.protein_progress_bar);
        carbsProgressBar = view.findViewById(R.id.carbs_progress_bar);
        fatProgressBar = view.findViewById(R.id.fat_progress_bar);

        setGoalsButton = view.findViewById(R.id.set_goals_button);
        addManualProgressButton = view.findViewById(R.id.add_manual_progress_button);
        addRecipeProgressButton = view.findViewById(R.id.add_recipe_progress_button);

        checkPeriodReset();

        updateProgressViews();

        setGoalsButton.setOnClickListener(v -> showSetGoalsDialog());
        addManualProgressButton.setOnClickListener(v -> showManualProgressDialog());
        addRecipeProgressButton.setOnClickListener(v -> showRecipeProgressDialog());
    }

    /**
     * Reads the target mode from SharedPreferences.
     *
     * Default is true, meaning the app uses daily targets unless the user enables weekly targets
     * in Settings.
     */
    private boolean isDailyTargetsEnabled() {
        return prefs.getBoolean(KEY_DAILY_TARGETS, true);
    }

    /**
     * Checks whether the current tracking period has changed.
     *
     * Example:
     * - Daily mode: if the saved period start was yesterday's midnight and today is now active,
     *   save yesterday's result and reset today's progress.
     * - Weekly mode: if the saved period start was last Monday and a new Monday has started,
     *   save last week's result and reset weekly progress.
     */
    private void checkPeriodReset() {
        boolean dailyTargets = isDailyTargetsEnabled();

        long currentPeriodStart = getCurrentPeriodStartMillis(dailyTargets);
        long savedPeriodStart = prefs.getLong(KEY_PERIOD_START, -1);

        /*
         * First time using the tracker: initialise the period start and do not save any previous
         * result because there is no completed period yet.
         */
        if (savedPeriodStart == -1) {
            prefs.edit()
                    .putLong(KEY_PERIOD_START, currentPeriodStart)
                    .apply();
            return;
        }

        /*
         * If the calculated current period is newer than the saved one, the previous period has
         * ended. Save its success/failure result, then reset current progress for the new period.
         */
        if (currentPeriodStart > savedPeriodStart) {
            saveLastPeriodResult();

            prefs.edit()
                    .putLong(KEY_PERIOD_START, currentPeriodStart)
                    .putFloat(KEY_CURRENT_CALORIES, 0f)
                    .putFloat(KEY_CURRENT_PROTEIN, 0f)
                    .putFloat(KEY_CURRENT_CARBS, 0f)
                    .putFloat(KEY_CURRENT_FAT, 0f)
                    .apply();
        }
    }

    /**
     * Returns the start timestamp for the currently active tracking period.
     */
    private long getCurrentPeriodStartMillis(boolean dailyTargets) {
        if (dailyTargets) {
            return getTodayMidnightMillis();
        } else {
            return getCurrentMondayMidnightMillis();
        }
    }

    /**
     * Returns today's date at 00:00.
     *
     * Used as the period start for daily target tracking.
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
     * Returns Monday 00:00 for the current week.
     *
     * Used as the period start for weekly target tracking.
     */
    private long getCurrentMondayMidnightMillis() {
        Calendar calendar = Calendar.getInstance();

        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        /*
         * Calendar.DAY_OF_WEEK uses Sunday as 1 and Monday as 2.
         * This calculation converts the current day into "days since Monday".
         */
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        int daysSinceMonday = dayOfWeek - Calendar.MONDAY;

        /*
         * If today is Sunday, daysSinceMonday becomes negative, so add 7 to move back to the
         * previous Monday.
         */
        if (daysSinceMonday < 0) {
            daysSinceMonday += 7;
        }

        calendar.add(Calendar.DAY_OF_MONTH, -daysSinceMonday);

        return calendar.getTimeInMillis();
    }

    /**
     * Saves the result for the period that just ended.
     *
     * It records:
     * - Whether each macro target was reached.
     * - The previous progress values.
     * - The previous target values.
     *
     * The target values are saved because the user might change their goals later, and the previous
     * result should still reflect the targets that existed at the time.
     */
    private void saveLastPeriodResult() {
        float goalCalories = getFloat(KEY_GOAL_CALORIES);
        float goalProtein = getFloat(KEY_GOAL_PROTEIN);
        float goalCarbs = getFloat(KEY_GOAL_CARBS);
        float goalFat = getFloat(KEY_GOAL_FAT);

        /*
         * Only create a previous result if at least one target was actually set.
         */
        boolean goalsWereSet =
                goalCalories > 0 ||
                        goalProtein > 0 ||
                        goalCarbs > 0 ||
                        goalFat > 0;

        if (!goalsWereSet) {
            prefs.edit()
                    .putBoolean(KEY_HAS_LAST_RESULT, false)
                    .apply();
            return;
        }

        float currentCalories = getFloat(KEY_CURRENT_CALORIES);
        float currentProtein = getFloat(KEY_CURRENT_PROTEIN);
        float currentCarbs = getFloat(KEY_CURRENT_CARBS);
        float currentFat = getFloat(KEY_CURRENT_FAT);

        prefs.edit()
                .putBoolean(KEY_HAS_LAST_RESULT, true)

                /*
                 * A macro is successful only if a target was set and progress reached/exceeded it.
                 */
                .putBoolean(KEY_LAST_CALORIES_SUCCESS, goalCalories > 0 && currentCalories >= goalCalories)
                .putBoolean(KEY_LAST_PROTEIN_SUCCESS, goalProtein > 0 && currentProtein >= goalProtein)
                .putBoolean(KEY_LAST_CARBS_SUCCESS, goalCarbs > 0 && currentCarbs >= goalCarbs)
                .putBoolean(KEY_LAST_FAT_SUCCESS, goalFat > 0 && currentFat >= goalFat)

                /*
                 * Store actual previous progress.
                 */
                .putFloat(KEY_LAST_CALORIES_PROGRESS, currentCalories)
                .putFloat(KEY_LAST_PROTEIN_PROGRESS, currentProtein)
                .putFloat(KEY_LAST_CARBS_PROGRESS, currentCarbs)
                .putFloat(KEY_LAST_FAT_PROGRESS, currentFat)

                /*
                 * Store the goals that were active during that previous period.
                 */
                .putFloat(KEY_LAST_CALORIES_GOAL, goalCalories)
                .putFloat(KEY_LAST_PROTEIN_GOAL, goalProtein)
                .putFloat(KEY_LAST_CARBS_GOAL, goalCarbs)
                .putFloat(KEY_LAST_FAT_GOAL, goalFat)

                .apply();
    }

    /**
     * Shows a dialog for setting macro targets.
     *
     * The same dialog is used for daily and weekly targets. The current mode is read from Settings
     * and shown in the dialog title/message.
     */
    private void showSetGoalsDialog() {
        boolean dailyTargets = isDailyTargetsEnabled();

        LinearLayout layout = createInputLayout();

        EditText caloriesInput = createNumberInput("Calories target");
        EditText proteinInput = createNumberInput("Protein target (g)");
        EditText carbsInput = createNumberInput("Carbs target (g)");
        EditText fatInput = createNumberInput("Fat target (g)");

        /*
         * Pre-fill the dialog with currently saved goals so users can edit instead of retyping
         * everything.
         */
        caloriesInput.setText(formatInputValue(getFloat(KEY_GOAL_CALORIES)));
        proteinInput.setText(formatInputValue(getFloat(KEY_GOAL_PROTEIN)));
        carbsInput.setText(formatInputValue(getFloat(KEY_GOAL_CARBS)));
        fatInput.setText(formatInputValue(getFloat(KEY_GOAL_FAT)));

        layout.addView(caloriesInput);
        layout.addView(proteinInput);
        layout.addView(carbsInput);
        layout.addView(fatInput);

        new AlertDialog.Builder(requireContext())
                .setTitle(dailyTargets ? "Set Daily Targets" : "Set Weekly Targets")
                .setMessage(dailyTargets
                        ? "These targets reset every day at midnight. Change target type in Settings."
                        : "These targets reset every Monday at midnight. Change target type in Settings.")
                .setView(layout)
                .setPositiveButton("Save", (dialog, which) -> {
                    /*
                     * Save target values. Empty/invalid fields are parsed as 0.
                     */
                    prefs.edit()
                            .putFloat(KEY_GOAL_CALORIES, parseInput(caloriesInput))
                            .putFloat(KEY_GOAL_PROTEIN, parseInput(proteinInput))
                            .putFloat(KEY_GOAL_CARBS, parseInput(carbsInput))
                            .putFloat(KEY_GOAL_FAT, parseInput(fatInput))
                            .apply();

                    Toast.makeText(getContext(), "Targets saved", Toast.LENGTH_SHORT).show();
                    updateProgressViews();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Shows a dialog that lets the user manually add consumed macros.
     */
    private void showManualProgressDialog() {
        LinearLayout layout = createInputLayout();

        EditText caloriesInput = createNumberInput("Calories eaten");
        EditText proteinInput = createNumberInput("Protein eaten (g)");
        EditText carbsInput = createNumberInput("Carbs eaten (g)");
        EditText fatInput = createNumberInput("Fat eaten (g)");

        layout.addView(caloriesInput);
        layout.addView(proteinInput);
        layout.addView(carbsInput);
        layout.addView(fatInput);

        new AlertDialog.Builder(requireContext())
                .setTitle("Add Progress Manually")
                .setView(layout)
                .setPositiveButton("Add", (dialog, which) -> {
                    addProgress(
                            parseInput(caloriesInput),
                            parseInput(proteinInput),
                            parseInput(carbsInput),
                            parseInput(fatInput)
                    );

                    Toast.makeText(getContext(), "Progress added", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Shows a list of saved recipes so the user can add a recipe's macro values to progress.
     *
     * This reuses the macro values already stored for each recipe, whether they were entered
     * manually or estimated through the USDA API.
     */
    private void showRecipeProgressDialog() {
        ArrayList<Recipe> recipes = dbHelper.getAllRecipes();

        if (recipes.isEmpty()) {
            Toast.makeText(getContext(), "No saved recipes found", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] recipeNames = new String[recipes.size()];

        for (int i = 0; i < recipes.size(); i++) {
            recipeNames[i] = recipes.get(i).getTitle();
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Add Progress From Recipe")
                .setItems(recipeNames, (dialog, which) -> {
                    Recipe selectedRecipe = recipes.get(which);

                    addProgress(
                            parseFloat(selectedRecipe.getCalories()),
                            parseFloat(selectedRecipe.getProtein()),
                            parseFloat(selectedRecipe.getCarbs()),
                            parseFloat(selectedRecipe.getFat())
                    );

                    Toast.makeText(getContext(), "Recipe progress added", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Adds macro values to the current period's progress.
     */
    private void addProgress(float calories, float protein, float carbs, float fat) {
        prefs.edit()
                .putFloat(KEY_CURRENT_CALORIES, getFloat(KEY_CURRENT_CALORIES) + calories)
                .putFloat(KEY_CURRENT_PROTEIN, getFloat(KEY_CURRENT_PROTEIN) + protein)
                .putFloat(KEY_CURRENT_CARBS, getFloat(KEY_CURRENT_CARBS) + carbs)
                .putFloat(KEY_CURRENT_FAT, getFloat(KEY_CURRENT_FAT) + fat)
                .apply();

        updateProgressViews();
    }

    /**
     * Refreshes all Home screen text and progress bars.
     */
    private void updateProgressViews() {
        boolean dailyTargets = isDailyTargetsEnabled();

        /*
         * Adapt headings depending on the selected target mode.
         */
        homeHeading.setText(dailyTargets ? "Daily Macro Targets" : "Weekly Macro Targets");
        lastPeriodHeading.setText(dailyTargets ? "Yesterday's Results" : "Last Week's Results");

        float goalCalories = getFloat(KEY_GOAL_CALORIES);
        float goalProtein = getFloat(KEY_GOAL_PROTEIN);
        float goalCarbs = getFloat(KEY_GOAL_CARBS);
        float goalFat = getFloat(KEY_GOAL_FAT);

        float currentCalories = getFloat(KEY_CURRENT_CALORIES);
        float currentProtein = getFloat(KEY_CURRENT_PROTEIN);
        float currentCarbs = getFloat(KEY_CURRENT_CARBS);
        float currentFat = getFloat(KEY_CURRENT_FAT);

        updateSingleProgress(
                caloriesProgressText,
                caloriesProgressBar,
                "Calories",
                currentCalories,
                goalCalories,
                "kcal"
        );

        updateSingleProgress(
                proteinProgressText,
                proteinProgressBar,
                "Protein",
                currentProtein,
                goalProtein,
                "g"
        );

        updateSingleProgress(
                carbsProgressText,
                carbsProgressBar,
                "Carbs",
                currentCarbs,
                goalCarbs,
                "g"
        );

        updateSingleProgress(
                fatProgressText,
                fatProgressBar,
                "Fat",
                currentFat,
                goalFat,
                "g"
        );

        updateLastPeriodResults();
    }

    /**
     * Updates one macro text label and progress bar.
     *
     * The progress bar is capped at 100% so going over target does not visually overflow.
     */
    private void updateSingleProgress(
            TextView textView,
            ProgressBar progressBar,
            String label,
            float current,
            float goal,
            String unit
    ) {
        textView.setText(String.format(
                Locale.US,
                "%s: %.1f / %.1f %s",
                label,
                current,
                goal,
                unit
        ));

        int progress = 0;

        if (goal > 0) {
            progress = Math.round((current / goal) * 100f);
        }

        if (progress > 100) {
            progress = 100;
        }

        progressBar.setProgress(progress);
    }

    /**
     * Displays the previous period's success/failure result.
     */
    private void updateLastPeriodResults() {
        boolean dailyTargets = isDailyTargetsEnabled();
        boolean hasResult = prefs.getBoolean(KEY_HAS_LAST_RESULT, false);

        if (!hasResult) {
            lastWeekResultsText.setText(
                    dailyTargets
                            ? "No result for yesterday yet."
                            : "No previous weekly results yet."
            );
            return;
        }

        String resultText =
                buildLastPeriodLine(
                        "Calories",
                        KEY_LAST_CALORIES_SUCCESS,
                        KEY_LAST_CALORIES_PROGRESS,
                        KEY_LAST_CALORIES_GOAL,
                        "kcal"
                ) + "\n" +
                        buildLastPeriodLine(
                                "Protein",
                                KEY_LAST_PROTEIN_SUCCESS,
                                KEY_LAST_PROTEIN_PROGRESS,
                                KEY_LAST_PROTEIN_GOAL,
                                "g"
                        ) + "\n" +
                        buildLastPeriodLine(
                                "Carbs",
                                KEY_LAST_CARBS_SUCCESS,
                                KEY_LAST_CARBS_PROGRESS,
                                KEY_LAST_CARBS_GOAL,
                                "g"
                        ) + "\n" +
                        buildLastPeriodLine(
                                "Fat",
                                KEY_LAST_FAT_SUCCESS,
                                KEY_LAST_FAT_PROGRESS,
                                KEY_LAST_FAT_GOAL,
                                "g"
                        );

        lastWeekResultsText.setText(resultText);
    }

    /**
     * Builds one line of previous-period result text.
     */
    private String buildLastPeriodLine(
            String label,
            String successKey,
            String progressKey,
            String goalKey,
            String unit
    ) {
        boolean success = prefs.getBoolean(successKey, false);
        float progress = getFloat(progressKey);
        float goal = getFloat(goalKey);

        if (goal <= 0) {
            return label + ": No target set";
        }

        return String.format(
                Locale.US,
                "%s: %s (%.1f / %.1f %s)",
                label,
                success ? "Success" : "Failed",
                progress,
                goal,
                unit
        );
    }

    /**
     * Creates a vertical layout used inside AlertDialogs.
     */
    private LinearLayout createInputLayout() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 0);
        return layout;
    }

    /**
     * Creates a numeric EditText for macro/target input.
     */
    private EditText createNumberInput(String hint) {
        EditText input = new EditText(requireContext());
        input.setHint(hint);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return input;
    }

    private float parseInput(EditText input) {
        return parseFloat(input.getText().toString());
    }

    private float parseFloat(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0f;
        }

        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private String formatInputValue(float value) {
        if (value <= 0) {
            return "";
        }

        return String.format(Locale.US, "%.1f", value);
    }

    private float getFloat(String key) {
        return prefs.getFloat(key, 0f);
    }
}