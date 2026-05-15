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

/**
 * Fragment used for both adding a new recipe and editing an existing recipe.
 *
 * This screen is responsible for collecting:
 * - Recipe title
 * - Tags
 * - Ingredients
 * - Instructions
 * - Optional image
 * - Macro information
 *
 * Macro information can be entered manually by the user, or estimated using the USDA FoodData
 * Central API. The auto-estimate feature is intentionally presented as an estimate because the API
 * result depends on search matches and ingredient formatting.
 */
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

    /**
     * Launcher for Android's system document picker.
     *
     * ActivityResultContracts.OpenDocument is used because it allows the user to choose an image
     * from their device storage or supported document providers.
     *
     * The app also takes persistable URI permission. This is important because without it, Android
     * may later block access to the selected image after the app restarts.
     */
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

        /*
         * Existing tags are not stored in a separate tag table.
         * Instead, RecipeDbHelper collects distinct tags from recipes that already exist.
         */
        selectExistingTagsButton.setOnClickListener(v -> showExistingTagsDialog());

        /*
         * Opens Android's image picker and limits choices to image files.
         */
        selectImageButton.setOnClickListener(v ->
                imagePickerLauncher.launch(new String[]{"image/*"})
        );

        /*
         * Save button handles both normal add mode and edit mode.
         */
        saveButton.setOnClickListener(v -> saveRecipe());

        /*
         * When auto macros are enabled, hide manual macro inputs.
         *
         * This makes the UI clearer because the user should either enter macro values manually
         * or let the API estimate them, not both at the same time.
         */
        autoMacrosSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            manualMacrosLayout.setVisibility(isChecked ? View.GONE : View.VISIBLE);

            /*
             * Show a warning the first time auto macros are enabled.
             *
             * This is important for usability because USDA estimates are not guaranteed to be
             * perfectly accurate. The user should understand that manual entry may be better when
             * exact nutrition is required.
             */
            if (isChecked && !hasShownAutoMacrosWarning) {
                hasShownAutoMacrosWarning = true;

                new AlertDialog.Builder(requireContext())
                        .setTitle("Auto-filled macros")
                        .setMessage("This feature uses the USDA FoodData Central API to provide estimated macro values. Results may not be entirely accurate. We recommend manually entering macros when uncertain.")
                        .setPositiveButton("OK", null)
                        .show();
            }
        });

        /*
         * If this fragment was opened to edit an existing recipe, pre-fill all fields.
         */
        setupEditModeIfNeeded();
    }

    /**
     * Checks whether this fragment was opened in edit mode.
     */
    private void setupEditModeIfNeeded() {
        Bundle args = getArguments();

        if (args == null || !args.getBoolean(ARG_IS_EDIT, false)) {
            return;
        }

        isEditMode = true;
        editRecipeId = args.getLong(ARG_ID);

        /*
         * Pre-fill text fields with the existing recipe data.
         */
        titleInput.setText(args.getString(ARG_TITLE, ""));
        tagsInput.setText(args.getString(ARG_TAGS, ""));
        ingredientsInput.setText(args.getString(ARG_INGREDIENTS, ""));
        instructionsInput.setText(args.getString(ARG_INSTRUCTIONS, ""));
        caloriesInput.setText(args.getString(ARG_CALORIES, ""));
        proteinInput.setText(args.getString(ARG_PROTEIN, ""));
        carbsInput.setText(args.getString(ARG_CARBS, ""));
        fatInput.setText(args.getString(ARG_FAT, ""));

        /*
         * Restore image preview if the recipe already has an image URI.
         */
        String imageUriString = args.getString(ARG_IMAGE_URI, "");
        if (imageUriString != null && !imageUriString.isEmpty()) {
            selectedImageUri = Uri.parse(imageUriString);
            recipeImagePreview.setImageURI(selectedImageUri);
            recipeImagePreview.setVisibility(View.VISIBLE);
        }

        saveButton.setText("Update Recipe");
    }

    /**
     * Validates user input and decides whether to save manual macros or fetch USDA estimates.
     */
    private void saveRecipe() {
        String title = titleInput.getText().toString().trim();
        String tags = cleanTags(tagsInput.getText().toString().trim());
        String ingredients = ingredientsInput.getText().toString().trim();
        String instructions = instructionsInput.getText().toString().trim();

        /*
         * Basic validation prevents empty required fields being saved to SQLite.
         */
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

        /*
         * The image is optional. If no image was chosen, store an empty string.
         */
        String imageUri = selectedImageUri != null ? selectedImageUri.toString() : "";

        boolean useAutoMacros = autoMacrosSwitch.isChecked();

        if (useAutoMacros) {
            /*
             * Auto macro path:
             * Send the full ingredient list to UsdaRepository. The repository splits the text into
             * individual ingredients, searches USDA, and returns summed macro values.
             */
            fetchMacrosThenSave(title, tags, ingredients, instructions, imageUri);
        } else {
            /*
             * Manual macro path:
             * Use the values typed directly by the user.
             */
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
                    fat,
                    ""
            );
        }
    }

    /**
     * Fetches macro estimates from the USDA repository before saving.
     *
     * This method disables the save button while the network request is running. That prevents the
     * user from tapping Save multiple times and creating duplicate recipes or overlapping requests.
     */
    private void fetchMacrosThenSave(
            String title,
            String tags,
            String ingredients,
            String instructions,
            String imageUri
    ) {
        saveButton.setEnabled(false);
        saveButton.setText("Fetching macros...");

        UsdaRepository.getInstance().fetchMacrosForIngredients(
                ingredients,
                new UsdaRepository.MacroMultiCallback() {
                    @Override
                    public void onSuccess(
                            String apiCalories,
                            String apiProtein,
                            String apiCarbs,
                            String apiFat,
                            String usedIngredients
                    ) {
                        /*
                         * USDA successfully returned macro estimates.
                         * Save the recipe using the API-generated macro values.
                         */
                        saveRecipeToDatabase(
                                title,
                                tags,
                                ingredients,
                                instructions,
                                imageUri,
                                apiCalories,
                                apiProtein,
                                apiCarbs,
                                apiFat,
                                usedIngredients
                        );
                    }

                    @Override
                    public void onError(String message) {
                        /*
                         * Re-enable the save button so the user can try again or switch to manual
                         * macro entry.
                         */
                        saveButton.setEnabled(true);
                        saveButton.setText(isEditMode ? "Update Recipe" : "Save Recipe");
                        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    /**
     * Inserts or updates the recipe in SQLite.
     *
     * macroEstimateSource is only filled when the USDA API was used. It is not stored in the
     * database; it is only used for feedback in the Toast message.
     */
    private void saveRecipeToDatabase(
            String title,
            String tags,
            String ingredients,
            String instructions,
            String imageUri,
            String calories,
            String protein,
            String carbs,
            String fat,
            String macroEstimateSource
    ) {
        /*
         * Create a Recipe object that matches the database model.
         *
         * New recipes use ID 0 because SQLite will assign the real autoincrement ID.
         * Edited recipes keep their existing ID so updateRecipe knows which row to update.
         */
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

        /*
         * Choose insert or update depending on how the fragment was opened.
         */
        if (isEditMode) {
            result = dbHelper.updateRecipe(recipe);
        } else {
            result = dbHelper.insertRecipe(recipe);
        }

        /*
         * SQLite insert/update failure
         */
        if (result == -1) {
            Toast.makeText(getContext(), "Error saving recipe", Toast.LENGTH_SHORT).show();
            saveButton.setEnabled(true);
            saveButton.setText(isEditMode ? "Update Recipe" : "Save Recipe");
            return;
        }

        /*
         * Give the user feedback after saving.
         *
         * If USDA was used, also show which ingredients were used in the estimate. This improves
         * transparency because API-generated nutrition values are estimates, not guaranteed facts.
         */
        String successMessage = isEditMode ? "Recipe updated" : "Recipe saved";

        if (macroEstimateSource != null && !macroEstimateSource.trim().isEmpty()) {
            successMessage += ". Macros estimated using: " + macroEstimateSource;
        }

        Toast.makeText(
                getContext(),
                successMessage,
                macroEstimateSource != null && !macroEstimateSource.trim().isEmpty()
                        ? Toast.LENGTH_LONG
                        : Toast.LENGTH_SHORT
        ).show();

        /*
         * Return to Recipe Manager after saving.
         */
        requireActivity()
                .getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, new RecipeFragment())
                .commit();

        ((MainActivity) requireActivity()).setToolbarTitle("Recipe Manager");
    }

    /**
     * Normalises the tags typed by the user.
     *
     * Example:
     * " Vegan,  Vegetarian,High Protein "
     * becomes:
     * "Vegan, Vegetarian, High Protein"
     *
     * Tags are stored as comma-separated text for simplicity.
     */
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

    /**
     * Shows a multi-select dialog containing tags already used in previous recipes.
     *
     * This improves usability because users do not need to repeatedly type common tags like
     * "Vegan", "Vegetarian", or "High Protein".
     */
    private void showExistingTagsDialog() {
        ArrayList<String> existingTags = dbHelper.getAllTags();

        if (existingTags.isEmpty()) {
            Toast.makeText(getContext(), "No existing tags found", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] tagArray = existingTags.toArray(new String[0]);
        boolean[] checkedItems = new boolean[tagArray.length];

        String currentTags = tagsInput.getText().toString();

        /*
         * Pre-check tags that are already typed into the input field.
         */
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

                    /*
                     * Add all checked tags that are not already present.
                     */
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

    /**
     * Checks whether a comma-separated tag string already contains a specific tag.
     *
     * This uses exact tag matching instead of simple String.contains().
     * That avoids mistakes such as treating "Vegetarian" as already containing "Vegan".
     */
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

    /**
     * Factory method used by RecipeDetailFragment when the user taps Edit Recipe.
     *
     * It creates an AddRecipeFragment and attaches the existing recipe data as arguments.
     */
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