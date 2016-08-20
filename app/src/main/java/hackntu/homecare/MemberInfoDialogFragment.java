package hackntu.homecare;

import android.app.Dialog;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.SystemClock;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.json.simple.JSONArray;
import org.json.simple.JSONValue;

import java.text.SimpleDateFormat;
import java.util.Calendar;

import onepv1.ClientOnep;
import onepv1.OneException;
import onepv1.Result;

/**
 * Created by kai-chuan on 8/20/16.
 */
public class MemberInfoDialogFragment extends DialogFragment {
    private static final String TAG = "MemberInfoDialog";
    private InfoManager mInfoManager;
    private MemberInfo mMemberInfo;
    private TextView mDevice;
    private TextView mTime;
    private TextView mHumidity;
    private TextView mTemperature;
    private boolean dialogCreated = false;
    private String humidity = "0";
    private String temperature = "0";
    private CountDownTimer mTimer;
    static MainActivity.DialogDismissedListener mDismissListener;
    private Long lastHandled = 0L;

    public void setData(InfoManager infoManager, MemberInfo memberInfo) {
        mInfoManager = infoManager;
        mMemberInfo = memberInfo;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        dialogCreated = true;
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.info_dialog, null);
        builder.setView(dialogView);
        builder.setTitle("Current Status");
        mDevice = (TextView) dialogView.findViewById(R.id.device);
        mTime = (TextView) dialogView.findViewById(R.id.time);
        mHumidity = (TextView) dialogView.findViewById(R.id.humidity);
        mTemperature = (TextView) dialogView.findViewById(R.id.temperature);

        if (mMemberInfo!=null) {
            mDevice.setText(mMemberInfo.getMemberName());
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd hh:mm:ss");
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(mMemberInfo.getStartTime());
            String startTime = formatter.format(calendar.getTime());
            mTime.setText(startTime);
            builder.setNeutralButton(R.string.delete, deleteClickListener);
            builder.setNegativeButton(R.string.cancel, cancelClickListener);
            Thread thread = new Thread(retrieveThread);
            thread.start();
            mTimer = new CountDownTimer(10000, 1000) {
                @Override
                public void onTick(long l) {
                    Log.d(TAG, "count: " + l);
                }

                @Override
                public void onFinish() {
                    if (dialogCreated) {
                        mHumidity.setText(humidity);
                        mTemperature.setText(temperature);
                        mTimer.start();
                    }
                }
            }.start();
        } else {
            // Do nothing
        }
        return builder.create();
    }

    DialogInterface.OnClickListener deleteClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            Log.i(TAG, "delete button selected");
            lastHandled = mMemberInfo.getStartTime();
            mInfoManager.removeMemberInfo(mMemberInfo);
        }
    };

    DialogInterface.OnClickListener cancelClickListener = new DialogInterface.OnClickListener() {
        @Override
        public void onClick(DialogInterface dialogInterface, int i) {
            Log.i(TAG, "cancel button selected");
            dialogInterface.cancel();
        }
    };

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        Log.d(TAG, "MemberInfoDialog Dismissed");
        dialogCreated = false;
        if (mDismissListener!=null) {
            Log.d(TAG, "Last handled = " + lastHandled);
            mDismissListener.handleDialogDismissed(lastHandled);
        }
    }

    static public void setDismissListener(MainActivity.DialogDismissedListener dismissListener) {
        mDismissListener = dismissListener;
    }

    private Runnable retrieveThread = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Thread start");
            String cik = "5ae5d50aba78473a1df7e6a682c4acd9321899c7";
            ClientOnep conn = new ClientOnep("http://m2.exosite.com/api:v1/rpc/process", 3, cik);

            try {
                while (dialogCreated) {
                    Result res = conn.read("Humidity_SHT30");
                    if (res.getStatus() == Result.OK){
                        String read = res.getMessage();
                        JSONArray dataarr = (JSONArray) JSONValue.parse(read);
                        JSONArray data1 = (JSONArray)dataarr.get(0);
                        Log.d(TAG, "Humidity SHT30= " + data1.get(1) + " is read.");
                        humidity = data1.get(1).toString();
                    }

                    res = conn.read("Temperature_MCP9800");
                    if (res.getStatus() == Result.OK){
                        String read = res.getMessage();
                        JSONArray dataarr = (JSONArray) JSONValue.parse(read);
                        JSONArray data1 = (JSONArray)dataarr.get(0);
                        Log.d(TAG, "Temperature MCP9800= " + data1.get(1) + " is read.");
                        temperature = data1.get(1).toString();
                    }
                    Log.d(TAG, "Sleep 10 seconds");
                    SystemClock.sleep(10000);
                }
            } catch (OneException e) {
                e.printStackTrace();
            }
        }
    };
}
