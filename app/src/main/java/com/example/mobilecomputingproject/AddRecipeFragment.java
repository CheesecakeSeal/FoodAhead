package com.example.mobilecomputingproject;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mobilecomputingproject.api.UsdaRepository;

import java.util.ArrayList;

public class AddRecipeFragment extends Fragment {

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
    private static final String ARG_TAGS = "tags";

    private EditText titleInput;
    private EditText tagsInput;
    private EditText ingredientsInput;
    private EditText instructionsInput;
    private EditText caloriesInput;
    private EditText proteinInput;
    private EditText carbsInput;
    private EditText fatInput;

    private Button saveButton;
    private Button selectImageButton;
    private Button selectExistingTagsButton;

    private ImageView recipeImagePreview;
    private Switch autoMacrosSwitch;
    private LinearLayout manualMacrosLayout;

    private Uri selectedImageUri;
    private RecipeDbHelper dbHelper;

    private boolean isEditMode = false;
    private long editRecipeId = -1;
    private boolean hasShownAutoMacrosWarning = false;

    public AddRecipeFragment() {
        // Required empty public constructor
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
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_add_recipe, container, false);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        dbHelper = new RecipeDbHelper(requireContext());

        titleInput = view.findViewById(R.id.recipe_title_input);
        tagsInput = view.findViewById(R.id.recipe_tags_input);
        ingredientsInput = view.findViewById(R.id.recipe_ingredients_input);
        instructionsInput = view.findViewById(R.id.recipe_instructions_input);

        caloriesInput = view.findViewById(R.id.calories_input);
        proteinInput = view.findViewById(R.id.protein_input);
        carbsInput = view.findViewById(R.id.carbs_input);
        fatInput = view.findViewById(R.id.fat_input);

        saveButton = view.findViewById(R.id.save_recipe_button);
        selectImageButton = view.findViewById(R.id.select_image_button);
        selectExistingTagsButton = view.findViewById(R.id.select_existing_tags_button);

        recipeImagePreview = view.findViewById(R.id.recipe_image_preview);
        autoMacrosSwitch = view.findViewById(R.id.auto_macros_switch);
        manualMacrosLayout = view.findViewById(R.id.manual_macros_layout);

        selectExistingTagsButton.setOnClickListener(v -> showExistingTagsDialog());

        selectImageButton.setOnClickListener(v ->
                imagePickerLauncher.launch(new String[]{"image/*"})
        );

        saveButton.setOnClickListener(v -> saveRecipe());

        autoMacrosSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            manualMacrosLayout.setVisibility(isChecked ? View.GONE : View.VISIBLE);

            if (isChecked && !hasShownAutoMacrosWarning) {
                hasShownAutoMacrosWarning = true;

                new AlertDialog.Builder(requireContext())
                        .setTitle("Auto-filled macros")
                        .setMessage("This feature uses the USDA FoodData Central API to provide estimated macro values. Results may not be entirely accurate. We recommend manually entering macros when uncertain.")
                        .setPositiveButton("OK", null)
                        .show();
            }
        });

        setupEditModeIfNeeded();
    }

    private void setupEditModeIfNeeded() {
        Bundle args = getArguments();

        if (args == null || !args.getBoolean(ARG_IS_EDIT, false)) {
            return;
        }

        isEditMode = true;
        editRecipeId = args.getLong(ARG_ID);

        titleInput.setText(args.getString(ARG_TITLE, ""));
        tagsInput.setText(args.getString(ARG_TAGS, ""));
        ingredientsInput.setText(args.getString(ARG_INGREDIENTS, ""));
        instructionsInput.setText(args.getString(ARG_INSTRUCTIONS, ""));
        caloriesInput.setText(args.getString(ARG_CALORIES, ""));
        proteinInput.setText(args.getString(ARG_PROTEIN, ""));
        carbsInput.setText(args.getString(ARG_CARBS, ""));
        fatInput.setText(args.getString(ARG_FAT, ""));

        String imageUriString = args.getString(ARG_IMAGE_URI, "");
        if (imageUriString != null && !imageUriString.isEmpty()) {
            selectedImageUri = Uri.parse(imageUriString);
            recipeImagePreview.setImageURI(selectedImageUri);
            recipeImagePreview.setVisibility(View.VISIBLE);
        }

        saveButton.setText("Update Recipe");
    }

    private void saveRecipe() {
        String title = titleInput.getText().toString().trim();
        String tags = cleanTags(tagsInput.getText().toString().trim());
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
        boolean useAutoMacros = autoMacrosSwitch.isChecked();

        if (useAutoMacros) {
            fetchMacrosThenSave(title, tags, ingredients, instructions, imageUri);
        } else {
            String calories = caloriesInput.getText().toString().trim();
            String protein = proteinInput.getText().toString().trim();
            String carbs = carbsInput.getText().toString().trim();
            String fat = fatInput.getText().toString().trim();

            saveRecipeToDatabase(
                    title,
                    tags,
                    ingredients,
                    instructions,
                    imageUri,
                    calories,
                    protein,
                    carbs,
                    fat
            );
        }
    }

    private void fetchMacrosThenSave(
            String title,
            String tags,
            String ingredients,
            String instructions,
            String imageUri
    ) {
        String ingredientForApi = getFirstIngredient(ingredients);

        if (ingredientForApi.isEmpty()) {
            Toast.makeText(getContext(), "No ingredient found for macro lookup", Toast.LENGTH_SHORT).show();
            return;
        }

        saveButton.setEnabled(false);
        saveButton.setText("Fetching macros...");

        UsdaRepository.getInstance().fetchMacrosForIngredient(
                ingredientForApi,
                new UsdaRepository.MacroCallback() {
                    @Override
                    public void onSuccess(String apiCalories, String apiProtein, String apiCarbs, String apiFat) {
                        saveRecipeToDatabase(
                                title,
                                tags,
                                ingredients,
                                instructions,
                                imageUri,
                                apiCalories,
                                apiProtein,
                                apiCarbs,
                                apiFat
                        );
                    }

                    @Override
                    public void onError(String message) {
                        saveButton.setEnabled(true);
                        saveButton.setText(isEditMode ? "Update Recipe" : "Save Recipe");
                        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private String getFirstIngredient(String ingredients) {
        if (ingredients == null || ingredients.trim().isEmpty()) {
            return "";
        }

        String[] parts = ingredients.split("[,\\n]");
        return parts[0].trim();
    }

    private void saveRecipeToDatabase(
            String title,
            String tags,
            String ingredients,
            String instructions,
            String imageUri,
            String calories,
            String protein,
            String carbs,
            String fat
    ) {
        Recipe recipe = new Recipe(
                isEditMode ? editRecipeId : 0,
                title,
                ingredients,
                instructions,
                imageUri,
                calories,
                protein,
                carbs,
                fat,
                tags
        );

        long result;

        if (isEditMode) {
            result = dbHelper.updateRecipe(recipe);
        } else {
            result = dbHelper.insertRecipe(recipe);
        }

        if (result == -1) {
            Toast.makeText(getContext(), "Error saving recipe", Toast.LENGTH_SHORT).show();
            saveButton.setEnabled(true);
            saveButton.setText(isEditMode ? "Update Recipe" : "Save Recipe");
            return;
        }

        Toast.makeText(
                getContext(),
                isEditMode ? "Recipe updated" : "Recipe saved",
                Toast.LENGTH_SHORT
        ).show();

        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new RecipeFragment())
                .commit();

        ((MainActivity) requireActivity()).setToolbarTitle("Recipe Manager");
    }

    private String cleanTags(String rawTags) {
        if (rawTags == null || rawTags.trim().isEmpty()) {
            return "";
        }

        String[] splitTags = rawTags.split(",");
        StringBuilder cleaned = new StringBuilder();

        for (String tag : splitTags) {
            String cleanedTag = tag.trim();

            if (!cleanedTag.isEmpty()) {
                if (cleaned.length() > 0) {
                    cleaned.append(", ");
                }

                cleaned.append(cleanedTag);
            }
        }

        return cleaned.toString();
    }

    private void showExistingTagsDialog() {
        ArrayList<String> existingTags = dbHelper.getAllTags();

        if (existingTags.isEmpty()) {
            Toast.makeText(getContext(), "No existing tags found", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] tagArray = existingTags.toArray(new String[0]);
        boolean[] checkedItems = new boolean[tagArray.length];

        String currentTags = tagsInput.getText().toString();

        for (int i = 0; i < tagArray.length; i++) {
            checkedItems[i] = currentTags.toLowerCase().contains(tagArray[i].toLowerCase());
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Select Tags")
                .setMultiChoiceItems(tagArray, checkedItems, (dialog, which, isChecked) -> {
                    checkedItems[which] = isChecked;
                })
                .setPositiveButton("Add Tags", (dialog, which) -> {
                    StringBuilder selectedTags = new StringBuilder(tagsInput.getText().toString().trim());

                    for (int i = 0; i < tagArray.length; i++) {
                        if (checkedItems[i]) {
                            String tag = tagArray[i];

                            if (!containsTag(selectedTags.toString(), tag)) {
                                if (selectedTags.length() > 0) {
                                    selectedTags.append(", ");
                                }

                                selectedTags.append(tag);
                            }
                        }
                    }

                    tagsInput.setText(cleanTags(selectedTags.toString()));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean containsTag(String currentTags, String tagToCheck) {
        if (currentTags == null || currentTags.trim().isEmpty()) {
            return false;
        }

        String[] splitTags = currentTags.split(",");

        for (String tag : splitTags) {
            if (tag.trim().equalsIgnoreCase(tagToCheck.trim())) {
                return true;
            }
        }

        return false;
    }

    public static AddRecipeFragment newInstanceForEdit(Recipe recipe) {
        AddRecipeFragment fragment = new AddRecipeFragment();
        Bundle args = new Bundle();

        args.putBoolean(ARG_IS_EDIT, true);
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
}