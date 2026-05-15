package com.example.mobilecomputingproject.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.mobilecomputingproject.BuildConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Repository class responsible for communicating with the USDA FoodData Central API.
 *
 * This class sits between the UI layer and the Retrofit API interface. The fragments should not
 * need to know how the USDA request is built, how ingredients are parsed, how retries work, or how
 * the best food result is selected. They only ask this repository for macro estimates.
 *
 * Main responsibilities:
 * - Split a recipe's ingredient list into individual searchable ingredients.
 * - Parse simple quantities such as "200g chicken", "1kg rice", "2 eggs", and "2x eggs".
 * - Search USDA FoodData Central through Retrofit.
 * - Prefer generic USDA entries over less useful branded results.
 * - Retry failed ingredient lookups before giving up.
 * - Sum all ingredient macro estimates into one recipe-level estimate.
 */
public class UsdaRepository {

    /*
     * Each ingredient is retried because a request can fail temporarily due to network delay,
     * API rate limits, or an intermittent server issue.
     *
     * The app should not skip an ingredient after one failed attempt, because that would create
     * incomplete macro totals without the user realising.
     */
    private static final int MAX_RETRIES_PER_INGREDIENT = 3;

    /*
     * A short delay between requests/retries makes the process less aggressive and gives temporary
     * network/API issues a chance to recover.
     */
    private static final int RETRY_DELAY_MS = 600;

    /**
     * Callback used when fetching macros for one ingredient.
     *
     * Values are returned as strings because the rest of the app stores macros as text in SQLite.
     */
    public interface MacroCallback {
        void onSuccess(String calories, String protein, String carbs, String fat);
        void onError(String message);
    }

    /**
     * Callback used when fetching macros for a full recipe ingredient list.
     *
     * usedIngredients is a short summary of the ingredients that were successfully used for the
     * estimate. It is shown to the user so the estimate is transparent.
     */
    public interface MacroMultiCallback {
        void onSuccess(String calories, String protein, String carbs, String fat, String usedIngredients);
        void onError(String message);
    }

    private static UsdaRepository instance;

    private final UsdaApi api;

    private UsdaRepository() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(UsdaApi.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        api = retrofit.create(UsdaApi.class);
    }

    /**
     * Returns the shared repository instance.
     *
     * synchronized prevents two instances being created at the same time if this is called quickly
     * from different places.
     */
    public static synchronized UsdaRepository getInstance() {
        if (instance == null) {
            instance = new UsdaRepository();
        }
        return instance;
    }

    /**
     * Fetch macro estimates for a whole recipe.
     *
     * The raw ingredient text may contain multiple ingredients separated by new lines or commas:
     *
     * 200g chicken
     * 100g rice
     * 2 eggs
     *
     * This method splits those into separate ingredients, fetches each one in sequence, and adds
     * their estimated calories/protein/carbs/fat together.
     */
    public void fetchMacrosForIngredients(String rawIngredients, MacroMultiCallback callback) {
        ArrayList<String> ingredients = splitIngredients(rawIngredients);

        if (ingredients.isEmpty()) {
            callback.onError("No ingredients found for macro lookup");
            return;
        }

        /*
         * totals[0] = calories
         * totals[1] = protein
         * totals[2] = carbs
         * totals[3] = fat
         *
         * A double array is used because the asynchronous callbacks need to keep updating the same
         * running totals while moving through the ingredient list.
         */
        double[] totals = new double[]{0, 0, 0, 0};

        /*
         * Stores the ingredients that successfully contributed to the estimate. This is later used
         * to show feedback such as:
         *
         * "Macros estimated using: 200g chicken, 100g rice, 2 eggs"
         */
        ArrayList<String> usedIngredients = new ArrayList<>();

        fetchIngredientAtIndex(
                ingredients,
                0,
                totals,
                usedIngredients,
                callback
        );
    }

    /**
     * Starts the fetch process for a specific ingredient index.
     *
     * This wrapper keeps the main sequence readable and always starts retries at attempt 1.
     */
    private void fetchIngredientAtIndex(
            ArrayList<String> ingredients,
            int index,
            double[] totals,
            ArrayList<String> usedIngredients,
            MacroMultiCallback callback
    ) {
        fetchIngredientAtIndexWithRetry(
                ingredients,
                index,
                totals,
                usedIngredients,
                callback,
                1
        );
    }

    /**
     * Fetches one ingredient, retrying if the request fails.
     *
     * The requests are intentionally processed sequentially rather than all at once. This makes the
     * total calculation simpler and avoids sending many API requests at exactly the same time.
     *
     * Flow:
     * - If all ingredients are processed, return the final totals.
     * - Otherwise, fetch the current ingredient.
     * - If successful, add its macros to the running totals and move to the next ingredient.
     * - If failed, retry up to MAX_RETRIES_PER_INGREDIENT.
     * - If it still fails, stop and tell the user to try again or enter macros manually.
     */
    private void fetchIngredientAtIndexWithRetry(
            ArrayList<String> ingredients,
            int index,
            double[] totals,
            ArrayList<String> usedIngredients,
            MacroMultiCallback callback,
            int attempt
    ) {
        /*
         * Base case: every ingredient has been processed.
         */
        if (index >= ingredients.size()) {
            if (usedIngredients.isEmpty()) {
                callback.onError("Could not estimate macros from the listed ingredients");
                return;
            }

            callback.onSuccess(
                    format(totals[0]),
                    format(totals[1]),
                    format(totals[2]),
                    format(totals[3]),
                    buildIngredientSummary(usedIngredients)
            );

            return;
        }

        String currentIngredient = ingredients.get(index);

        fetchMacrosForIngredient(currentIngredient, new MacroCallback() {
            @Override
            public void onSuccess(String calories, String protein, String carbs, String fat) {
                /*
                 * Convert returned strings back to numbers so they can be added to the recipe total.
                 * parseDoubleSafely avoids crashes if the API response is missing or malformed.
                 */
                totals[0] += parseDoubleSafely(calories);
                totals[1] += parseDoubleSafely(protein);
                totals[2] += parseDoubleSafely(carbs);
                totals[3] += parseDoubleSafely(fat);

                usedIngredients.add(currentIngredient);

                /*
                 * Continue with the next ingredient after a short delay.
                 * Handler + main looper is used because Retrofit callbacks return asynchronously,
                 * and this keeps the next step safely scheduled on the main thread.
                 */
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    fetchIngredientAtIndex(
                            ingredients,
                            index + 1,
                            totals,
                            usedIngredients,
                            callback
                    );
                }, RETRY_DELAY_MS);
            }

            @Override
            public void onError(String message) {
                /*
                 * Do not immediately skip failed ingredients. Temporary request failures are common
                 * enough that retrying gives a much more reliable result. During testing I had to press
                 * "search" several times on occasion prior to adding this.
                 */
                if (attempt < MAX_RETRIES_PER_INGREDIENT) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        fetchIngredientAtIndexWithRetry(
                                ingredients,
                                index,
                                totals,
                                usedIngredients,
                                callback,
                                attempt + 1
                        );
                    }, RETRY_DELAY_MS);
                } else {
                    /*
                     * After repeated failure, stop the whole auto-fill process rather than saving
                     * partial nutrition data. This is safer and more honest for the user.
                     */
                    callback.onError(
                            "Could not estimate macros for: " + currentIngredient +
                                    ". Please try again or enter macros manually."
                    );
                }
            }
        });
    }

    /**
     * Fetch macro estimates for one ingredient.
     *
     * This method:
     * - Parses the ingredient into a USDA search term and multiplier.
     * - Searches USDA FoodData Central.
     * - Chooses the best matching food result from the returned list.
     * - Extracts calories, protein, carbs, and fat.
     * - Applies the multiplier based on the user's entered amount.
     */
    public void fetchMacrosForIngredient(String ingredient, MacroCallback callback) {
        ParsedIngredient parsed = parseIngredient(ingredient);

        /*
         * These data types are preferred because they are generally more useful for generic recipe
         * ingredients than branded supermarket products. See comment further down
         */
        List<String> dataTypes = Arrays.asList(
                "Foundation",
                "SR Legacy",
                "Survey (FNDDS)"
        );

        /*
         * pageSize = 10 gives several candidates. Searching only one result was unreliable because
         * a broad term like "egg" could return an unsuitable first match.
         */
        api.searchFoods(parsed.searchTerm, 10, dataTypes, BuildConfig.USDA_API_KEY)
                .enqueue(new Callback<UsdaSearchResponse>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<UsdaSearchResponse> call,
                            @NonNull retrofit2.Response<UsdaSearchResponse> response
                    ) {
                        if (!response.isSuccessful() || response.body() == null) {
                            callback.onError("Could not fetch nutrition data");
                            return;
                        }

                        List<UsdaFood> foods = response.body().getFoods();

                        if (foods == null || foods.isEmpty()) {
                            callback.onError("No USDA match found for: " + parsed.searchTerm);
                            return;
                        }

                        UsdaFood bestFood = chooseBestFood(foods, parsed.searchTerm);

                        double calories = 0;
                        double protein = 0;
                        double carbs = 0;
                        double fat = 0;

                        /*
                         * USDA returns a list of nutrient records. We only care about:
                         * - Energy in KCAL
                         * - Protein
                         * - Carbohydrate by difference
                         * - Total lipid/fat
                         */
                        if (bestFood.getFoodNutrients() != null) {
                            for (UsdaNutrient nutrient : bestFood.getFoodNutrients()) {
                                String name = nutrient.getNutrientName();
                                String unit = nutrient.getUnitName();

                                if (name == null) {
                                    continue;
                                }

                                if (name.equalsIgnoreCase("Energy")
                                        && unit != null
                                        && unit.equalsIgnoreCase("KCAL")) {
                                    calories = nutrient.getValue();
                                } else if (name.equalsIgnoreCase("Protein")) {
                                    protein = nutrient.getValue();
                                } else if (name.equalsIgnoreCase("Carbohydrate, by difference")) {
                                    carbs = nutrient.getValue();
                                } else if (name.equalsIgnoreCase("Total lipid (fat)")) {
                                    fat = nutrient.getValue();
                                }
                            }
                        }

                        /*
                         * Apply the parsed multiplier. For example:
                         * - 200g chicken -> multiplier 2.0, because USDA values are treated as per 100g.
                         * - 1kg rice -> multiplier 10.0.
                         * - 2 eggs -> multiplier 2.0.
                         */
                        calories *= parsed.multiplier;
                        protein *= parsed.multiplier;
                        carbs *= parsed.multiplier;
                        fat *= parsed.multiplier;

                        callback.onSuccess(
                                format(calories),
                                format(protein),
                                format(carbs),
                                format(fat)
                        );
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<UsdaSearchResponse> call,
                            @NonNull Throwable t
                    ) {
                        Log.e("USDA_API", "Error: " + t.getMessage());
                        callback.onError("Network error");
                    }
                });
    }

    /**
     * Chooses the most useful USDA result from the returned search results.
     *
     * USDA search can return many possible matches. For example, "egg" might return branded foods,
     * dried egg products, cooked dishes, or generic raw eggs. This scoring system is not perfect,
     * but it improves consistency by preferring generic USDA data sources.
     */
    private UsdaFood chooseBestFood(List<UsdaFood> foods, String searchTerm) {
        UsdaFood bestFood = foods.get(0);
        int bestScore = -999;

        String cleanedSearchTerm = searchTerm.toLowerCase(Locale.ROOT);

        for (UsdaFood food : foods) {
            int score = 0;

            String description = food.getDescription() == null
                    ? ""
                    : food.getDescription().toLowerCase(Locale.ROOT);

            String dataType = food.getDataType() == null
                    ? ""
                    : food.getDataType();

            /*
             * Prefer descriptions that actually contain the cleaned search term.
             */
            if (description.contains(cleanedSearchTerm)) {
                score += 10;
            }

            /*
             * Prefer more generic USDA datasets.
             *
             * I found that Foundation and SR Legacy are usually better for raw/common ingredients.
             * Survey/FNDDS can still be useful, but may represent prepared foods.
             */
            if (dataType.equalsIgnoreCase("Foundation")) {
                score += 8;
            } else if (dataType.equalsIgnoreCase("SR Legacy")) {
                score += 6;
            } else if (dataType.equalsIgnoreCase("Survey (FNDDS)")) {
                score += 3;
            }

            /*
             * Raw ingredients are usually closer to what users type into a recipe.
             */
            if (description.contains("raw")) {
                score += 2;
            }

            /*
             * Branded items can vary widely and may not represent a generic ingredient well.
             */
            if (description.contains("branded")) {
                score -= 10;
            }

            if (score > bestScore) {
                bestScore = score;
                bestFood = food;
            }
        }

        return bestFood;
    }

    /**
     * Parses a user-entered ingredient into:
     * - a search term for USDA
     * - a multiplier for scaling the returned nutrition data
     *
     * Supported examples:
     * - "200g chicken" -> search "chicken", multiplier 2.0
     * - "1kg rice" -> search "rice", multiplier 10.0
     * - "250ml milk" -> search "milk", multiplier 2.5
     * - "2 eggs" -> search "eggs", multiplier 2.0
     * - "2x eggs" -> search "eggs", multiplier 2.0
     *
     * This parser is simple and not perfect.
     */
    private ParsedIngredient parseIngredient(String ingredient) {
        String cleaned = ingredient.toLowerCase(Locale.ROOT).trim();

        // Convert "2x eggs" or "2 x eggs" to "2 eggs".
        cleaned = cleaned.replaceAll("(\\d+(\\.\\d+)?)\\s*x\\s*", "$1 ");

        // Remove common bullet prefixes and normalise repeated spaces.
        cleaned = cleaned.replaceAll("^[-•*]\\s*", "");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        double multiplier = 1.0;

        /*
         * Weight/volume handling.
         *
         * USDA nutrition values are commonly treated here as per 100g/ml, so the multiplier is:
         * - grams / 100
         * - kilograms converted to grams, then / 100
         * - millilitres / 100
         */
        Pattern weightPattern = Pattern.compile("^(\\d+(\\.\\d+)?)\\s*(kg|g|ml)\\s+(.+)$");
        Matcher weightMatcher = weightPattern.matcher(cleaned);

        if (weightMatcher.matches()) {
            double amount = Double.parseDouble(weightMatcher.group(1));
            String unit = weightMatcher.group(3);
            cleaned = weightMatcher.group(4).trim();

            if (unit.equals("kg")) {
                multiplier = (amount * 1000) / 100.0;
            } else if (unit.equals("g") || unit.equals("ml")) {
                multiplier = amount / 100.0;
            }

            return new ParsedIngredient(cleaned, multiplier);
        }

        /*
         * Count handling.
         *
         * This is a generic approximation:
         * - "2 eggs" multiplies the selected USDA result by 2.
         * - "3 apples" multiplies the selected USDA result by 3.
         *
         * This is not perfect because different foods have different real-world sizes
         */
        Pattern countPattern = Pattern.compile("^(\\d+(\\.\\d+)?)\\s+(.+)$");
        Matcher countMatcher = countPattern.matcher(cleaned);

        if (countMatcher.matches()) {
            double count = Double.parseDouble(countMatcher.group(1));
            cleaned = countMatcher.group(3).trim();

            multiplier = count;
        }

        return new ParsedIngredient(cleaned, multiplier);
    }

    /**
     * Splits the user's ingredient text into individual ingredients.
     *
     * The app accepts both newline-separated and comma-separated ingredients.
     *
     * Example:
     * "200g chicken\n100g rice, 2 eggs"
     * becomes:
     * ["200g chicken", "100g rice", "2 eggs"]
     */
    private ArrayList<String> splitIngredients(String rawIngredients) {
        ArrayList<String> ingredients = new ArrayList<>();

        if (rawIngredients == null || rawIngredients.trim().isEmpty()) {
            return ingredients;
        }

        String[] parts = rawIngredients.split("[,\\n]");

        for (String part : parts) {
            String cleaned = part
                    .replaceAll("^[-•*]\\s*", "")
                    .trim();

            if (!cleaned.isEmpty()) {
                ingredients.add(cleaned);
            }
        }

        return ingredients;
    }

    /**
     * Safely converts a string to a double.
     *
     * This prevents the app from crashing if an API value is missing, empty, or unexpectedly formatted.
     */
    private double parseDoubleSafely(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }

        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Builds a short readable summary of the ingredients used in the estimate.
     */
    private String buildIngredientSummary(ArrayList<String> usedIngredients) {
        return joinIngredientList(usedIngredients);
    }

    /**
     * Joins ingredient names into a short user-friendly list.
     *
     * Only the first three ingredients are shown directly.
     * Extra ingredients are summarised as "+N more".
     */
    private String joinIngredientList(ArrayList<String> ingredients) {
        StringBuilder builder = new StringBuilder();

        int maxShown = 3;
        int count = Math.min(ingredients.size(), maxShown);

        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(", ");
            }

            builder.append(ingredients.get(i));
        }

        if (ingredients.size() > maxShown) {
            builder.append(" +")
                    .append(ingredients.size() - maxShown)
                    .append(" more");
        }

        return builder.toString();
    }

    /**
     * Formats macro values consistently to one decimal place.
     */
    private String format(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    /**
     * Small helper object used internally after parsing an ingredient.
     *
     * searchTerm is what gets sent to the USDA search endpoint.
     * multiplier is applied to the returned macro values.
     */
    private static class ParsedIngredient {
        String searchTerm;
        double multiplier;

        ParsedIngredient(String searchTerm, double multiplier) {
            this.searchTerm = searchTerm;
            this.multiplier = multiplier;
        }
    }
}