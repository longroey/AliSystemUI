package com.android.systemui.statusbar.auto.compositecard;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.aliyun.ams.tyid.TYIDConstants;
import com.aliyun.ams.tyid.TYIDManager;
import com.aliyun.ams.tyid.TYIDException;
import com.android.systemui.R;
import com.android.systemui.statusbar.auto.FontHelper;
import com.android.systemui.statusbar.auto.IconHelper;
import com.android.systemui.statusbar.auto.NotificationConstant;
import com.android.systemui.utils.AliUserTrackUtil;

public class AccountLayout extends ParceableLayout implements View.OnClickListener{

    private static final boolean DEBUG = true;
    private static final String TAG = "AccountLayout";
    private TextView mWelcomeInfo;
    private TextView mToHomeView;
    private TextView mToCompanyView;
    private ImageView mUserIcon;
    private ImageView mSettingUserIcon;
    private TextView mSettingUserName;
    private TextView mSettingTitle;
    private TextView mSettingLogin;
    private TextView mSettingLogout;
    private View mWelcomeLayout;
    private View mSettingLayout;
    private String mDefaultTitleString;
    private Bitmap mAvtarBitmap;
    private static final String TYID_ACTION = "com.aliyun.ams.tyid.service.ITYIDService";
    private static final String TYID_PACKAGE = "com.aliyun.ams.tyid";
    private static final String KEY_ACCOUNT_NAME = "key_account_name";
    private static final String KEY_ACCOUNT_URL = "key_account_url";
    private static final String KEY_ACCOUNT_STATE = "key_account_state";
    private static final String URL_HEAD = "http://wwc.taobaocdn.com/avatar/getAvatar.do?userId=";
    private static final String URL_WIDTH = "&width=";
    private static final String URL_HEIGHT = "&height=";
    private static final String URL_TAIL= "&type=sns";
    private static final int MSG_UPDATE_VIEW = 0;
    private static final int MSG_CHECK_TIME = 1;
    private static final long CHECK_TIME_DELAY = 60 * 1000;//check time per min
    private int mWelcomeIndex = 0;
    private TYIDManager mTYIDManager = null;
    private int mState = TYIDConstants.EYUNOS_INITIAL;
    private String mAvtaUrl = "";
    private static final String AVATAR_SIZE = "100";
    private String mUrl = "";
    private String mName = "";
    private String[] mWelcomeString = null;
    private boolean initialAccount = true;
    boolean finishedInflate = false;
    ConnectivityManager mConnectivityManager;
    Context mContext;
    boolean mInterruptCheckTime;
    Handler myHandler = new Handler(){
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_UPDATE_VIEW:
                updateView4AccountInfoChanged();
                break;
            case MSG_CHECK_TIME:
                handleCheckTime();
                break;
            default:
                break;
            }
        }
    };
    public AccountLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mConnectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        IntentFilter filter = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(mNetWorkReceiver, filter);
        try {
              mTYIDManager = TYIDManager.get(context);
        } catch(TYIDException e) {
            Log.e(TAG, this.toString() + " cannot get TYIDManager instance, start tyid service");
            Intent tyidIntent = new Intent(TYID_ACTION);
            tyidIntent.setPackage(TYID_PACKAGE);
            context.startService(tyidIntent);
        }
    }
    private void sendCheckTimeMessage(){
        if(myHandler.hasMessages(MSG_CHECK_TIME)){
            myHandler.removeMessages(MSG_CHECK_TIME);
        }
        myHandler.sendEmptyMessageDelayed(MSG_CHECK_TIME, CHECK_TIME_DELAY);
    }
    protected void handleCheckTime() {
        updateView4AccountInfoChanged();
        Log.d(TAG, "handleCheckTime mWelcomeIndex = " + mWelcomeIndex + ", info = " + mWelcomeString[mWelcomeIndex]);
        if(!mInterruptCheckTime){
            sendCheckTimeMessage();
        }
    }
    BroadcastReceiver mNetWorkReceiver = new BroadcastReceiver(){
        public void onReceive(Context context, Intent intent) {
            NetworkInfo info = null;
            /*if (intent.getAction().equalsIgnoreCase(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (info.getState().equals(NetworkInfo.State.CONNECTED)) {
                    //UpdateLoginType(true);
                }
                Log.v(TAG, "NetworkInfo state:" + info.getState());
            } else */
            if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                info = mConnectivityManager.getActiveNetworkInfo();
                if (info != null && info.isAvailable()) {
                    String name = info.getTypeName();
                    boolean success = checkBitmapFromUrl();
                    Log.v(TAG, "NetworkInfo getTypeName:" + name + ", checkBitmapFromUrl success = " + success);
                }else{
                    Log.v(TAG, "network disconnected: ------");
                }
            }

        };
    };
    private boolean checkBitmapFromUrl(){
        if(mAvtarBitmap != null || mUrl == null || mUrl.isEmpty()){
            return false;
        }
        getBitmapFromUrl(mUrl);
        return true;
    }
    private void syncAccountInfoFromTYID() {
        if (DEBUG) Log.d(TAG, "updateAccountState mTYIDManager == null" + (mTYIDManager == null));
        if(mTYIDManager == null){
            //request TYID .
            try {
                  mTYIDManager = TYIDManager.get(getContext());
            } catch(TYIDException e) {
                Log.e(TAG, this.toString() + " cannot get TYIDManager instance, get account info from preference");
                getAccountInfoFromPreference();
                if(mUrl != null && !mUrl.isEmpty()){
                    getBitmapFromUrl(mUrl);
                }else{
                    updateView4AccountInfoChanged();
                }
                return;
            }
        }
        mState = mTYIDManager.yunosGetLoginState();
        if (mState != TYIDConstants.EYUNOS_INITIAL) {
            mName = mTYIDManager.yunosGetLoginId();
            String mUserId = mTYIDManager.yunosGetHavanaId();
            mUrl = formatURL(mUserId, AVATAR_SIZE);
            getBitmapFromUrl(mUrl);
        }else{
            //reset name, url
            mName = "";
            mUrl = "";
            mAvtarBitmap = null;
            //update ui
            updateView4AccountInfoChanged();
        }
        updateSharedPreference();
    }

    private void updateSharedPreference() {
        Editor mEditor = mContext.getSharedPreferences(mContext.getPackageName(),
                Activity.MODE_PRIVATE).edit();
        mEditor.putInt(KEY_ACCOUNT_STATE, mState);
        mEditor.putString(KEY_ACCOUNT_NAME, mName);
        mEditor.putString(KEY_ACCOUNT_URL, mUrl);
        mEditor.apply();
        if (DEBUG) Log.d(TAG, "updateSharedPreference mState = " + mState + ", mName = " + mName
                + ", mUrl = " + mUrl);
    }
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mContext.unregisterReceiver(mNetWorkReceiver);
    }
    private void getAccountInfoFromPreference(){
        SharedPreferences mPreference = mContext.getSharedPreferences(mContext.getPackageName(),
                Activity.MODE_PRIVATE);
        mState = mPreference.getInt(KEY_ACCOUNT_STATE, TYIDConstants.EYUNOS_INITIAL);
        mName = mPreference.getString(KEY_ACCOUNT_NAME, "");
        mUrl = mPreference.getString(KEY_ACCOUNT_URL, "");
        if (DEBUG) Log.d(TAG, "getAccountInfoFromPreference mState = " + mState + ", mName = " + mName
                + ", mUrl = " + mUrl);
    }
    public void getBitmapFromUrl(final String url){
        if(url == null || url.isEmpty()){
            Log.d(TAG, "getBitmapFromUrl  url is null or empty! ");
            return;
        }
        if(mAvtaUrl.equals(url)){
            Log.d(TAG, "getBitmapFromUrl duplicate url, not need load. url = " + url);
            return;
        }
        try {
            new AsyncTask<Void, Void, Void>() {
                public Void doInBackground(Void ... args) {
                    try {
                        mAvtaUrl = url;
                        URL uri = new URL(url);
                        try {
                            mAvtarBitmap = BitmapFactory.decodeStream(uri.openStream());
                            if(mAvtarBitmap != null){
                                mAvtarBitmap = IconHelper.getRoundBitmap(mAvtarBitmap);
                            }
                            myHandler.sendEmptyMessage(MSG_UPDATE_VIEW);
                        } catch (IOException e) {
                            // TODO Auto-generated catch block
                            Log.d(TAG, "update other info, user avtar not show ! exception = " + e);
                            mAvtaUrl = "";
                            myHandler.sendEmptyMessage(MSG_UPDATE_VIEW);//update other info, user avtar not show.
                        } /*finally {
                        if(bm != null)
                            bm.recycle();
                    }*/
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void) null);
        } catch (RejectedExecutionException rejectedException) {
            Log.d(TAG, "getBitmapFromUrl thread is over lap, url = " + url, rejectedException);
        }
    }
    public static String formatURL(String userId, String size) {
        try {
            userId = URLEncoder.encode(userId, "GBK") ;
        } catch (Exception e) {
            return null;
        }
        return URL_HEAD + userId + URL_WIDTH + size + URL_HEIGHT + size + URL_TAIL;
    }
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mWelcomeInfo = (TextView) findViewById(R.id.welcome_info);
        FontHelper.applyFont(getContext(), mWelcomeInfo, FontHelper.MYINGHEI_18030_H);
        mToHomeView = (TextView) findViewById(R.id.home_button);
        FontHelper.applyFont(getContext(), mToHomeView, FontHelper.MYINGHEI_18030_M);
//        mHomeUnitView = (TextView) findViewById(R.id.home_unit);
        mToCompanyView = (TextView) findViewById(R.id.company_button);
        FontHelper.applyFont(getContext(), mToCompanyView, FontHelper.MYINGHEI_18030_M);
//        mCompanyUnitView = (TextView) findViewById(R.id.company_unit);
        mUserIcon = (ImageView) findViewById(R.id.account_icon);
        mToHomeView.setOnClickListener(this);
        mToCompanyView.setOnClickListener(this);
        mUserIcon.setOnClickListener(this);
        mWelcomeLayout = findViewById(R.id.account_welcome_layout);
        mSettingLayout = findViewById(R.id.account_setting_layout);
        mSettingUserIcon = (ImageView)findViewById(R.id.setting_login_icon);
        mSettingUserIcon.setOnClickListener(this);
        mSettingUserName = (TextView)findViewById(R.id.setting_account_name);
        FontHelper.applyFont(getContext(), mSettingUserName, FontHelper.MYINGHEI_18030_M);
        mSettingLogin = (TextView)findViewById(R.id.setting_account_login);
        mSettingLogin.setOnClickListener(this);
        FontHelper.applyFont(getContext(), mSettingLogin, FontHelper.MYINGHEI_18030_M);
        mSettingLogout = (TextView)findViewById(R.id.setting_account_logout);
        mSettingLogout.setOnClickListener(this);
        FontHelper.applyFont(getContext(), mSettingLogout, FontHelper.MYINGHEI_18030_M);
        mSettingTitle = (TextView)findViewById(R.id.setting_title_text);
        FontHelper.applyFont(getContext(), mSettingTitle, FontHelper.MYINGHEI_18030_H);
        mDefaultTitleString = getContext().getResources().getString(R.string.setting_default_title);
        finishedInflate = true;
        showWelcomeLayout();
        sendCheckTimeMessage();
    }
    private void updateWelcomeInfo() {
        if(mWelcomeString == null)
            mWelcomeString = mContext.getResources().getStringArray(R.array.account_welcome_info);
        Long time = System.currentTimeMillis();
        Calendar mCalendar = Calendar.getInstance();
        mCalendar.setTimeInMillis(time);
        int hour = mCalendar.get(Calendar.HOUR_OF_DAY);
        if ((4 <= hour) && (hour < 12)) {
            mWelcomeIndex = 1;
        } else if ((12 <= hour) && (hour < 18)) {
            mWelcomeIndex = 2;
        } else {
            mWelcomeIndex = 3;
        }
    }
    private void showWelcomeLayout() {
        mWelcomeLayout.setVisibility(View.VISIBLE);
        mSettingLayout.setVisibility(View.INVISIBLE);//hide setting view.
    }
    private void updateAccountNameView(){
        if(mWelcomeInfo != null){
            if(mName == null || mName.isEmpty()){
                mWelcomeInfo.setText(mWelcomeString[mWelcomeIndex]);
            }else{
                mWelcomeInfo.setText(mName + "," + mWelcomeString[mWelcomeIndex]);
            }
        }
        if(mSettingUserName != null){
            mSettingUserName.setText(mName);
        }
    }
    public boolean isShowingSettingLayout(){
        return mSettingLayout.isShown();
    }
    private void updateAccountAvtarView(){
        if(mAvtarBitmap != null){
            mUserIcon.setImageBitmap(mAvtarBitmap);
            mSettingUserIcon.setImageBitmap(mAvtarBitmap);
        }else{
            mUserIcon.setImageResource(R.drawable.home_accounts_head_default);
            mSettingUserIcon.setImageResource(R.drawable.ic_account_default_face);
        }
    }
    private void updateView4AccountInfoChanged() {
        if(!finishedInflate){
            Log.d(TAG, "updateView4AccountInfoChanged before layout inflate. ignore it, correct it when boot completed");
            return;
        }
        updateWelcomeInfo();
        updateAccountNameView();
        updateAccountAvtarView();
        updateLoginLayoutShow();
        if (DEBUG)
            Log.d(TAG, "updateView4AccountInfoChanged mState=" + mState + " mUrl=" + mUrl + " mName=" + mName
                    + " mWelcome[" + mWelcomeIndex + "]=" + mWelcomeString[mWelcomeIndex]);
    };
    private void updateLoginLayoutShow(){
        if(mState != TYIDConstants.EYUNOS_INITIAL){
            //logout state
            mSettingLogout.setVisibility(View.VISIBLE);
            mSettingLogin.setVisibility(View.GONE);
            mSettingUserName.setVisibility(View.VISIBLE);
        }else{
            //login state
            mSettingLogout.setVisibility(View.GONE);
            mSettingLogin.setVisibility(View.VISIBLE);
            mSettingUserName.setVisibility(View.GONE);
        }
    }
    private void showSettingLayout(){
        mSettingLayout.setVisibility(View.VISIBLE);
        mWelcomeLayout.setVisibility(View.INVISIBLE);//hide welcome view.
        updateLoginLayoutShow();
    }
    /**
     * if account info changed received, update account info.
     */
    public void notifyAccountInfoUpdate(){
        Log.d(TAG, "receive broadcast & notifyAccountInfoUpdate !");
        syncAccountInfoFromTYID();
    }
    @Override
    public void parseParams(String param) {
        try {
            JSONObject o = new JSONObject(param);
            int type = o.optInt(NotificationConstant.ACCOUNT_INFO_TYPE, -1);
            Log.d(TAG, "parseParams param = " + param);
            switch (type) {
            case NotificationConstant.ACCOUNT_INFO_TYPE_INITIAL:
                syncAccountInfoFromTYID();
//                showWelcomeLayout();
                break;
            case NotificationConstant.ACCOUNT_INFO_TYPE_FROM_SETTING:
                String title = o.optString(NotificationConstant.ACCOUNT_INFO_SETTING_TITLE, mDefaultTitleString);
                mSettingTitle.setText(title);
                showSettingLayout();
//                getRoot().showSpeedLimitFloat(false);
                break;
            default:
                break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    public void resetAccountInitial(){
        initialAccount = true;
        if(getVisibility() != View.VISIBLE)//show welcome layout, when setting layout not show.
            showWelcomeLayout();
    }
    public void cancelAccountInitial(){
        initialAccount = false;
        mWelcomeLayout.setVisibility(View.INVISIBLE);
        hideLayout();
    }
    public boolean isInitial(){
        return initialAccount;
    }
    protected void hideLayout(){
        setVisibility(View.INVISIBLE);
        if(initialAccount){
            showWelcomeLayout();
        }
        interruptCheckTime(true);
    }
    protected void showLayout(){
        updateView4AccountInfoChanged();
        setVisibility(View.VISIBLE);
        interruptCheckTime(false);
        sendCheckTimeMessage();
    }
    private void interruptCheckTime(boolean interrupt){
        mInterruptCheckTime = interrupt;
    }
    private void handleLogout() {
        sendBroadCastToAccount(NotificationConstant.ACCOUNT_TYPE_LOGOUT_DELETE);
    }
    private void handleLogin(){
        sendBroadCastToAccount(NotificationConstant.ACCOUNT_TYPE_LOGIN);
    }
    private void handleSignout(){
        sendBroadCastToAccount(NotificationConstant.ACCOUNT_TYPE_LOGOUT_RESERVE);
    }
    private void sendBroadCastToAccount(int accountType){
        Intent intent = new Intent();
        intent.setPackage(NotificationConstant.ACCOUNT_RECEIVE_PACKAGE);
        intent.setAction(NotificationConstant.ACCOUNT_RECEIVE_ACTION);
        intent.putExtra(NotificationConstant.KEY_ACCOUNT_CODE, accountType);
        getContext().sendBroadcast(intent);
    }
    @Override
    public void onClick(View v) {
    	Map<String, String> lMap = new HashMap<String, String>();
        switch (v.getId()) {
        case R.id.home_button:
            goHome();
			lMap.put("click", "home");
            //AliUserTrackUtil.ctrlClicked("Page_Desktop_Hot", "Button-Big_Card_Click","click=home");
            break;
        case R.id.company_button:
            goCompany();
			lMap.put("click", "company");
            //AliUserTrackUtil.ctrlClicked("Page_Desktop_Hot", "Button-Big_Card_Click","click=company");
            break;
        case R.id.setting_account_login:
            handleLogin();
			lMap.put("click", "account");
			lMap.put("state", "login");
			lMap.put("login_state", mState + "");
            //AliUserTrackUtil.ctrlClicked("Page_Desktop_Hot", "Button-Big_Card_Click","click=account");
            break;
        case R.id.setting_account_logout:
            handleLogout();
			lMap.put("click", "account");
			lMap.put("state", "logout");
			lMap.put("login_state", mState + "");
            //AliUserTrackUtil.ctrlClicked("Page_Desktop_Hot", "Button-Big_Card_Click","click=account");
            break;
        case R.id.account_icon:
            if(mState == TYIDConstants.EYUNOS_INITIAL)
                handleLogin();
			lMap.put("click", "account");
			lMap.put("state", "big_login_icon");
			lMap.put("login_state", mState + "");
            //AliUserTrackUtil.ctrlClicked("Page_Desktop_Hot", "Button-Big_Card_Click","click=account");
//            Log.d(TAG, "click big login icon, mState = " + mState);
//            AliUserTrackUtil.ctrlClicked("AccountLayout", "Big_Login_Icon", "login state : " + mState);
            break;
        case R.id.setting_login_icon:
            if(mState == TYIDConstants.EYUNOS_INITIAL)
                handleLogin();
            Log.d(TAG, "click login icon, mState = " + mState);
            lMap.put("click", "account");
			lMap.put("state", "login_icon");
			lMap.put("login_state", mState + "");
            //AliUserTrackUtil.ctrlClicked("AccountLayout", "Login_Icon", "login state : " + mState);
            break;
        default:
            break;
        }
		AliUserTrackUtil.ctrlClicked("Page_Desktop_Hot", "Button-Big_Card_Click",lMap);
    }
    private void goCompany() {
        if(!getRoot().isDeviceProvisioned()){
            Log.d(TAG, "device is not provisioned, ignore go company");
            return;
        }
        callGaoDeAmapApi(1);
    }
    private void goHome() {
        if(!getRoot().isDeviceProvisioned()){
            Log.d(TAG, "device is not provisioned, ignore go home");
            return;
        }
        callGaoDeAmapApi(0);
    }
    //call GAODE API
    // act=android.intent.action.VIEW
    // cat=android.intent.category.DEFAULT
    // dat=androidauto://navi2SpecialDest?sourceApplication=appname&dest=home
    // pkg=com.autonavi.amapauto
    private void callGaoDeAmapApi(int dest){
        /*Intent intent = new Intent(NotificationConstant.GAODE_MAIN_ACTION,
                uri);
        intent.addCategory(NotificationConstant.GAODE_DEFAULT_CATEGORY);
        intent.setPackage(NotificationConstant.GAODE_RECEIVE_PACAKGE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        getContext().startActivity(intent);*/
        Intent intent = new Intent(AutoConstant.AUTONAVI_RECV_ACTION);
        intent.putExtra(AutoConstant.AUTONAVI_KEY_TYPE, 10040);
        intent.putExtra("SOURCE_APP", mContext.getPackageName());
        intent.putExtra("DEST", dest); //0 gohome, 1 go company
        intent.putExtra("IS_START_NAVI", 0); //0 yes, 1 no
        mContext.sendBroadcast(intent);
    }
}
