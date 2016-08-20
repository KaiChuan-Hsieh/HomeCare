package hackntu.homecare;

import android.app.Dialog;
import android.app.AlertDialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Created by kai-chuan on 8/20/16.
 */
public class MemberInfoDialogFragment extends DialogFragment {
    private InfoManager mInfoManager;
    private MemberInfo mMemberInfo;
    private TextView mDevice;
    private TextView mTime;
    private TextView mHumidity;
    private TextView mTemperature;

    public void setData(InfoManager infoManager, MemberInfo memberInfo) {
        mInfoManager = infoManager;
        mMemberInfo = memberInfo;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.info_dialog, null);
        builder.setView(dialogView);
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
            mHumidity.setText("70");
            mTemperature.setText("50");
        } else {
            // Do nothing
        }
        return builder.create();
    }
}
