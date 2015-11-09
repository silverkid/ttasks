package com.teo.ttasks.activities;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.plus.Plus;
import com.google.android.gms.plus.model.people.Person;
import com.google.api.services.tasks.TasksScopes;
import com.google.api.services.tasks.model.TaskList;
import com.koushikdutta.ion.Ion;
import com.mikepenz.google_material_typeface_library.GoogleMaterial;
import com.mikepenz.materialdrawer.AccountHeader;
import com.mikepenz.materialdrawer.AccountHeaderBuilder;
import com.mikepenz.materialdrawer.Drawer;
import com.mikepenz.materialdrawer.DrawerBuilder;
import com.mikepenz.materialdrawer.model.DividerDrawerItem;
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileDrawerItem;
import com.mikepenz.materialdrawer.model.ProfileSettingDrawerItem;
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem;
import com.mikepenz.materialdrawer.model.interfaces.IProfile;
import com.teo.ttasks.AboutActivity;
import com.teo.ttasks.R;
import com.teo.ttasks.TaskUtils;
import com.teo.ttasks.TasksFragment;
import com.teo.ttasks.receivers.NetworkInfoReceiver;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import timber.log.Timber;

/**
 * Based on:
 * Sample activity for Google Tasks API v1. It demonstrates how to use authorization to list tasks
 * with the user's permission.
 *
 * @author Yaniv Inbar
 * @author Teo Baranga
 */

// TODO: store task lists offline
// TODO: Authenticate only once with Google
// TODO: fix SwipeRefreshLayout after switching fragments
// TODO: implement multiple accounts

public final class MainActivity extends AppCompatActivity implements OnConnectionFailedListener, ConnectionCallbacks {

    //private static final int RC_ADD = 4;

    private static final int ID_PROFILE_DEFAULT = -1;
    private static final int ID_ADD_ACCOUNT = 1000;
    private static final int ID_MANAGE_ACCOUNT = 1001;
    private static final int ID_ADD_TASKLIST = 2000;
    private static final int ID_ABOUT = 2;

    // Request code to use when launching the resolution activity
    private static final int RC_RESOLVE_ERROR = 1001;

    // Unique tag for the error dialog fragment
    private static final String DIALOG_ERROR = "dialog_error";
    private static final String STATE_RESOLVING_ERROR = "resolving_error";

    public IProfile profile = null;
    /* Client used to interact with Google APIs. */
    private GoogleApiClient mGoogleApiClient;
    // save our header or result
    private AccountHeader accountHeader = null;
    private Drawer drawer = null;
    // Bool to track whether the app is already resolving an error
    private boolean mResolvingError = false;
    private ArrayList<IDrawerItem> taskLists = new ArrayList<>();
    private NetworkInfoReceiver mNetworkInfoReceiver;

    private TasksFragment tasksFragment;
    private Realm realm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Realm
        RealmConfiguration realmConfiguration = new RealmConfiguration.Builder(this).build();
        Realm.deleteRealm(realmConfiguration);
        realm = Realm.getInstance(realmConfiguration);

        // Create the network info receiver
        mNetworkInfoReceiver = new NetworkInfoReceiver(this) {
            @Override
            public void onReceive(@NotNull Context context, @NotNull Intent intent) {
                Toast.makeText(MainActivity.this, (isOnline() ? "Yey" : "No") + "Internet!", Toast.LENGTH_SHORT).show();
            }
        };

        mResolvingError = savedInstanceState != null && savedInstanceState.getBoolean(STATE_RESOLVING_ERROR, false);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Plus.API)
                .addScope(Plus.SCOPE_PLUS_PROFILE)
                .addScope(new Scope(TasksScopes.TASKS))
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        // Handle Toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Initialize the default profile
        profile = new ProfileDrawerItem()
                .withNameShown(true)
                .withName("")
                .withEmail("")
                .withIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_photo, null))
                .withIdentifier(ID_PROFILE_DEFAULT);

        // Create the AccountHeader
        accountHeader = new AccountHeaderBuilder()
                .withActivity(this)
                .withHeaderBackground(R.drawable.ic_cover)
                .addProfiles(
                        profile,
                        new ProfileSettingDrawerItem()
                                .withName(getResources().getString(R.string.addaccount))
                                .withIcon(GoogleMaterial.Icon.gmd_account_add)
                                .withIdentifier(ID_ADD_ACCOUNT),
                        new ProfileSettingDrawerItem()
                                .withName(getResources().getString(R.string.manageaccount))
                                .withIcon(GoogleMaterial.Icon.gmd_settings)
                                .withIdentifier(ID_MANAGE_ACCOUNT)
                )
                .withOnAccountHeaderListener((View view, IProfile profile, boolean current) -> {
                    Timber.d(profile.toString());
                    return true;
                })
                .withSavedInstance(savedInstanceState)
                .build();

        //Create the drawer
        drawer = new DrawerBuilder()
                .withActivity(this)
                .withToolbar(toolbar)
                .withAccountHeader(accountHeader) //set the AccountHeader we created earlier for the header
                .withDrawerItems(taskLists)
                .addDrawerItems(
                        new PrimaryDrawerItem()
                                .withName(R.string.drawer_addtasklist)
                                .withIcon(GoogleMaterial.Icon.gmd_plus)
                                .withIdentifier(ID_ADD_TASKLIST)
                                .withSelectable(false),
                        new DividerDrawerItem(),
                        new SecondaryDrawerItem()
                                .withName(getResources().getString(R.string.settings))
                                .withIcon(GoogleMaterial.Icon.gmd_settings)
                                .withSelectable(false),
                        new SecondaryDrawerItem()
                                .withName(R.string.helpandfeedback)
                                .withIcon(GoogleMaterial.Icon.gmd_help)
                                .withSelectable(false),
                        new SecondaryDrawerItem()
                                .withName(R.string.about)
                                .withIcon(GoogleMaterial.Icon.gmd_info_outline)
                                .withIdentifier(ID_ABOUT)
                                .withSelectable(false)
                ) // add the items we want to use with our Drawer
                .withOnDrawerItemClickListener((View view, int position, IDrawerItem drawerItem) -> {
                    //check if the drawerItem is set.
                    //there are different reasons for the drawerItem to be null
                    //--> click on the header
                    //--> click on the footer
                    //those items don't contain a drawerItem
                    if (drawerItem != null) {
                        switch (drawerItem.getIdentifier()) {
                            case ID_ADD_TASKLIST:
                                break;
                            case ID_ABOUT:
                                startActivity(new Intent(this, AboutActivity.class));
                                break;
                            default:
                                // Check that the activity is using the layout version with
                                // the fragment_container FrameLayout
                                if (findViewById(R.id.fragment_container) != null) {

                                    // However, if we're being restored from a previous state,
                                    // then we don't need to do anything and should return or else
                                    // we could end up with overlapping fragments.
                                    //                                if (savedInstanceState != null) {
                                    //                                    return;
                                    //                                }

                                    // Create a new Fragment to be placed in the activity layout
                                    if (tasksFragment == null)
                                        tasksFragment = new TasksFragment();

                                    // Add the fragment to the 'fragment_container' FrameLayout
                                    getSupportFragmentManager()
                                            .beginTransaction()
                                            .replace(R.id.fragment_container, tasksFragment)
                                            .commit();
                                }
                        }
                    }
                    return false;
                })
                .withSavedInstance(savedInstanceState)
                .build();

        //only set the active selection or active profile if we do not recreate the activity
        if (savedInstanceState == null) {
            // TODO: think about this some more
            // set the selection to the first item
            drawer.setSelectionAtPosition(1);
            //set the active profile
            accountHeader.setActiveProfile(profile);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!mResolvingError)
            mGoogleApiClient.connect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mNetworkInfoReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mNetworkInfoReceiver);
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        realm.close();
    }

    @Override
    public void onConnectionSuspended(int cause) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // This callback is important for handling errors that
        // may occur while attempting to connect with Google.
        Timber.w("Connection failed!");
        if (!mResolvingError) {
            // Not already attempting to resolve an error.
            if (result.hasResolution()) {
                try {
                    mResolvingError = true;
                    result.startResolutionForResult(this, RC_RESOLVE_ERROR);
                } catch (IntentSender.SendIntentException e) {
                    // There was an error with the resolution intent. Try again.
                    mGoogleApiClient.connect();
                }

            } else {
                // Show dialog using GooglePlayServicesUtil.getErrorDialog()
                showErrorDialog(result.getErrorCode());
                mResolvingError = true;
            }
        }
    }

    @Override
    public void onConnected(Bundle connectionHint) {
        // Connected to Google Play services!
        Timber.d("Connection succeeded!");

        // We've resolved any connection errors.  mGoogleApiClient can be used to
        // access Google APIs on behalf of the user.
        Person currentPerson = Plus.PeopleApi.getCurrentPerson(mGoogleApiClient);
        if (currentPerson == null) {
            // This method can return null if the required scopes weren't specified
            // in the GoogleApiClient.Builder, or if there was a network error while connecting.
            // TODO: handle this
            return;
        }
        String email = Plus.AccountApi.getAccountName(mGoogleApiClient);
        String personName = currentPerson.getDisplayName();
        String cover = null;
        if (currentPerson.hasCover())
            cover = currentPerson.getCover().getCoverPhoto().getUrl();
        // by default the profile url gives 50x50 px image only
        // we can replace the value with whatever dimension we want by
        // replacing sz=X
        String pic = currentPerson.getImage().getUrl();
        // Requesting a size of 400x400
        pic = pic.substring(0, pic.length() - 2) + 400;

        profile.withName(personName);
        profile.withEmail(email);
        profile.withIcon(pic);
        accountHeader.updateProfile(profile);

        if (mNetworkInfoReceiver.isOnline()) {
            if (cover != null)
                Ion.with(this).load(cover).asBitmap().setCallback((Exception e, Bitmap result) -> {
                    if (e == null) {
                        BitmapDrawable d = new BitmapDrawable(getResources(), result);
                        accountHeader.setBackground(d);
                    } else Timber.e(e.toString());
                });
        }

        GetTaskLists();

        // TODO: fix this, maybe using an interface?
//        if (tasksFragment != null)
//            tasksFragment.fetchTasks();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
//            case RC_ADD:
//                if(resultCode == RESULT_OK) {
//                    MaterialAccount acc = new MaterialAccount(this.getResources(), data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME),"", R.drawable.ic_photo, R.drawable.ic_cover);
//                    this.addAccount(acc);
//                    Toast.makeText(MainActivity.this, data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME), Toast.LENGTH_SHORT).show();
//                }
//                break;
            case RC_RESOLVE_ERROR:
                mResolvingError = false;
                if (resultCode == RESULT_OK) {
                    if (!mGoogleApiClient.isConnecting() && !mGoogleApiClient.isConnected()) {
                        mGoogleApiClient.connect();
                    }
                }
                break;
        }
    }

    // TODO: implement chooseAccount
//    private void chooseAccount() {
//        // disconnects the account and the account picker
//        // stays there until another account is chosen
//        if (mGoogleApiClient.isOnline()) {
//            // Prior to disconnecting, run clearDefaultAccount().
////            Plus.AccountApi.clearDefaultAccount(mGoogleApiClient);
////            Plus.AccountApi.revokeAccessAndDisconnect(mGoogleApiClient)
////                    .setResultCallback(new ResultCallback<Status>() {
////
////                        public void onResult(Status status) {
////                            // mGoogleApiClient is now disconnected and access has been revoked.
////                            // Trigger app logic to comply with the developer policies
////                        }
////
////                    });
//            Plus.AccountApi.clearDefaultAccount(mGoogleApiClient);
//            mGoogleApiClient.disconnect();
//            mGoogleApiClient.connect();
//        }
//    }

    // TODO: implement OnAccountAddComplete
//    private class OnAccountAddComplete implements AccountManagerCallback<Bundle> {
//        @Override
//        public void run(AccountManagerFuture<Bundle> result) {
//            Bundle bundle;
//            try {
//                bundle = result.getResult();
//            } catch (OperationCanceledException e) {
//                e.printStackTrace();
//                return;
//            } catch (AuthenticatorException e) {
//                e.printStackTrace();
//                return;
//            } catch (IOException e) {
//                e.printStackTrace();
//                return;
//            }
//
////            MaterialAccount acc = new MaterialAccount(MainActivity.this.getResources(), "",
////                    bundle.getString(AccountManager.KEY_ACCOUNT_NAME), R.drawable.ic_photo, R.drawable.ic_cover);
////            MainActivity.this.addAccount(acc);
//
////            mAccount = new Account(
////                    bundle.getString(AccountManager.KEY_ACCOUNT_NAME),
////                    bundle.getString(AccountManager.KEY_ACCOUNT_TYPE)
////            );
//            // do more stuff
//        }
//    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_tasks, menu);
        return true;
    }

    /* Creates a dialog for an error message */
    private void showErrorDialog(int errorCode) {
        // Create a fragment for the error dialog
        ErrorDialogFragment dialogFragment = new ErrorDialogFragment();
        // Pass the error that should be displayed
        Bundle args = new Bundle();
        args.putInt(DIALOG_ERROR, errorCode);
        dialogFragment.setArguments(args);
        dialogFragment.show(getSupportFragmentManager(), "errordialog");
    }

    /* Called from ErrorDialogFragment when the dialog is dismissed. */
    public void onDialogDismissed() {
        mResolvingError = false;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        //add the values which need to be saved from the drawer to the bundle
        outState = drawer.saveInstanceState(outState);
        //add the values which need to be saved from the accountHeader to the bundle
        outState = accountHeader.saveInstanceState(outState);
        // Keep track of the mResolvingError boolean across activity restarts
        outState.putBoolean(STATE_RESOLVING_ERROR, mResolvingError);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        //handle the back press :D close the drawer first and if the drawer is closed close the activity
        if (drawer != null && drawer.isDrawerOpen()) {
            drawer.closeDrawer();
        } else {
            super.onBackPressed();
        }
    }

    // TODO: supposed to be here?
    public void GetTaskLists() {
        // Load locally from Realm
        List<com.teo.ttasks.model.TaskList> taskListList = realm.allObjects(com.teo.ttasks.model.TaskList.class);
        if (taskListList.size() == 0)
            Timber.d("No task lists");
        else for (com.teo.ttasks.model.TaskList taskList : taskListList) {
            addTaskList(taskList.getTitle());
        }
        // Get the token and load the task lists
        new TaskUtils.GetTokenTask(this, profile.getEmail().getText()) {
            @Override
            public void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                new TaskUtils.GetTaskLists() {
                    @Override
                    protected void onPostExecute(List<TaskList> taskLists) {
                        super.onPostExecute(taskLists);
                        if (taskLists != null) {
                            Timber.d("Found %s task lists", taskLists.size());
                            // Insert task lists into Navigation Drawer
                            // TODO: insert into some specific order
                            for (TaskList taskList : taskLists) {
                                realm.beginTransaction();
                                realm.createObjectFromJson(com.teo.ttasks.model.TaskList.class, taskList.toString());
                                realm.commitTransaction();
                            }
                            try {
                                List<com.teo.ttasks.model.TaskList> taskListList = realm.allObjects(com.teo.ttasks.model.TaskList.class);
                                for (com.teo.ttasks.model.TaskList taskList : taskListList) {
                                    addTaskList(taskList.getTitle());
                                }
                            } catch (Exception e) {
                                Timber.e(e.toString());
                            }

                            // Reselect the first item in the drawer
                            drawer.setSelectionAtPosition(0);
                        }
                    }
                }.execute();
            }
        }.execute();
    }

    private void addTaskList(String title) {
        drawer.addItem(new PrimaryDrawerItem()
                .withName(title)
                .withIcon(GoogleMaterial.Icon.gmd_assignment)
                .withSelectable(false));
    }

    /* A fragment to display an error dialog */
    public static class ErrorDialogFragment extends DialogFragment {

        @Override
        @NonNull
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Get the error code and retrieve the appropriate dialog
            int errorCode = getArguments().getInt(DIALOG_ERROR);
            return GooglePlayServicesUtil.getErrorDialog(errorCode, getActivity(), RC_RESOLVE_ERROR);
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            ((MainActivity) getActivity()).onDialogDismissed();
        }
    }
}