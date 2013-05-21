package com.coinbase.android;

import java.util.List;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.Toast;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.coinbase.api.LoginManager;

public class LoginActivity extends CoinbaseActivity {
	private static final String TAG = LoginActivity.class.getName();
  private static final String REDIRECT_URL = "urn:ietf:wg:oauth:2.0:oob";
  public static final String EXTRA_SHOW_INTRO = "show_intro";

  WebView mLoginWebView;
  View mLoginIntro;

  Button mLoginButton;
  MenuItem mRefreshItem;
  boolean mRefreshItemState = false;

  private class OAuthCodeTask extends AsyncTask<String, Void, String> {

    ProgressDialog mDialog;

    @Override
    protected void onPreExecute() {
      super.onPreExecute();
      mDialog = ProgressDialog.show(LoginActivity.this, null, getString(R.string.login_progress));
    }

    @Override
    protected String doInBackground(String... params) {
      return LoginManager.getInstance().addAccountOAuth(LoginActivity.this, params[0], REDIRECT_URL);
    }

    protected void onPostExecute(String result) {

      try {
        mDialog.dismiss();
      } catch (Exception e) {
        // ProgressDialog has been destroyed already
      }

      if(result == null) {
        // Success!
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish();
      } else {
        // Failure.
        Toast.makeText(LoginActivity.this, result, Toast.LENGTH_LONG).show();
        loadLoginUrl();
      }
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);  
    setContentView(R.layout.activity_login);
    setProgressBarIndeterminateVisibility(false); 
    getSupportActionBar().setTitle(R.string.login_title);

    mLoginWebView = (WebView) findViewById(R.id.login_webview);
    mLoginIntro = findViewById(R.id.login_intro);

    mLoginButton = (Button) findViewById(R.id.login_intro_submit);
    mLoginButton.setOnClickListener(new View.OnClickListener() {

      @Override
      public void onClick(View v) {

        changeMode(false);
      }
    });

    // Load authorization URL before user clicks on the sign in button so that it loads quicker
    mLoginWebView.getSettings().setJavaScriptEnabled(true);
    mLoginWebView.getSettings().setSavePassword(false);

    // Clear cookies so that user is not already logged in if they want to add a new account
    CookieSyncManager.createInstance(this); 
    CookieManager cookieManager = CookieManager.getInstance();
    cookieManager.removeAllCookie();

    mLoginWebView.setWebViewClient(new WebViewClient() {

      @Override
      public void onPageStarted(WebView view, String url, Bitmap favicon) {

        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
          // There is a bug where shouldOverrideUrlLoading is not called
          // On versions of Android lower then Honeycomb
          // When the URL change is a result of a redirect
          // Emulate it here
          boolean shouldOverride = _shouldOverrideUrlLoading(view, url);
          if(shouldOverride) {
            view.stopLoading();
          }
        }
      }

      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url) {

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
          return _shouldOverrideUrlLoading(view, url);
        } else {
          return false;
        }
      }

      public boolean _shouldOverrideUrlLoading(WebView view, String url) {

        Uri uri = Uri.parse(url);
        List<String> pathSegments = uri.getPathSegments();
        if(pathSegments.size() == 3 && "oauth".equals(pathSegments.get(0)) && "authorize".equals(pathSegments.get(1))) {
          // OAuth redirect - we will handle this.
          String oauthCode = pathSegments.get(2);
          Log.e(TAG,"oauthCode : " + oauthCode);
          new OAuthCodeTask().execute(oauthCode);
          return true;
        } else if(uri.getPath().startsWith("/transactions") || uri.getPath().isEmpty()) { 
          // The coinbase site is trying to redirect us to the transactions page or the home page
          // Since we are not logged in go to the login page
          loadLoginUrl();
          return true;
        } else if(!url.contains("oauth") && !url.contains("signin") && !url.contains("signup") &&
            !url.contains("users") &&
            !url.contains("sessions")) {

          // Do not allow leaving the login page.
          Intent intent = new Intent(Intent.ACTION_VIEW);
          intent.setData(Uri.parse(url));
          intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          startActivity(intent);
          return true;
        }

        Log.i("Coinbase", "Login activity allowed to browse to " + url);
        return false;
      }
    });
    mLoginWebView.setWebChromeClient(new WebChromeClient() {

      @Override
      public void onProgressChanged(WebView view, int newProgress) {

        setProgressBarVisible(newProgress != 100); 
      }

    });

    onNewIntent(getIntent());
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);

    setIntent(intent);

    if(getIntent().getData() != null) {
      // Load this URL in the web view
      mLoginWebView.loadUrl(getIntent().getDataString());
      changeMode(false);
    } else {
      loadLoginUrl();
      changeMode(getIntent().getBooleanExtra(EXTRA_SHOW_INTRO, true) ? true : false);
    }
  }

  public void setProgressBarVisible(boolean animated) {

    mRefreshItemState = animated;

    if(mRefreshItem == null) {
      return;
    }

    if(animated) {
      mRefreshItem.setVisible(true);
      mRefreshItem.setActionView(R.layout.actionbar_indeterminate_progress);
    } else {
      mRefreshItem.setVisible(false);
      mRefreshItem.setActionView(null);
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getSupportMenuInflater().inflate(R.menu.activity_login, menu);
    mRefreshItem = menu.findItem(R.id.menu_refresh);
    setProgressBarVisible(mRefreshItemState);
    return true;
  }

  private void loadLoginUrl() {
    mLoginWebView.loadUrl(LoginManager.getInstance().generateOAuthUrl(REDIRECT_URL));
  }

  private void changeMode(boolean intro) {

    if(intro) {
      getSupportActionBar().hide();
    } else {
      getSupportActionBar().show();
    }

    mLoginIntro.setVisibility(intro ? View.VISIBLE : View.GONE);
    mLoginWebView.setVisibility(intro ? View.GONE : View.VISIBLE);
    
    if(!intro) {
      // Fixes focus bug on Android 2.3
      mLoginWebView.requestFocus();
    }
  }
}
