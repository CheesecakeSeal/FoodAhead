package com.example.mobilecomputingproject;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.content.Intent;
import android.net.Uri;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import android.widget.LinearLayout;
import android.widget.Switch;

public class AddRecipeFragment extends Fragment {

    private EditText titleInput;
    private EditText ingredientsInput;
    private EditText instructionsInput;
    private Button saveButton;
    private Button selectImageButton;
    private ImageView recipeImagePreview;
    private Uri selectedImageUri;
    private Switch autoMacrosSwitch;
    private LinearLayout manualMacrosLayout;
    private EditText caloriesInput;
    private EditText proteinInput;
    private EditText carbsInput;
    private EditText fatInput;
    private RecipeDbHelper dbHelper;
    private static final String ARG_IS_EDIT = "isEdit";
    private static final String ARG_ID = "id";
    private static final String ARG_TITLE = "title";
    private static final String ARG_INGREDIENTS = "ingredients";
    private static final String ARG_INSTRUCTIONS = "instructions";
    private static final String ARG_IMAGE_URI = "imageUri";
    private static final String ARG_CALORIES = "calories";
    private static final String ARG_PROTEIN = "protein";
    private static final String ARG_CARBS = "carbs";
    private static final String ARG_FAT = "fat";

    private boolean isEditMode = false;
    private long editRecipeId = -1;

    public AddRecipeFragment() {
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_add_recipe, container, false);
    }

    private final ActivityResultLauncher<String[]> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    selectedImageUri = uri;

                    requireActivity().getContentResolver().takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                    );

                    recipeImagePreview.setImageURI(uri);
                    recipeImagePreview.setVisibility(View.VISIBLE);
                }
            });

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        titleInput = view.findViewById(R.id.recipe_title_input);
        ingredientsInput = view.findViewById(R.id.recipe_ingredients_input);
        instructionsInput = view.findViewById(R.id.recipe_instructions_input);
        saveButton = view.findViewById(R.id.save_recipe_button);

        saveButton.setOnClickListener(v -> saveRecipe());

        selectImageButton = view.findViewById(R.id.select_image_button);
        recipeImagePreview = view.findViewById(R.id.recipe_image_preview);

        selectImageButton.setOnClickListener(v -> {
            imagePickerLauncher.launch(new String[]{"image/*"});
        });
        autoMacrosSwitch = view.findViewById(R.id.auto_macros_switch);
        manualMacrosLayout = view.findViewById(R.id.manual_macros_layout);

        caloriesInput = view.findViewById(R.id.calories_input);
        proteinInput = view.findViewById(R.id.protein_input);
        carbsInput = view.findViewById(R.id.carbs_input);
        fatInput = view.findViewById(R.id.fat_input);

        dbHelper = new RecipeDbHelper(requireContext());

        autoMacrosSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                manualMacrosLayout.setVisibility(View.GONE);
            } else {
                manualMacrosLayout.setVisibility(View.VISIBLE);
            }
        });

        Bundle args = getArguments();

        if (args != null && args.getBoolean(ARG_IS_EDIT, false)) {
            isEditMode = true;
            editRecipeId = args.getLong(ARG_ID);

            titleInput.setText(args.getString(ARG_TITLE, ""));
            ingredientsInput.setText(args.getString(ARG_INGREDIENTS, ""));
            instructionsInput.setText(args.getString(ARG_INSTRUCTIONS, ""));
            caloriesInput.setText(args.getString(ARG_CALORIES, ""));
            proteinInput.setText(args.getString(ARG_PROTEIN, ""));
            carbsInput.setText(args.getString(ARG_CARBS, ""));
            fatInput.setText(args.getString(ARG_FAT, ""));

            String imageUriString = args.getString(ARG_IMAGE_URI, "");
            if (!imageUriString.isEmpty()) {
                selectedImageUri = Uri.parse(imageUriString);
                recipeImagePreview.setImageURI(selectedImageUri);
                recipeImagePreview.setVisibility(View.VISIBLE);
            }

            saveButton.setText("Update Recipe");
        }
    }

    private void saveRecipe() {
        String title = titleInput.getText().toString().trim();
        String ingredients = ingredientsInput.getText().toString().trim();
        String instructions = instructionsInput.getText().toString().trim();

        if (TextUtils.isEmpty(title)) {
            titleInput.setError("Recipe title is required");
            return;
        }

        if (TextUtils.isEmpty(ingredients)) {
            ingredientsInput.setError("Ingredients are required");
            return;
        }

        if (TextUtils.isEmpty(instructions)) {
            instructionsInput.setError("Instructions are required");
            return;
        }

        String imageUri = selectedImageUri != null ? selectedImageUri.toString() : "";

        Toast.makeText(getContext(), "Recipe saved", Toast.LENGTH_SHORT).show();

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new RecipeFragment())
                .commit();

        ((MainActivity) requireActivity()).setToolbarTitle("Recipe Manager");

        boolean useAutoMacros = autoMacrosSwitch.isChecked();

        String calories = "";
        String protein = "";
        String carbs = "";
        String fat = "";

        if (!useAutoMacros) {
            calories = caloriesInput.getText().toString().trim();
            protein = proteinInput.getText().toString().trim();
            carbs = carbsInput.getText().toString().trim();
            fat = fatInput.getText().toString().trim();
        }

        Recipe recipe = new Recipe(
                0,
                title,
                ingredients,
                instructions,
                imageUri,
                calories,
                protein,
                carbs,
                fat
        );

        long result;
        if (isEditMode) {
            recipe = new Recipe(
                    editRecipeId,
                    title,
                    ingredients,
                    instructions,
                    imageUri,
                    calories,
                    protein,
                    carbs,
                    fat
            );

            result = dbHelper.updateRecipe(recipe);
        } else {
            result = dbHelper.insertRecipe(recipe);
        }

        if (result == -1) {
            Toast.makeText(getContext(), "Error saving recipe", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(getContext(), isEditMode ? "Recipe updated" : "Recipe saved", Toast.LENGTH_SHORT).show();

            requireActivity()
                    .getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, new RecipeFragment())
                    .commit();

            ((MainActivity) requireActivity()).setToolbarTitle("Recipe Manager");
        }
    }

    public static AddRecipeFragment newInstanceForEdit(Recipe recipe) {
        AddRecipeFragment fragment = new AddRecipeFragment();
        Bundle args = new Bundle();

        args.putBoolean(ARG_IS_EDIT, true);
        args.putLong(ARG_ID, recipe.getId());
        args.putString(ARG_TITLE, recipe.getTitle());
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
}