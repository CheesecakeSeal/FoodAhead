package com.example.mobilecomputingproject.api;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface UsdaApi {

    String BASE_URL = "https://api.nal.usda.gov/fdc/v1/";

    @GET("foods/search")
    Call<UsdaSearchResponse> searchFoods(
            @Query("query") String query,
            @Query("pageSize") int pageSize,
            @Query("dataType") List<String> dataTypes,
            @Query("api_key") String apiKey
    );
}