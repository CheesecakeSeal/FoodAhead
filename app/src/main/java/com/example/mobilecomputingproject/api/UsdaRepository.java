package com.example.mobilecomputingproject.api;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.mobilecomputingproject.BuildConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class UsdaRepository {

    public interface MacroCallback {
        void onSuccess(String calories, String protein, String carbs, String fat);
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

    public static synchronized UsdaRepository getInstance() {
        if (instance == null) {
            instance = new UsdaRepository();
        }
        return instance;
    }

    public void fetchMacrosForIngredient(String ingredient, MacroCallback callback) {
        ParsedIngredient parsed = parseIngredient(ingredient);

        List<String> dataTypes = Arrays.asList(
                "Foundation",
                "SR Legacy",
                "Survey (FNDDS)"
        );

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

                        if (bestFood.getFoodNutrients() != null) {
                            for (UsdaNutrient nutrient : bestFood.getFoodNutrients()) {
                                String name = nutrient.getNutrientName();
                                String unit = nutrient.getUnitName();

                                if (name == null) continue;

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

    private UsdaFood chooseBestFood(List<UsdaFood> foods, String searchTerm) {
        UsdaFood bestFood = foods.get(0);
        int bestScore = -999;

        for (UsdaFood food : foods) {
            int score = 0;

            String description = food.getDescription() == null
                    ? ""
                    : food.getDescription().toLowerCase(Locale.ROOT);

            String dataType = food.getDataType() == null
                    ? ""
                    : food.getDataType();

            if (description.contains(searchTerm.toLowerCase(Locale.ROOT))) {
                score += 10;
            }

            if (dataType.equalsIgnoreCase("Foundation")) {
                score += 8;
            } else if (dataType.equalsIgnoreCase("SR Legacy")) {
                score += 6;
            } else if (dataType.equalsIgnoreCase("Survey (FNDDS)")) {
                score += 3;
            }

            if (description.contains("raw")) {
                score += 2;
            }

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

    private ParsedIngredient parseIngredient(String ingredient) {
        String cleaned = ingredient.toLowerCase(Locale.ROOT).trim();

        cleaned = cleaned.replaceAll("(\\d+(\\.\\d+)?)\\s*x\\s*", "$1 ");

        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        double multiplier = 1.0;
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

        Pattern countPattern = Pattern.compile("^(\\d+(\\.\\d+)?)\\s+(.+)$");
        Matcher countMatcher = countPattern.matcher(cleaned);

        if (countMatcher.matches()) {
            double count = Double.parseDouble(countMatcher.group(1));
            cleaned = countMatcher.group(3).trim();

            multiplier = count;
        }

        return new ParsedIngredient(cleaned, multiplier);
    }

    private String format(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static class ParsedIngredient {
        String searchTerm;
        double multiplier;

        ParsedIngredient(String searchTerm, double multiplier) {
            this.searchTerm = searchTerm;
            this.multiplier = multiplier;
        }
    }
}