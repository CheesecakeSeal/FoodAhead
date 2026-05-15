package com.example.mobilecomputingproject;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;

/**
 * MealPlannerFragment displays the weekly meal planner.
 *
 * The planner is built as a 24-hour table:
 * - Rows represent hours from 00:00 to 23:00.
 * - Columns represent Monday to Sunday.
 * - Each cell can contain one saved recipe.
 *
 * User interactions:
 * - Tap an empty slot to choose a recipe.
 * - Tap a filled slot to open that recipe's details.
 * - Long press a slot to clear it.
 */
public class MealPlannerFragment extends Fragment {

    /*
     * TableLayout is used instead of GridLayout because it gives more consistent row alignment
     * when recipe names are displayed inside cells.
     */
    private TableLayout plannerTable;
    private RecipeDbHelper dbHelper;
    private final String[] days = {
            "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"
    };

    public MealPlannerFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_meal_planner, container, false);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        dbHelper = new RecipeDbHelper(requireContext());
        plannerTable = view.findViewById(R.id.meal_planner_table);

        /*
         * Build the planner dynamically from saved SQLite data.
         */
        buildPlannerGrid();
    }

    /**
     * Builds the full planner table.
     *
     * The table contains:
     * - One header row with day names.
     * - 24 time rows.
     * - 7 meal cells per hour.
     *
     * The table is rebuilt whenever a meal is added or cleared so the UI always matches the
     * database.
     */
    private void buildPlannerGrid() {
        plannerTable.removeAllViews();

        /*
         * First row: blank top-left corner + day headings.
         */
        TableRow headerRow = new TableRow(requireContext());
        headerRow.addView(createHeaderCell(""));

        for (String day : days) {
            headerRow.addView(createHeaderCell(day));
        }

        plannerTable.addView(headerRow);

        /*
         * Create one row for each hour of the day.
         */
        for (int hour = 0; hour < 24; hour++) {
            TableRow row = new TableRow(requireContext());

            /*
             * First column shows the hour, e.g. 00:00, 01:00, 02:00.
             */
            row.addView(createHeaderCell(String.format("%02d:00", hour)));

            /*
             * Remaining columns are the meal slots for Monday to Sunday.
             */
            for (int day = 0; day < 7; day++) {
                row.addView(createMealSlotCell(day, hour));
            }

            plannerTable.addView(row);
        }
    }

    /**
     * Creates a header cell used for day names and time labels.
     */
    private TextView createHeaderCell(String text) {
        TextView cell = createCell(text);

        cell.setTextColor(getResources().getColor(R.color.planner_text));
        cell.setTypeface(null, android.graphics.Typeface.BOLD);
        cell.setBackgroundColor(getResources().getColor(R.color.planner_cell_empty));

        return cell;
    }

    /**
     * Creates a single meal planner slot.
     *
     * If the slot is empty:
     * - It uses the empty-cell colour.
     * - Tapping it opens the recipe picker.
     *
     * If the slot has a recipe:
     * - It uses the filled-cell colour.
     * - Tapping it opens the recipe detail screen.
     *
     * Long pressing any slot clears it.
     */
    private TextView createMealSlotCell(int day, int hour) {
        /*
         * Look up whether a recipe has already been saved for this day/hour slot.
         */
        String mealTitle = dbHelper.getMealForSlot(day, hour);
        long recipeId = dbHelper.getMealRecipeIdForSlot(day, hour);

        TextView cell = createCell(mealTitle);

        // It may be deprecated, but it works
        if (mealTitle.isEmpty()) {
            /*
             * Empty slot: allow the user to choose a recipe.
             */
            cell.setTextColor(getResources().getColor(R.color.planner_text));
            cell.setBackgroundColor(getResources().getColor(R.color.planner_cell_empty));
            cell.setOnClickListener(v -> showRecipePicker(day, hour));
        } else {
            /*
             * Filled slot: open the saved recipe instead of replacing it immediately.
             */
            cell.setTextColor(getResources().getColor(R.color.planner_recipe_text));
            cell.setBackgroundColor(getResources().getColor(R.color.planner_cell_filled));
            cell.setOnClickListener(v -> openRecipeDetails(recipeId));
        }

        /*
         * Long press is used as a quick way to remove a meal from the planner.
         */
        cell.setOnLongClickListener(v -> {
            dbHelper.clearMealSlot(day, hour);
            Toast.makeText(getContext(), "Meal cleared", Toast.LENGTH_SHORT).show();

            /*
             * Rebuild the table so the cleared slot updates visually.
             */
            buildPlannerGrid();
            return true;
        });

        return cell;
    }

    /**
     * Creates the base style for every table cell.
     *
     * All cells use fixed dimensions so long recipe titles do not stretch the table.
     * Text is restricted to one line and ellipsized to keep the planner tidy.
     */
    private TextView createCell(String text) {
        TextView cell = new TextView(requireContext());

        TableRow.LayoutParams params = new TableRow.LayoutParams(
                dpToPx(95),
                dpToPx(70)
        );
        params.setMargins(1, 1, 1, 1);

        cell.setLayoutParams(params);
        cell.setText(text);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(4, 2, 4, 2);
        cell.setTextSize(12);

        /*
         * Prevent long recipe names from breaking row height.
         */
        cell.setSingleLine(true);
        cell.setEllipsize(TextUtils.TruncateAt.END);
        cell.setIncludeFontPadding(false);

        cell.setBackgroundColor(getResources().getColor(R.color.planner_cell_empty));

        return cell;
    }

    /**
     * Shows a dialog containing all saved recipes.
     *
     * When the user selects a recipe, the selected recipe is stored in the meal_plan table for the
     * chosen day and hour.
     */
    private void showRecipePicker(int day, int hour) {
        ArrayList<Recipe> recipes = dbHelper.getAllRecipes();

        if (recipes.isEmpty()) {
            Toast.makeText(getContext(), "Add recipes before planning meals", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] recipeNames = new String[recipes.size()];

        for (int i = 0; i < recipes.size(); i++) {
            recipeNames[i] = recipes.get(i).getTitle();
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Choose recipe for " + days[day] + " " + String.format("%02d:00", hour))
                .setItems(recipeNames, (dialog, which) -> {
                    Recipe selectedRecipe = recipes.get(which);

                    /*
                     * Save or update this planner slot in SQLite.
                     */
                    dbHelper.saveMealPlan(
                            day,
                            hour,
                            selectedRecipe.getId(),
                            selectedRecipe.getTitle()
                    );

                    Toast.makeText(getContext(), "Meal added", Toast.LENGTH_SHORT).show();

                    /*
                     * Rebuild the table so the selected recipe appears immediately.
                     */
                    buildPlannerGrid();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Opens the recipe detail page for a filled meal planner slot.
     */
    private void openRecipeDetails(long recipeId) {
        Recipe recipe = dbHelper.getRecipeById(recipeId);

        if (recipe == null) {
            /*
             * This can happen if a recipe was deleted but an old planner slot still references it.
             * RecipeDbHelper normally clears planner entries when deleting recipes, but this guard
             * keeps the app safe.
             */
            Toast.makeText(getContext(), "Recipe not found", Toast.LENGTH_SHORT).show();
            return;
        }

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, RecipeDetailFragment.newInstance(recipe))
                .addToBackStack(null)
                .commit();

        ((MainActivity) requireActivity()).setToolbarTitle(recipe.getTitle());
    }

    /**
     * Converts density-independent pixels to actual pixels.
     *
     * The planner cells are defined in dp for consistent sizing across different screen densities,
     * but TableRow.LayoutParams requires pixel values.
     */
    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}