package com.oozeetech.bizdesk;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.BottomSheetDialog;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ListView;
import android.widget.Toast;

import com.oozeetech.bizdesk.adapter.bottomsheet.Item;
import com.oozeetech.bizdesk.adapter.bottomsheet.ItemAdapter;
import com.oozeetech.bizdesk.models.CommonResponse;
import com.oozeetech.bizdesk.models.LogoutRequest;
import com.oozeetech.bizdesk.retrofit.ApiClient;
import com.oozeetech.bizdesk.retrofit.RequestAPI;
import com.oozeetech.bizdesk.ui.LoginActivity;
import com.oozeetech.bizdesk.utils.AsyncProgressDialog;
import com.oozeetech.bizdesk.utils.Constants;
import com.oozeetech.bizdesk.utils.DialogFactory;
import com.oozeetech.bizdesk.utils.GsonUtils;
import com.oozeetech.bizdesk.utils.InternalStorageContentProvider;
import com.oozeetech.bizdesk.utils.LogUtils;
import com.oozeetech.bizdesk.utils.MarshMallowPermission;
import com.oozeetech.bizdesk.utils.Preferences;
import com.oozeetech.bizdesk.utils.Utils;
import com.oozeetech.bizdesk.widget.DTextView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by divyeshshani on 21/06/16.
 */
public abstract class BaseFragment extends Fragment {

    public static final int REQUEST_CODE_GALLERY = 0x1;
    public static final int REQUEST_CODE_TAKE_PICTURE = 0x2;
    public static final String TEMP_PHOTO_FILE_NAME = "temp_photo.jpg";
    public static final int READ_CALENDAR_CODE = 1;
    public static final int WRITE_CALENDAR_CODE = 2;
    public static final int CAMERA_CODE = 3;
    public static final int READ_CONTACTS_CODE = 4;
    public static final int WRITE_CONTACTS_CODE = 5;
    public static final int GET_ACCOUNT_CODE = 6;
    public static final int ACCESS_FINE_LOCATION_CODE = 7;
    public static final int ACCESS_COARSE_LOCATION_CODE = 8;
    public static final int RECORD_AUDIO_CODE = 9;
    public static final int READ_PHONE_STATE_CODE = 10;
    public static final int CALL_PHONE_CODE = 11;
    public static final int READ_CALL_LOG_CODE = 12;
    public static final int WRITE_CALL_LOG_CODE = 13;
    public static final int ADD_VOICE_MAIL_CODE = 14;
    public static final int USE_SIP_CODE = 15;
    public static final int PROCESS_OUTGOING_CALL_CODE = 16;
    public static final int BODY_SENSOR_CODE = 17;
    public static final int SEND_SMS_CODE = 18;
    public static final int RECEIVE_SMS_CODE = 19;
    public static final int READ_SMS_CODE = 20;
    public static final int RECEIVE_WAP_PUSH_CODE = 21;
    public static final int RECEIVE_MMS_CODE = 22;
    public static final int READ_EXTERNAL_STORAGE_CODE = 23;
    public static final int WRITE_EXTERNAL_STORAGE_CODE = 24;
    public static File mFileTemp;

    static {
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
    }

    public Preferences pref;
    public GsonUtils gsonUtils;
    public MarshMallowPermission marshMallowPermission;
    public RequestAPI requestAPI;
    public LogUtils log;
    public View rootView;
    AsyncProgressDialog ad;
    private Toast toast;
    private BottomSheetBehavior mBehavior;
    private BottomSheetDialog mBottomSheetDialog;
    private Callback<CommonResponse> logoutResponseCallback = new Callback<CommonResponse>() {
        @Override
        public void onResponse(Call<CommonResponse> call, Response<CommonResponse> response) {
            dismissProgress();
            if (response.isSuccessful()) {
                if (response.body().getReturnCode().equals("1")) {
                    pref.clear();
                    Intent intent = new Intent(getActivity(), LoginActivity.class);
                    startActivity(intent);
                    getActivity().finishAffinity();
                } else
                    showDialog("", response.body().getReturnMsg(), response.body().getReturnCode());
            } else {
                log.LOGE("Code: " + response.code() + " Message: " + response.message());
            }
        }

        @Override
        public void onFailure(Call<CommonResponse> call, Throwable t) {
            t.printStackTrace();
            dismissProgress();
        }
    };

    public static void copyStream(InputStream input, OutputStream output) throws IOException {

        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

//    Fragment getCurrentFragment() {
//        Fragment currentFragment = getActivity().getSupportFragmentManager().findFragmentById(R.id.container_body);
//        return currentFragment;
//    }

    public void showDialogDone(String title, String msg) {

        if (title.isEmpty())
            title = getString(R.string.app_name);
        AlertDialog builder = new AlertDialog.Builder(getActivity())
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(R.string.hint_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).create();

        builder.show();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        toast = Toast.makeText(getActivity(), "", Toast.LENGTH_LONG);
        gsonUtils = GsonUtils.getInstance();
        pref = new Preferences(getActivity());
        requestAPI = ApiClient.getClient().create(RequestAPI.class);
        log = new LogUtils(getActivity().getClass());
        marshMallowPermission = new MarshMallowPermission(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        rootView = inflater.inflate(getFragmentLayout(), container, false);
        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }


    protected abstract int getFragmentLayout();

    public void checkResponseCode(String code) {
        if (code.equals("21")) {
            pref.clear();
            Intent intent = new Intent(getActivity(), LoginActivity.class);
            startActivity(intent);
            getActivity().finishAffinity();
        }
    }

    public void initBottomSheet(View mBottomSheet) {
        mBehavior = BottomSheetBehavior.from(mBottomSheet);
    }

    public void initState() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            mFileTemp = new File(Environment.getExternalStorageDirectory(), TEMP_PHOTO_FILE_NAME);
        } else {
            mFileTemp = new File(getActivity().getFilesDir(), TEMP_PHOTO_FILE_NAME);
        }
    }

    @SuppressLint("InflateParams")
    public void showBottomSheetDialog() {
        if (mBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            mBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }

        mBottomSheetDialog = new BottomSheetDialog(getActivity());
        View view = getActivity().getLayoutInflater().inflate(R.layout.sheet, null);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setAdapter(new ItemAdapter(getActivity(), createItems(), new ItemAdapter.ItemListener() {
            @Override
            public void onItemClick(Item item) {

                if (item.getTitle().equals("Camera")) {
                    takePicture();
                } else if (item.getTitle().equals("Gallery")) {
                    openGallery();
                }
                if (mBottomSheetDialog != null) {
                    mBottomSheetDialog.dismiss();
                    mBottomSheetDialog = null;
                }
            }
        }));

        mBottomSheetDialog.setContentView(view);
        mBottomSheetDialog.show();
        mBottomSheetDialog.setCancelable(false);
    }

    public List<Item> createItems() {

        ArrayList<Item> items = new ArrayList<>();
        items.add(new Item(R.drawable.ic_add_a_photo_black_24dp, "Camera"));
        items.add(new Item(R.drawable.ic_photo_black_24dp, "Gallery"));
        items.add(new Item(R.drawable.ic_close_black_24dp, "Cancel"));

        return items;
    }


    public void takePicture() {
        if (!checkPermissionForCamera()) {
            requestPermissionForCamera();
        } else {
            if (!checkPermissionForWriteExternalStorage()) {
                requestPermissionForWriteExternalStorage();
            } else {
                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                try {
                    Uri mImageCaptureUri = null;
                    String state = Environment.getExternalStorageState();
                    if (Environment.MEDIA_MOUNTED.equals(state)) {
                        mImageCaptureUri = Uri.fromFile(mFileTemp);
                    } else {
                /*
                 * The solution is taken from here:
				 * http://stackoverflow.com/questions
				 * /10042695/how-to-getSize-camera-result-as-a-uri-in-data-folder
				 */
                        mImageCaptureUri = InternalStorageContentProvider.CONTENT_URI;
                    }
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, mImageCaptureUri);
                    intent.putExtra("return-data", true);
                    startActivityForResult(intent, REQUEST_CODE_TAKE_PICTURE);
                } catch (ActivityNotFoundException e) {
                    log.LOGE("cannot take picture", e);
                }
            }
        }
    }

    public void openGallery() {
        if (!checkPermissionForReadExternalStorage()) {
            requestPermissionForReadExternalStorage();
        } else {
            Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
            photoPickerIntent.setType("image/*");
            startActivityForResult(photoPickerIntent, REQUEST_CODE_GALLERY);
        }
    }

    public boolean isLoggedIn() {
        return pref.getBoolean(Constants.IS_LOGIN, false);
    }

    public String formatDeciPoint(double value) {
        DecimalFormat formatVal = new DecimalFormat("##.##");
        return formatVal.format(value);
    }

    public void hideKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getActivity().getCurrentFocus().getWindowToken(), 0);
        } catch (Exception e) {
        }
    }

    public void showToast(final int text, final boolean isShort) {
        getActivity().runOnUiThread(new Runnable() {

            @Override
            public void run() {
                toast.setText(getString(text).toString());
                toast.setDuration(isShort ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG);
                toast.show();
            }
        });
    }

    public void showToast(final String text, final boolean isShort) {
        getActivity().runOnUiThread(new Runnable() {

            @Override
            public void run() {
                toast.setText(text);
                toast.setDuration(isShort ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG);
                toast.show();
            }
        });
    }

    public void showProgress() {
        try {
            if (ad != null && ad.isShowing()) {
                return;
            }
            ad = AsyncProgressDialog.getInstant(getActivity());
            ad.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void dismissProgress() {
        try {
            if (ad != null) {
                ad.dismiss();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void confirmLogout() {
        DialogFactory.alertDialog(getActivity(), getString(R.string.logout_title), getString(R.string.logout_msg), getString(R.string.hint_logout), getString(R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                showProgress();
                LogoutRequest request = new LogoutRequest();
                request.setAPIKey(Constants.API_KEY);
                request.setToken(Utils.getLoginDetail(getActivity()).getReturnValue());
                requestAPI.postLogoutRequest(request).enqueue(logoutResponseCallback);

            }
        }, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

    }

    public void showNoInternetDialog() {

        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setMessage(getString(R.string.title_no_internet))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.hint_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        getActivity().finish();
                    }
                }).create();
        dialog.show();
    }


    public void showDialog(String title, String msg, final String returnCode) {
        if (title.isEmpty())
            title = getString(R.string.app_name);
        if (returnCode.equals("21"))
            msg = getString(R.string.session_closed);
        AlertDialog builder = new AlertDialog.Builder(getActivity())
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(R.string.hint_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (returnCode.equals("21"))
                            checkResponseCode(returnCode);
                        dialog.dismiss();
                    }
                }).create();

        builder.show();
    }


    public boolean isCallPhoneAllowed() {
        //Getting the permission status
        int result = ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CALL_PHONE);

        //If permission is granted returning true
        if (result == PackageManager.PERMISSION_GRANTED)
            return true;

        //If permission is not granted returning false
        return false;
    }


    public boolean isReadStorageAllowed() {
        //Getting the permission status
        int result = ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE);

        //If permission is granted returning true
        if (result == PackageManager.PERMISSION_GRANTED)
            return true;

        //If permission is not granted returning false
        return false;
    }


    public boolean isLocationPermissionAllowed() {
        int result = ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION);
        int resultFileLocation = ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION);

        //If permission is granted returning true
        if (result == PackageManager.PERMISSION_GRANTED && resultFileLocation == PackageManager.PERMISSION_GRANTED)
            return true;

        //If permission is not granted returning false
        return false;
    }

    public void noDataFound(DTextView dTextView, ListView view) {
        dTextView.setVisibility(View.VISIBLE);
        view.setVisibility(View.GONE);
    }

    public void showSettingAlert() {
        android.support.v7.app.AlertDialog.Builder alertDialog = new android.support.v7.app.AlertDialog.Builder(getActivity());
        alertDialog.setTitle("GPS");
        alertDialog.setMessage("GPS is not enabled. Do you want to go to settings menu?");
        alertDialog.setPositiveButton("Settings", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        });
        alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                getActivity().finish();
            }
        });
        alertDialog.show();
    }

    public String getDistance(double lat1, double lon1, double lat2, double lon2) {
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1))
                * Math.sin(deg2rad(lat2))
                + Math.cos(deg2rad(lat1))
                * Math.cos(deg2rad(lat2))
                * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;
        return formatDeciPoint(dist);
    }

    private double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }

    private double rad2deg(double rad) {
        return (rad * 180.0 / Math.PI);
    }

    public boolean checkPermissionForReadCalender() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_CALENDAR);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForWriteCalender() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_CALENDAR);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForCamera() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForReadContacts() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_CONTACTS);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForWriteContacts() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_CONTACTS);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForGetAccount() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.GET_ACCOUNTS);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForAccessFineLocation() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForAccessCoarseLocation() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForRecordAudio() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.RECORD_AUDIO);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForReadPhoneState() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_PHONE_STATE);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForCallPhone() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CALL_PHONE);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForReadCallLog() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_CALL_LOG);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForWriteCallLog() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_CALL_LOG);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForAddVoiceMail() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ADD_VOICEMAIL);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForUseSip() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.USE_SIP);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForProcessOutGoingCall() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.PROCESS_OUTGOING_CALLS);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForBodySensor() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.BODY_SENSORS);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForSendSms() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.SEND_SMS);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForReceiveSms() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.RECEIVE_SMS);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForReadSms() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_SMS);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForReceiveWapPush() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.RECEIVE_WAP_PUSH);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForReceiveMms() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.RECEIVE_MMS);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForReadExternalStorage() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public boolean checkPermissionForWriteExternalStorage() {
        int result = ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return result == PackageManager.PERMISSION_GRANTED;
    }


    public void requestPermissionForReadCalender() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)) {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, READ_CALENDAR_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, READ_CALENDAR_CODE);
        }
    }

    public void requestPermissionForWriteCalender() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CALENDAR)) {
            requestPermissions(new String[]{Manifest.permission.WRITE_CALENDAR}, WRITE_CALENDAR_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.WRITE_CALENDAR}, WRITE_CALENDAR_CODE);
        }
    }

    public void requestPermissionForCamera() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_CODE);

        }
    }

    public void requestPermissionForReadContacts() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS)) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, READ_CONTACTS_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, READ_CONTACTS_CODE);
        }
    }

    public void requestPermissionForWriteContacts() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CONTACTS)) {
            requestPermissions(new String[]{Manifest.permission.WRITE_CONTACTS}, WRITE_CONTACTS_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.WRITE_CONTACTS}, WRITE_CONTACTS_CODE);
        }
    }

    public void requestPermissionForGetAccount() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.GET_ACCOUNTS)) {
            requestPermissions(new String[]{Manifest.permission.GET_ACCOUNTS}, GET_ACCOUNT_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.GET_ACCOUNTS}, GET_ACCOUNT_CODE);
        }
    }

    public void requestPermissionForAccessFineLocation() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, ACCESS_FINE_LOCATION_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, ACCESS_FINE_LOCATION_CODE);
        }
    }

    public void requestPermissionForAccessCoarseLocatio() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, ACCESS_COARSE_LOCATION_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, ACCESS_COARSE_LOCATION_CODE);
        }
    }

    public void requestPermissionForRecordAudio() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_CODE);
        }
    }

    public void requestPermissionForReadPhoneState() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)) {
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, READ_PHONE_STATE_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, READ_PHONE_STATE_CODE);
        }
    }

    public void requestPermissionForCallPhone() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE)) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, CALL_PHONE_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, CALL_PHONE_CODE);
        }
    }

    public void requestPermissionForReadCallLog() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.READ_CALL_LOG)) {
            requestPermissions(new String[]{Manifest.permission.READ_CALL_LOG}, READ_CALL_LOG_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_CALL_LOG}, READ_CALL_LOG_CODE);
        }
    }

    public void requestPermissionForWriteCallLog() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_CALL_LOG)) {
            requestPermissions(new String[]{Manifest.permission.WRITE_CALL_LOG}, WRITE_CALL_LOG_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.WRITE_CALL_LOG}, WRITE_CALL_LOG_CODE);
        }
    }

    public void requestPermissionForAddVoiceMail() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.ADD_VOICEMAIL)) {
            requestPermissions(new String[]{Manifest.permission.ADD_VOICEMAIL}, ADD_VOICE_MAIL_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.ADD_VOICEMAIL}, ADD_VOICE_MAIL_CODE);
        }
    }

    public void requestPermissionForUseSip() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.USE_SIP)) {
            requestPermissions(new String[]{Manifest.permission.USE_SIP}, USE_SIP_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.USE_SIP}, USE_SIP_CODE);
        }
    }

    public void requestPermissionForProcessOutGoingCall() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.PROCESS_OUTGOING_CALLS)) {
            requestPermissions(new String[]{Manifest.permission.PROCESS_OUTGOING_CALLS}, PROCESS_OUTGOING_CALL_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.PROCESS_OUTGOING_CALLS}, PROCESS_OUTGOING_CALL_CODE);
        }
    }

    public void requestPermissionForBodySensor() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.BODY_SENSORS)) {
            requestPermissions(new String[]{Manifest.permission.BODY_SENSORS}, BODY_SENSOR_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.BODY_SENSORS}, BODY_SENSOR_CODE);
        }
    }

    public void requestPermissionForSendSms() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.SEND_SMS)) {
            requestPermissions(new String[]{Manifest.permission.SEND_SMS}, SEND_SMS_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.SEND_SMS}, SEND_SMS_CODE);
        }
    }

    public void requestPermissionForReceiveSms() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_SMS)) {
            requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, RECEIVE_SMS_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS}, RECEIVE_SMS_CODE);
        }
    }

    public void requestPermissionForReadSms() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS)) {
            requestPermissions(new String[]{Manifest.permission.READ_SMS}, READ_SMS_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_SMS}, READ_SMS_CODE);
        }
    }

    public void requestPermissionForReceiveWapPush() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_WAP_PUSH)) {
            requestPermissions(new String[]{Manifest.permission.RECEIVE_WAP_PUSH}, RECEIVE_WAP_PUSH_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.RECEIVE_WAP_PUSH}, RECEIVE_WAP_PUSH_CODE);
        }
    }

    public void requestPermissionForReceiveMms() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECEIVE_MMS)) {
            requestPermissions(new String[]{Manifest.permission.RECEIVE_MMS}, RECEIVE_MMS_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.RECEIVE_MMS}, RECEIVE_MMS_CODE);
        }
    }

    public void requestPermissionForWriteExternalStorage() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_CODE);
        }
    }

    public void requestPermissionForReadExternalStorage() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_EXTERNAL_STORAGE_CODE);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, READ_EXTERNAL_STORAGE_CODE);
        }
    }
}
