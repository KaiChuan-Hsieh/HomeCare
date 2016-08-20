package hackntu.homecare;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity
        implements EasyPermissions.PermissionCallbacks {

    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    private static final String TAG = "HomeCare";
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String PREF_LAST_TIME = "lastTime";
    private static final String[] SCOPES = {
            GmailScopes.MAIL_GOOGLE_COM,
            GmailScopes.GMAIL_MODIFY,
            GmailScopes.GMAIL_READONLY};

    GoogleAccountCredential mCredential;
    ProgressDialog mProgress;
    private InfoManager mInfoManager;
    private ListView mMemberList;
    private TextView mOutputText;
    private Long lastHandled = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        verifyStoragePermissions(this);

        mMemberList = (ListView) findViewById(R.id.memberlist);
        mOutputText = (TextView) findViewById(R.id.status);
        mInfoManager = new InfoManager(getApplicationContext());

        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Calling Gmail API ...");

        Long lastTime = getPreferences(Context.MODE_PRIVATE)
                .getLong(PREF_LAST_TIME, 0L);

        if (lastTime!=0L) {
            lastHandled = lastTime;
        }

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());
    }

    private void verifyStoragePermissions(Activity activity) {
        final int REQUEST_EXTERNAL_STORAGE = 1;
        final String[] PERMISSIONS_STORAGE = {
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        };
        int permission = ActivityCompat.checkSelfPermission(activity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.action_add:
                syncMemberInfo();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void syncMemberInfo() {
        getResultsFromApi();
    }

    private void getResultsFromApi() {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (!isDeviceOnline()) {
            mOutputText.setText("No network connection available.");
        } else {
            new MakeRequestTask(mCredential).execute();
        }
    }

    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }

    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     *
     * @param requestCode  The request code passed in
     *                     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }
    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     *
     * @param requestCode The request code associated with the requested
     *                    permission
     * @param list        The requested permission list. Never null.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    mOutputText.setText(
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.");
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }
    }
    /**
     * An asynchronous task that handles the Gmail API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class MakeRequestTask extends AsyncTask<Void, Void, List<MemberInfo>> {
        private com.google.api.services.gmail.Gmail mService = null;
        private Exception mLastError = null;

        public MakeRequestTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.gmail.Gmail.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("Gmail API Android Quickstart")
                    .build();
        }

        /**
         * Background task to call Gmail API.
         *
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<MemberInfo> doInBackground(Void... params) {
            try {
                return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * Fetch a list of Gmail labels attached to the specified account.
         *
         * @return List of Strings labels.
         * @throws IOException
         */
        private List<MemberInfo> getDataFromApi() throws IOException {
            // Get the labels in the user's account.
            String user = "me";
            String query = "in:inbox from:mailrelay@exosite.com after:2016/08/20";
            List<Message> messages = listMessagesMatchingQuery(mService, user, query);

            List<String> labels = new ArrayList<String>();
            /*
            ListLabelsResponse listResponse =
                    mService.users().labels().list(user).execute();
            for (Label label : listResponse.getLabels()) {
                labels.add(label.getName());
            }
            */

            for (Message message : messages) {
                Message msg = getMessage(mService, "me", message.getId());
                String snippet = msg.getSnippet();
                Long recvTime = msg.getInternalDate();

                if (snippet.contains("Device") && mInfoManager.getMaxRecvTime()<recvTime && lastHandled<recvTime) {
                    String name = snippet.substring(snippet.indexOf("Device") + 8);
                    MemberInfo info = mInfoManager.getMemberInfo(name, recvTime, "Safe");
                    mInfoManager.addMemberInfo(info);
                    mInfoManager.saveMemberInfos();
                }
            }

            return mInfoManager.getMemberInfos();
        }

        /**
         * Get Message with given ID.
         *
         * @param service   Authorized Gmail API instance.
         * @param userId    User's email address. The special value "me"
         *                  can be used to indicate the authenticated user.
         * @param messageId ID of Message to retrieve.
         * @return Message Retrieved Message.
         * @throws IOException
         */
        public Message getMessage(Gmail service, String userId, String messageId)
                throws IOException {
            Message message = service.users().messages().get(userId, messageId).execute();

            Log.d(TAG, "Message internal date: " + message.getInternalDate());

            return message;
        }

        /**
         * List all Messages of the user's mailbox matching the query.
         *
         * @param service Authorized Gmail API instance.
         * @param userId  User's email address. The special value "me"
         *                can be used to indicate the authenticated user.
         * @param query   String used to filter the Messages listed.
         * @throws IOException
         */
        public List<Message> listMessagesMatchingQuery(Gmail service, String userId,
                                                       String query) throws IOException {
            ListMessagesResponse response = service.users().messages().list(userId).setQ(query).execute();

            List<Message> messages = new ArrayList<Message>();
            while (response.getMessages() != null) {
                messages.addAll(response.getMessages());
                if (response.getNextPageToken() != null) {
                    String pageToken = response.getNextPageToken();
                    response = service.users().messages().list(userId).setQ(query)
                            .setPageToken(pageToken).execute();
                } else {
                    break;
                }
            }

            for (Message message : messages) {
                Log.d(TAG, "Message Matching");
                Log.d(TAG, message.toString());
            }

            return messages;
        }

        @Override
        protected void onPreExecute() {
            //mOutputText.setText("");
            mProgress.show();
        }

        @Override
        protected void onPostExecute(List<MemberInfo> output) {
            mProgress.hide();
            if (output == null || output.size() == 0) {
                //mOutputText.setText("No results returned.");
                Toast.makeText(getApplicationContext(), "No member info returned", Toast.LENGTH_SHORT);
            } else {
                ArrayAdapter<MemberInfo> arrayAdapter = new ArrayAdapter<MemberInfo>(MainActivity.this,
                        R.layout.list_item, output);
                mMemberList.setAdapter(arrayAdapter);
                mMemberList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                        MemberInfo info = mInfoManager.getMemberInfos().get(i);
                        showDialog(info);
                    }
                });
                //output.add(0, "Data retrieved using the Gmail API:");
                //mOutputText.setText(TextUtils.join("\n", output));
            }
        }

        @Override
        protected void onCancelled() {
            mProgress.hide();
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    mOutputText.setText("The following error occurred:\n"
                            + mLastError.getMessage());
                }
            } else {
                mOutputText.setText("Request cancelled.");
            }
        }
    }

    private void showDialog(MemberInfo memberInfo) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        MemberInfoDialogFragment memberInfoDialog = new MemberInfoDialogFragment();
        memberInfoDialog.setData(mInfoManager, memberInfo);
        MemberInfoDialogFragment.setDismissListener(dialogDismissedListener);
        memberInfoDialog.show(fragmentManager, "MemberInfoDialogFragment");
    }

    public interface DialogDismissedListener {
        public void handleDialogDismissed(Long time);
    }

    DialogDismissedListener dialogDismissedListener = new DialogDismissedListener() {
        @Override
        public void handleDialogDismissed(Long time) {
            Log.i(TAG, "dialog dismissed");
            if (time>lastHandled) {
                lastHandled = time;
                SharedPreferences settings =
                        getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = settings.edit();
                editor.putLong(PREF_LAST_TIME, lastHandled);
                editor.apply();
            }
            updateList();
        }
    };

    private void updateList() {
        ArrayAdapter<MemberInfo> arrayAdapter = new ArrayAdapter<MemberInfo>(MainActivity.this,
                R.layout.list_item, mInfoManager.getMemberInfos());
        mMemberList.setAdapter(arrayAdapter);
        mMemberList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                MemberInfo info = mInfoManager.getMemberInfos().get(i);
                showDialog(info);
            }
        });
    }
}
