package com.example.bullsseeclient.services;

import android.content.Context;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.POST;

public class DataCollectionService extends Worker {
    public DataCollectionService(Context context, WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        try {
            // Example: Get last known location
            FusedLocationProviderClient locationClient = LocationServices.getFusedLocationProviderClient(getApplicationContext());
            locationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    // Prepare JSON data
                    LocationData data = new LocationData(location.getLatitude(), location.getLongitude(), 1);
                    sendDataToApi(data);
                }
            });
            return Result.success();
        } catch (Exception e) {
            return Result.failure();
        }
    }

    private void sendDataToApi(LocationData data) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://bullsseeapi.onrender.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ApiService apiService = retrofit.create(ApiService.class);
        RequestBody body = RequestBody.create(new Gson().toJson(data), okhttp3.MediaType.parse("application/json"));
        Call<Void> call = apiService.uploadData(body);
        call.enqueue(new retrofit2.Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, retrofit2.Response<Void> response) {
                // Handle success
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                // Handle failure
            }
        });
    }

    // Define LocationData class
    public static class LocationData {
        private double latitude;
        private double longitude;
        private int deviceId;

        public LocationData(double latitude, double longitude, int deviceId) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.deviceId = deviceId;
        }
    }

    // Define API interface
    public interface ApiService {
        @POST("api/upload")
        Call<Void> uploadData(@Body RequestBody data);
    }
}