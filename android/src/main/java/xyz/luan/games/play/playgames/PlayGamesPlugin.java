package xyz.luan.games.play.playgames;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import android.util.Log;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.Player;
import com.google.android.gms.games.PlayersClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.games.snapshot.Snapshot;

import java.util.HashMap;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener;
import io.flutter.plugin.common.PluginRegistry.Registrar;

public class PlayGamesPlugin implements FlutterPlugin, ActivityAware, MethodCallHandler, ActivityResultListener {

    private static final String TAG = PlayGamesPlugin.class.getCanonicalName();
    private static final int RC_SIGN_IN = 9001;
    private static final int RC_ACHIEVEMENT_UI = 9002;
    private static final int RC_LEADERBOARD_UI = 9004;
    private static final int RC_ALL_LEADERBOARD_UI = 9005;

    private Context context;
    private Activity activity;
    private PendingOperation pendingOperation;
    private GoogleSignInAccount currentAccount;
    private Map<String, Snapshot> loadedSnapshots = new HashMap<>();

    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "play_games");
        PlayGamesPlugin playGamesPlugin = new PlayGamesPlugin(registrar);
        registrar.addActivityResultListener(playGamesPlugin);
        channel.setMethodCallHandler(playGamesPlugin);
    }

    public PlayGamesPlugin() {
    }

    public PlayGamesPlugin(Registrar registrar) {
        this.context = registrar.activity();
        this.activity = registrar.activity();
    }

    @Override
    public void onAttachedToEngine(FlutterPluginBinding binding) {
        final MethodChannel channel = new MethodChannel(binding.getBinaryMessenger(), "play_games");
        context = binding.getApplicationContext();
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onDetachedFromEngine(FlutterPluginBinding binding) {
    }

    @Override
    public void onAttachedToActivity(ActivityPluginBinding binding) {
        activity = binding.getActivity();
        binding.addActivityResultListener(this);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        activity = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
        activity = binding.getActivity();
    }

    @Override
    public void onDetachedFromActivity() {
        activity = null;
    }

    private static class PendingOperation {
        MethodCall call;
        Result result;

        PendingOperation(MethodCall call, Result result) {
            this.call = call;
            this.result = result;
        }
    }

    private boolean getPropOrDefault(MethodCall call, String prop, boolean defaultValue) {
        Object value = call.argument(prop);
        if (value == null || value.toString().isEmpty()) {
            return defaultValue;
        }
        return value.toString().equalsIgnoreCase("true");
    }

    private void startTransaction(MethodCall call, Result result) {
        if (pendingOperation != null) {
            throw new IllegalStateException(
                    "signIn/showAchievements/showLeaderboard/saved games/snapshots cannot be used concurrently!");
        }
        pendingOperation = new PendingOperation(call, result);
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        if (call.method.equals("signIn")) {
            startTransaction(call, result);
            boolean scopeSnapshot = getPropOrDefault(call, "scopeSnapshot", false);
            boolean silentSignInOnly = getPropOrDefault(call, "silentSignInOnly", false);
            try {
                signIn(scopeSnapshot, silentSignInOnly);
            } catch (Exception ex) {
                pendingOperation = null;
                throw ex;
            }
        } else if (call.method.equals("signOut")) {
            startTransaction(call, result);
            try {
                signOut();
            } catch (Exception ex) {
                pendingOperation = null;
                throw ex;
            }
        } else if (call.method.equals("getLastSignedInAccount")) {
            startTransaction(call, result);
            try {
                GoogleSignInAccount account = getLastSignedInAccount();
                if (account != null) {
                    handleSuccess(account);
                } else {
                    Map<String, Object> successMap = new HashMap<>();
                    successMap.put("type", "NOT_SIGNED_IN");
                    result(successMap);
                }
            } catch (Exception ex) {
                pendingOperation = null;
                throw ex;
            }
        } else if (call.method.equals("showAchievements")) {
            startTransaction(call, result);
            try {
                showAchievements();
            } catch (Exception ex) {
                pendingOperation = null;
                throw ex;
            }
        } else if (call.method.equals("showLeaderboard")) {
            startTransaction(call, result);
            String leaderboardId = call.argument("leaderboardId");
            try {
                showLeaderboard(leaderboardId);
            } catch (Exception ex) {
                pendingOperation = null;
                throw ex;
            }
        } else if (call.method.equals("showAllLeaderboards")) {
            startTransaction(call, result);
            try {
                showAllLeaderboards();
            } catch (Exception ex) {
                pendingOperation = null;
                throw ex;
            }
        } else {
            new Request(this, currentAccount, activity, call, result).handle();
        }
    }

    private void signIn(boolean scopeSnapshot, boolean silentSignInOnly) {
        GoogleSignInOptions.Builder builder = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN);
        GoogleSignInOptions opts = builder.build();
        GoogleSignInClient signInClient = GoogleSignIn.getClient(context, opts);
        silentSignIn(signInClient, silentSignInOnly);
    }

    private void signOut() {
        GoogleSignInOptions.Builder builder = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN);
        GoogleSignInOptions opts = builder.build();
        GoogleSignInClient signInClient = GoogleSignIn.getClient(context, opts);
        signInClient.signOut().addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                currentAccount = null;
                Map<String, Object> successMap = new HashMap<>();
                successMap.put("type", "SUCCESS");
                result(successMap);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                error("ERROR_SIGN_OUT", e);
                Log.i(TAG, "Failed to signout", e);
            }
        });

    }

    private GoogleSignInAccount getLastSignedInAccount() {
        return GoogleSignIn.getLastSignedInAccount(context);
    }

    private void explicitSignIn(GoogleSignInClient signInClient) {
        Intent intent = signInClient.getSignInIntent();
        activity.startActivityForResult(intent, RC_SIGN_IN);
    }

    private void silentSignIn(final GoogleSignInClient signInClient, final boolean silentSignInOnly) {
        signInClient.silentSignIn().addOnSuccessListener(new OnSuccessListener<GoogleSignInAccount>() {
            @Override
            public void onSuccess(GoogleSignInAccount googleSignInAccount) {
                handleSuccess(googleSignInAccount);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                if (silentSignInOnly) {
                    Log.i(TAG, "Failed to silent signin", e);
                    error("ERROR_SIGNIN", e);
                } else {
                    Log.i(TAG, "Failed to silent signin, trying explicit signin", e);
                    explicitSignIn(signInClient);
                }
            }
        });
    }

    private void handleSuccess(GoogleSignInAccount acc) {
        currentAccount = acc;
        PlayersClient playersClient = Games.getPlayersClient(context, currentAccount);
        playersClient.getCurrentPlayer().addOnSuccessListener(new OnSuccessListener<Player>() {
            @Override
            public void onSuccess(Player player) {
                Map<String, Object> successMap = new HashMap<>();
                successMap.put("type", "SUCCESS");
                successMap.put("id", player.getPlayerId());
                successMap.put("email", currentAccount.getEmail());
                successMap.put("displayName", player.getDisplayName());
                successMap.put("hiResImageUri", player.getHiResImageUri().toString());
                successMap.put("iconImageUri", player.getIconImageUri().toString());
                result(successMap);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
                error("ERROR_FETCH_PLAYER_PROFILE", e);
            }
        });
    }

    private void result(Object response) {
        pendingOperation.result.success(response);
        pendingOperation = null;
    }

    private void error(String type, Throwable e) {
        Log.e(TAG, "Unexpected error on " + type, e);
        error(type, e.getMessage());
    }

    private void error(String type, String message) {
        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("type", type);
        errorMap.put("message", message);
        result(errorMap);
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (pendingOperation == null) {
            return false;
        }
        if (requestCode == RC_ACHIEVEMENT_UI || requestCode == RC_LEADERBOARD_UI
                || requestCode == RC_ALL_LEADERBOARD_UI) {
            Map<String, Object> result = new HashMap<>();
            result.put("closed", true);
            result(result);
            return true;
        } else if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess()) {
                handleSuccess(result.getSignInAccount());
            } else {
                String message = result.getStatus().getStatusMessage();
                if (message == null || message.isEmpty()) {
                    message = "Unexpected error " + result.getStatus();
                }
                error("ERROR_SIGNIN", message);
            }
            return true;
        }
        return false;
    }

    public void showAchievements() {
        Games.getAchievementsClient(context, currentAccount).getAchievementsIntent()
                .addOnSuccessListener(new OnSuccessListener<Intent>() {
                    @Override
                    public void onSuccess(Intent intent) {
                        activity.startActivityForResult(intent, RC_ACHIEVEMENT_UI);
                        result(new HashMap<>());
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        error("ERROR_SHOW_ACHIEVEMENTS", e);
                    }
                });
    }

    public void showLeaderboard(String leaderboardId) {
        Games.getLeaderboardsClient(context, currentAccount).getLeaderboardIntent(leaderboardId)
                .addOnSuccessListener(new OnSuccessListener<Intent>() {
                    @Override
                    public void onSuccess(Intent intent) {
                        activity.startActivityForResult(intent, RC_LEADERBOARD_UI);
                        result(new HashMap<>());
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        error("ERROR_SHOW_LEADERBOARD", e);
                    }
                });
    }

    public void showAllLeaderboards() {
        Games.getLeaderboardsClient(context, currentAccount).getAllLeaderboardsIntent()
                .addOnSuccessListener(new OnSuccessListener<Intent>() {
                    @Override
                    public void onSuccess(Intent intent) {
                        activity.startActivityForResult(intent, RC_ALL_LEADERBOARD_UI);
                        result(new HashMap<>());
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        error("ERROR_SHOW_LEADERBOARD", e);
                    }
                });
    }

    public Map<String, Snapshot> getLoadedSnapshot() {
        return this.loadedSnapshots;
    }
}