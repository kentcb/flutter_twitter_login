package com.roughike.fluttertwitterlogin.fluttertwitterlogin;

import android.content.Intent;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;

import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Twitter;
import com.twitter.sdk.android.core.TwitterAuthConfig;
import com.twitter.sdk.android.core.TwitterConfig;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.TwitterSession;
import com.twitter.sdk.android.core.identity.TwitterAuthClient;

import java.util.HashMap;

import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

public class TwitterLoginPlugin extends Callback<TwitterSession> implements MethodCallHandler, PluginRegistry.ActivityResultListener {
    private static final String CHANNEL_NAME = "com.roughike/flutter_twitter_login";
    private static final String METHOD_GET_CURRENT_SESSION = "getCurrentSession";
    private static final String METHOD_AUTHORIZE = "authorize";
    private static final String METHOD_LOG_OUT = "logOut";

    private final Registrar registrar;

    private Result pendingResult;

    public static void registerWith(Registrar registrar) {
        final TwitterLoginPlugin plugin = new TwitterLoginPlugin(registrar);
        final MethodChannel channel = new MethodChannel(registrar.messenger(), CHANNEL_NAME);
        channel.setMethodCallHandler(plugin);
    }

    private TwitterLoginPlugin(Registrar registrar) {
        this.registrar = registrar;
        registrar.addActivityResultListener(this);

        checkTwitterCoreAndEnable();

        Twitter.getLogger().i(TwitterCore.TAG, "Initialized.");
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        Twitter.getLogger().i(TwitterCore.TAG, "Received method call: " + call.method);

        switch (call.method) {
            case METHOD_GET_CURRENT_SESSION:
                getCurrentSession(result, call);
                break;
            case METHOD_AUTHORIZE:
                authorize(result, call);
                break;
            case METHOD_LOG_OUT:
                logOut(result, call);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private void setPendingResult(String methodName, MethodChannel.Result result) {
        if (pendingResult != null) {
            result.error(
                    "TWITTER_LOGIN_IN_PROGRESS",
                    methodName + " called while another Twitter " +
                            "login operation was in progress.",
                    null
            );
        }

        pendingResult = result;
    }

    private void getCurrentSession(Result result, MethodCall call) {
        TwitterSession session = TwitterCore.getInstance().getSessionManager().getActiveSession();
        HashMap<String, Object> sessionMap = sessionToMap(session);

        result.success(sessionMap);
    }

    private void authorize(Result result, MethodCall call) {
        setPendingResult("authorize", result);
        String consumerKey = call.argument("consumerKey");
        String consumerSecret = call.argument("consumerSecret");
        getTwitterAuthClient().authorize(registrar.activity(), this);
    }

    volatile TwitterAuthClient authClient;
    TwitterAuthClient getTwitterAuthClient() {
        if (authClient == null) {
            synchronized (TwitterLoginPlugin.class) {
                if (authClient == null) {
                    Twitter.getLogger().i(TwitterCore.TAG, "Creating auth client.");
                    authClient = new TwitterAuthClient();
                }
            }
        }
        return authClient;
    }

    private void logOut(Result result, MethodCall call) {
        CookieSyncManager.createInstance(registrar.context());
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.removeSessionCookie();

        TwitterCore.getInstance().getSessionManager().clearActiveSession();
        result.success(null);
    }

    private HashMap<String, Object> sessionToMap(final TwitterSession session) {
        if (session == null) {
            return null;
        }

        return new HashMap<String, Object>() {{
            put("secret", session.getAuthToken().secret);
            put("token", session.getAuthToken().token);
            put("userId", String.valueOf(session.getUserId()));
            put("username", session.getUserName());
        }};
    }

    @Override
    public void success(final com.twitter.sdk.android.core.Result<TwitterSession> result) {
        Twitter.getLogger().i(TwitterCore.TAG, "Success!");

        if (pendingResult != null) {
            final HashMap<String, Object> sessionMap = sessionToMap(result.data);
            final HashMap<String, Object> resultMap = new HashMap<String, Object>() {{
                put("status", "loggedIn");
                put("session", sessionMap);
            }};

            pendingResult.success(resultMap);
            pendingResult = null;
        }
    }

    @Override
    public void failure(final TwitterException exception) {
        Twitter.getLogger().i(TwitterCore.TAG, "Failure!");

        if (pendingResult != null) {
            final HashMap<String, Object> resultMap = new HashMap<String, Object>() {{
                put("status", "error");
                put("errorMessage", exception.getMessage());
            }};

            pendingResult.success(resultMap);
            pendingResult = null;
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        Twitter.getLogger().i(TwitterCore.TAG, "onActivityResult");

        if (requestCode == getTwitterAuthClient().getRequestCode()) {
            Twitter.getLogger().i(TwitterCore.TAG, "Forwarding result.");

            getTwitterAuthClient().onActivityResult(requestCode, resultCode, data);
        }

        return false;
    }

    private void checkTwitterCoreAndEnable() {
        TwitterAuthConfig authConfig = new TwitterAuthConfig("xxx", "xxx");
        TwitterConfig config = new TwitterConfig.Builder(registrar.context())
                .twitterAuthConfig(authConfig)
                .build();
        Twitter.initialize(config);

        try {
            TwitterCore.getInstance();
            Twitter.getLogger().i(TwitterCore.TAG, "Twitter core enabled.");

        } catch (IllegalStateException ex) {
            //Disable if TwitterCore hasn't started
            Twitter.getLogger().e(TwitterCore.TAG, ex.getMessage());
        }
    }
}
