package com.example.mobilecomputingproject;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class RecipeDetailFragment extends Fragment {

    private static final String ARG_ID = "id";
    private static final String ARG_TITLE = "title";
    private static final String ARG_TAGS = "tags";
    private static final String ARG_INGREDIENTS = "ingredients";
    private static final String ARG_INSTRUCTIONS = "instructions";
    private static final String ARG_IMAGE_URI = "imageUri";
    private static final String ARG_CALORIES = "calories";
    private static final String ARG_PROTEIN = "protein";
    private static final String ARG_CARBS = "carbs";
    private static final String ARG_FAT = "fat";

    public static RecipeDetailFragment newInstance(Recipe recipe) {
        RecipeDetailFragment fragment = new RecipeDetailFragment();
        Bundle args = new Bundle();

        args.putLong(ARG_ID, recipe.getId());
        args.putString(ARG_TITLE, recipe.getTitle());
        args.putString(ARG_TAGS, recipe.getTags());
        args.putString(ARG_INGREDIENTS, recipe.getIngredients());
        args.putString(ARG_INSTRUCTIONS, recipe.getInstructions());
        args.putString(ARG_IMAGE_URI, recipe.getImageUri());
        args.putString(ARG_CALORIES, recipe.getCalories());
        args.putString(ARG_PROTEIN, recipe.getProtein());
        args.putString(ARG_CARBS, recipe.getCarbs());
        args.putString(ARG_FAT, recipe.getFat());

        fragment.setArguments(args);
        return fragment;
    }

    public RecipeDetailFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_recipe_detail, container, false);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        ImageView image = view.findViewById(R.id.detail_recipe_image);
        TextView title = view.findViewById(R.id.detail_recipe_title);
        TextView tags = view.findViewById(R.id.detail_recipe_tags);
        TextView ingredients = view.findViewById(R.id.detail_recipe_ingredients);
        TextView instructions = view.findViewById(R.id.detail_recipe_instructions);
        TextView macros = view.findViewById(R.id.detail_recipe_macros);

        Button copyButton = view.findViewById(R.id.copy_recipe_button);
        Button editButton = view.findViewById(R.id.edit_recipe_button);
        Button deleteButton = view.findViewById(R.id.delete_recipe_button);

        Bundle args = getArguments();

        if (args == null) {
            return;
        }

        long recipeId = args.getLong(ARG_ID);
        String recipeTitle = args.getString(ARG_TITLE, "");
        String recipeTags = args.getString(ARG_TAGS, "");
        String recipeIngredients = args.getString(ARG_INGREDIENTS, "");
        String recipeInstructions = args.getString(ARG_INSTRUCTIONS, "");
        String imageUri = args.getString(ARG_IMAGE_URI, "");
        String calories = args.getString(ARG_CALORIES, "");
        String protein = args.getString(ARG_PROTEIN, "");
        String carbs = args.getString(ARG_CARBS, "");
        String fat = args.getString(ARG_FAT, "");

        title.setText(recipeTitle);

        if (recipeTags != null && !recipeTags.trim().isEmpty()) {
            tags.setText("Tags: " + recipeTags);
        } else {
            tags.setText("Tags: None");
        }

        ingredients.setText(recipeIngredients);
        instructions.setText(recipeInstructions);

        macros.setText(
                "Calories: " + calories + "\n" +
                        "Protein: " + protein + "g\n" +
                        "Carbs: " + carbs + "g\n" +
                        "Fat: " + fat + "g"
        );

        if (imageUri != null && !imageUri.isEmpty()) {
            image.setImageURI(Uri.parse(imageUri));
        }

        ((MainActivity) requireActivity()).setToolbarTitle(recipeTitle);

        String fullRecipeText =
                recipeTitle + "\n" +
                        "Tags: " + (recipeTags == null || recipeTags.trim().isEmpty() ? "None" : recipeTags) + "\n\n" +
                        "Ingredients:\n" + recipeIngredients + "\n\n" +
                        "Instructions:\n" + recipeInstructions + "\n\n" +
                        "Macros:\n" +
                        "Calories: " + calories + "\n" +
                        "Protein: " + protein + "g\n" +
                        "Carbs: " + carbs + "g\n" +
                        "Fat: " + fat + "g";

        copyButton.setOnClickListener(v -> {
            ClipboardManager clipboard =
                    (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);

            ClipData clip = ClipData.newPlainText("Recipe", fullRecipeText);
            clipboard.setPrimaryClip(clip);

            Toast.makeText(getContext(), "Recipe copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        editButton.setOnClickListener(v -> {
            Recipe recipe = new Recipe(
                    recipeId,
                    recipeTitle,
                    recipeIngredients,
                    recipeInstructions,
                    imageUri,
                    calories,
                    protein,
                    carbs,
                    fat,
                    recipeTags
            );

            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, AddRecipeFragment.newInstanceForEdit(recipe))
                    .addToBackStack(null)
                    .commit();

            ((MainActivity) requireActivity()).setToolbarTitle("Edit Recipe");
        });

        deleteButton.setOnClickListener(v -> {
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete Recipe")
                    .setMessage("Are you sure you want to delete this recipe?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        RecipeDbHelper dbHelper = new RecipeDbHelper(requireContext());
                        dbHelper.deleteRecipe(recipeId);

                        Toast.makeText(getContext(), "Recipe deleted", Toast.LENGTH_SHORT).show();

                        requireActivity()
                                .getSupportFragmentManager()
                                .beginTransaction()
                                .replace(R.id.fragment_container, new RecipeFragment())
                                .commit();

                        ((MainActivity) requireActivity()).setToolbarTitle("Recipe Manager");
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }
}