package com.luce.healthmanager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.luce.healthmanager.data.api.ApiService;
import com.luce.healthmanager.data.network.ApiClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText usernameEditText;
    private EditText passwordEditText;
    private Button loginButton;
    // 定義 LinearLayout 變量來表示自定義的按鈕
    LinearLayout googleLoginButton, facebookLoginButton, lineLoginButton;
    private GoogleSignInClient mGoogleSignInClient;
    private static final int RC_SIGN_IN = 9001;
    private CallbackManager callbackManager;
    private FirebaseAuth mAuth;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login); // 登入界面

        Button registerButton = findViewById(R.id.register_button);
        // Google
        googleLoginButton = findViewById(R.id.google_login_button);
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(getString(R.string.server_client_id)) // 使用您在 Google Cloud Console 中的客戶端 ID
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        // FB
        facebookLoginButton = findViewById(R.id.facebook_login_button);
        FacebookSdk.sdkInitialize(getApplicationContext());
        AppEventsLogger.activateApp(this.getApplication());
        mAuth = FirebaseAuth.getInstance();
        callbackManager = CallbackManager.Factory.create();

        lineLoginButton = findViewById(R.id.line_login_button);

        loginButton = findViewById(R.id.login_button);
        usernameEditText = findViewById(R.id.username_input);
        passwordEditText = findViewById(R.id.password_input);

        //一般用戶註冊
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 跳轉到 RegisterActivity
                Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });

        //一般用戶登入
        loginButton.setOnClickListener(v -> {
            String username = usernameEditText.getText().toString();
            String password = passwordEditText.getText().toString();

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this, "請輸入用戶名和密碼", Toast.LENGTH_SHORT).show();
            } else {
                new LoginTask().execute(username, password);
            }
        });

        // Google 登入按鈕點擊事件
        googleLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 這裡放置 Google 登入邏輯
                googleSignin();
            }
        });

        // Facebook 登入按鈕點擊事件
        facebookLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LoginManager.getInstance().logInWithReadPermissions(LoginActivity.this, Arrays.asList("email", "public_profile"));
                LoginManager.getInstance().registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult loginResult) {
                        handleFacebookAccessToken(loginResult.getAccessToken());
                    }

                    @Override
                    public void onCancel() {
                        Log.d("LoginActivity", "Login canceled");
                    }

                    @Override
                    public void onError(FacebookException exception) {
                        Log.e("LoginActivity", "Login error: " + exception.getMessage());
                    }
                });
            }
        });

        // Line 登入按鈕點擊事件
        lineLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 這裡放置 Line 登入邏輯
            }
        });
    }

    // 在 Facebook 登入成功後的回調中進行 Firebase 認證
    private void handleFacebookAccessToken(AccessToken token) {
        // 發送訪問令牌到後端進行驗證
        String accessToken = token.getToken();
        Log.d("fb test","fb accessToken is" + accessToken);
        verifyAccessToken(accessToken);
    }

    private void googleSignin() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d("LoginActivity", "onActivityResult called with requestCode: " + requestCode + ", resultCode: " + resultCode);

        // 處理 Google 登入
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleGoogleSignInResult(task); // 處理 Google 登入結果
        }

        // 處理 Facebook 登入
        callbackManager.onActivityResult(requestCode, resultCode, data); // 將結果傳遞給 Facebook 的 CallbackManager
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class); // 获取登录账户
            // 登入成功
            String idToken = account.getIdToken();
            // 將 ID Token 發送到後端
            sendIdTokenToServer(idToken);

        } catch (ApiException e) {
            int statusCode = e.getStatusCode();
            String errorMessage = "登入失敗: " + statusCode;
            // 登入失敗
            Toast.makeText(this, "登入失敗: " + e.getStatusCode(), Toast.LENGTH_SHORT).show();
            Log.w("GoogleSignIn", "登入失敗: " + e.getStatusCode() + " - " + e.getMessage());
            Log.e("GoogleSignInError", "Sign-in failed: " + e.getStatusCode(), e);

        }
    }

    // Google的
    private void sendIdTokenToServer(String idToken) {
        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        // 创建包含 idToken 的请求体
        Map<String, String> idTokenMap = new HashMap<>();
        idTokenMap.put("idToken", idToken);

        Call<UserResponse> call = apiService.googleLogin(idTokenMap);

        call.enqueue(new Callback<UserResponse>() {
            @Override
            public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                if (response.isSuccessful()) {
                    UserResponse user = response.body();
                    if (user != null) {
                        // Handle successful login
                        Log.d("test" ,"User ID: " + user.getId());
                        Log.d("test" ,"Username: " + user.getUsername());
                        Log.d("test" ,"Email: " + user.getEmail());
                        // Proceed with app logic (e.g., navigate to the main screen)
                        SharedPreferences sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("userId", user.getId());
                        editor.putString("username", user.getUsername());
                        editor.putString("email", user.getEmail());
                        editor.apply(); // 提交更改

                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        intent.putExtra("showHealthFragment", true);
                        startActivity(intent);
                        finish();
                    }
                } else {
                    // Handle error response
                    System.out.println("Login failed: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<UserResponse> call, Throwable t) {
                // Handle network error or failure
                System.out.println("Network error: " + t.getMessage());
            }
        });
    }

    // FB的
    private void verifyAccessToken(String accessToken) {
        // 使用 Retrofit 來呼叫後端 API
        Log.d("test"," verifyAccessToken in ");
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        Call<UserResponse> call = apiService.loginWithFacebook(accessToken);

        call.enqueue(new Callback<UserResponse>() {
            @Override
            public void onResponse(Call<UserResponse> call, Response<UserResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UserResponse user = response.body();
                    if (user != null) {
                        // Handle successful login
                        Log.d("test" ,"User ID: " + user.getId());
                        Log.d("test" ,"Username: " + user.getUsername());
                        Log.d("test" ,"Email: " + user.getEmail());
                        // Proceed with app logic (e.g., navigate to the main screen)
                        SharedPreferences sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("userId", user.getId());
                        editor.putString("username", user.getUsername());
                        editor.putString("email", user.getEmail());
                        editor.apply(); // 提交更改

                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        intent.putExtra("showHealthFragment", true);
                        startActivity(intent);
                        finish();
                    }
                } else {
                    Log.e("LoginActivity", "Login failed: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<UserResponse> call, Throwable t) {
                Log.e("LoginActivity", "Error: " + t.getMessage());
            }
        });
    }

    private class LoginTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
            String username = params[0];
            String password = params[1];
            String result = null;

            try {
                URL url = new URL("http://192.168.50.38:8080/HealthcareManager/api/auth/login");
                Log.d("test","123");
                //URL url = new URL("http://10.0.2.2:8080/api/auth/login");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                String jsonInputString = "{\"username\":\"" + username + "\", \"password\":\"" + password + "\"}";

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int statusCode = connection.getResponseCode(); // 獲取狀態碼
                BufferedReader br;
                if (statusCode == HttpURLConnection.HTTP_OK) { // 200
                    br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
                } else {
                    br = new BufferedReader(new InputStreamReader(connection.getErrorStream(), "utf-8"));
                }

                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                result = response.toString();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            if (result != null) {
                try {
                    JSONObject jsonResponse = new JSONObject(result);

                    // 檢查響應中是否包含token
                    if (jsonResponse.has("token")) {
                        String token = jsonResponse.getString("token");
                        Log.d("test","token at login is " + token);
                        Toast.makeText(LoginActivity.this, "登入成功", Toast.LENGTH_SHORT).show();
                        // 保存 token 到 SharedPreferences
                        SharedPreferences sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putString("jwt_token", token);
                        editor.apply();

                        // 使用公共的 ParseTokenTask
                        new ParseTokenTask(LoginActivity.this, new ParseTokenTask.ParseTokenCallback() {
                            @Override
                            public void onParseTokenCompleted(JSONObject userData) {
                                if (userData != null) {
                                    Log.d("test","userData is " + userData);
                                    try {
                                        String username = userData.getString("username");
                                        String userId = userData.getString("id");
                                        String email = userData.getString("email");
                                        String gender = userData.getString("gender");
                                        String height = userData.getString("height");
                                        String weight = userData.getString("weight");
                                        String dateOfBirth = userData.getString("dateOfBirth");
                                        String userImage = userData.getString("userImage");

                                        // 保存用户数据到 SharedPreferences
                                        SharedPreferences.Editor editor = sharedPreferences.edit();
                                        editor.putString("username", username);
                                        editor.putString("userId", userId);
                                        editor.putString("email", email);
                                        editor.putString("gender", gender);
                                        editor.putString("height", height);
                                        editor.putString("weight", weight);
                                        editor.putString("dateOfBirth", dateOfBirth);
                                        editor.putString("userImage", userImage);
                                        editor.apply();

                                        // 跳转到 MainActivity
                                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                                        intent.putExtra("showHealthFragment", true);
                                        startActivity(intent);
                                        finish();
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                        Toast.makeText(LoginActivity.this, "解析用戶數據出錯", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    Toast.makeText(LoginActivity.this, "解析 token 失敗", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }).execute(token);

                    } else {
                        // 如果返回中不包含token，顯示錯誤訊息
                        String message = jsonResponse.getString("message");
                        Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                    Toast.makeText(LoginActivity.this, "解析返回訊息時出錯", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

}
