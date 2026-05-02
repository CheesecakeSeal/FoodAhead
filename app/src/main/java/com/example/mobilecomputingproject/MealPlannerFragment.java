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

public class MealPlannerFragment extends Fragment {

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

        buildPlannerGrid();
    }

    private void buildPlannerGrid() {
        plannerTable.removeAllViews();

        TableRow headerRow = new TableRow(requireContext());
        headerRow.addView(createHeaderCell(""));

        for (String day : days) {
            headerRow.addView(createHeaderCell(day));
        }

        plannerTable.addView(headerRow);

        for (int hour = 0; hour < 24; hour++) {
            TableRow row = new TableRow(requireContext());

            row.addView(createHeaderCell(String.format("%02d:00", hour)));

            for (int day = 0; day < 7; day++) {
                row.addView(createMealSlotCell(day, hour));
            }

            plannerTable.addView(row);
        }
    }

    private TextView createHeaderCell(String text) {
        TextView cell = createCell(text);

        cell.setTextColor(getResources().getColor(R.color.planner_text));
        cell.setTypeface(null, android.graphics.Typeface.BOLD);
        cell.setBackgroundColor(getResources().getColor(R.color.planner_cell_empty));

        return cell;
    }

    private TextView createMealSlotCell(int day, int hour) {
        String mealTitle = dbHelper.getMealForSlot(day, hour);
        long recipeId = dbHelper.getMealRecipeIdForSlot(day, hour);

        TextView cell = createCell(mealTitle);

        if (mealTitle.isEmpty()) {
            cell.setTextColor(getResources().getColor(R.color.planner_text));
            cell.setBackgroundColor(getResources().getColor(R.color.planner_cell_empty));
            cell.setOnClickListener(v -> showRecipePicker(day, hour));
        } else {
            cell.setTextColor(getResources().getColor(R.color.planner_recipe_text));
            cell.setBackgroundColor(getResources().getColor(R.color.planner_cell_filled));
            cell.setOnClickListener(v -> openRecipeDetails(recipeId));
        }

        cell.setOnLongClickListener(v -> {
            dbHelper.clearMealSlot(day, hour);
            Toast.makeText(getContext(), "Meal cleared", Toast.LENGTH_SHORT).show();
            buildPlannerGrid();
            return true;
        });

        return cell;
    }

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
        cell.setSingleLine(true);
        cell.setEllipsize(TextUtils.TruncateAt.END);
        cell.setIncludeFontPadding(false);
        cell.setBackgroundColor(getResources().getColor(R.color.planner_cell_empty));

        return cell;
    }

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

                    dbHelper.saveMealPlan(
                            day,
                            hour,
                            selectedRecipe.getId(),
                            selectedRecipe.getTitle()
                    );

                    Toast.makeText(getContext(), "Meal added", Toast.LENGTH_SHORT).show();
                    buildPlannerGrid();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openRecipeDetails(long recipeId) {
        Recipe recipe = dbHelper.getRecipeById(recipeId);

        if (recipe == null) {
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

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}